package org.thoughtcrime.securesms.registration;

import android.app.Application;
import android.app.backup.BackupManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.core.app.NotificationManagerCompat;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.KeyHelper;
import org.signal.libsignal.zkgroup.profiles.ProfileKey;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.crypto.ProfileKeyUtil;
import org.thoughtcrime.securesms.crypto.SenderKeyUtil;
import org.thoughtcrime.securesms.crypto.storage.PreKeyMetadataStore;
import org.thoughtcrime.securesms.crypto.storage.SignalServiceAccountDataStoreImpl;
import org.thoughtcrime.securesms.database.IdentityTable;
import org.thoughtcrime.securesms.database.RecipientTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.jobs.AllDataSyncRequestJob;
import org.thoughtcrime.securesms.jobs.DirectoryRefreshJob;
import org.thoughtcrime.securesms.jobs.PreKeysSyncJob;
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob;
import org.thoughtcrime.securesms.jobs.RotateCertificateJob;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.notifications.NotificationIds;
import org.thoughtcrime.securesms.pin.SvrRepository;
import org.thoughtcrime.securesms.push.AccountManagerFactory;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.service.DirectoryRefreshListener;
import org.thoughtcrime.securesms.service.RotateSignedPreKeyListener;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceAccountManager.NewDeviceRegistrationReturn;
import org.whispersystems.signalservice.api.account.PreKeyCollection;
import org.whispersystems.signalservice.api.account.PreKeyUpload;
import org.whispersystems.signalservice.api.kbs.MasterKey;
import org.whispersystems.signalservice.api.push.ServiceId.ACI;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;
import org.whispersystems.signalservice.api.push.ServiceId;
import org.whispersystems.signalservice.api.push.ServiceIdType;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.Preconditions;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.push.BackupAuthCheckProcessor;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Operations required for finalizing the registration of an account. This is
 * to be used after verifying the code and registration lock (if necessary) with
 * the server and being issued a UUID.
 */
public final class RegistrationRepository {

  private static final String TAG = Log.tag(RegistrationRepository.class);

  private final Application context;

  public RegistrationRepository(@NonNull Application context) {
    this.context = context;
  }

  public int getRegistrationId() {
    int registrationId = SignalStore.account().getRegistrationId();
    if (registrationId == 0) {
      registrationId = KeyHelper.generateRegistrationId(false);
      SignalStore.account().setRegistrationId(registrationId);
    }
    return registrationId;
  }

  public int getPniRegistrationId() {
    int pniRegistrationId = SignalStore.account().getPniRegistrationId();
    if (pniRegistrationId == 0) {
      pniRegistrationId = KeyHelper.generateRegistrationId(false);
      SignalStore.account().setPniRegistrationId(pniRegistrationId);
    }
    return pniRegistrationId;
  }

  public @NonNull ProfileKey getProfileKey(@NonNull String e164) {
    ProfileKey profileKey = findExistingProfileKey(e164);

    if (profileKey == null) {
      profileKey = ProfileKeyUtil.createNew();
      Log.i(TAG, "No profile key found, created a new one");
    }

    return profileKey;
  }

  public Single<ServiceResponse<VerifyResponse>> registerAccount(@NonNull RegistrationData registrationData,
                                                                 @NonNull VerifyResponse response,
                                                                 boolean setRegistrationLockEnabled)
  {
    return Single.<ServiceResponse<VerifyResponse>>fromCallable(() -> {
      try {
        registerAccountInternal(registrationData, response, setRegistrationLockEnabled);

        JobManager jobManager = ApplicationDependencies.getJobManager();
        jobManager.add(new DirectoryRefreshJob(false));
        jobManager.add(new RotateCertificateJob());

        DirectoryRefreshListener.schedule(context);
        RotateSignedPreKeyListener.schedule(context);

        return ServiceResponse.forResult(response, 200, null);
      } catch (IOException e) {
        return ServiceResponse.forUnknownError(e);
      }
    }).subscribeOn(Schedulers.io());
  }

  public Single<ServiceResponse<NewDeviceRegistrationReturn>> registerAccountFromPrimaryDevice(@NonNull RegistrationData registrationData,
                                                                                               @NonNull NewDeviceRegistrationReturn response,
                                                                                               int deviceId,
                                                                                               String deviceName)
  {
    return Single.<ServiceResponse<NewDeviceRegistrationReturn>>fromCallable(() -> {
      try {
        // We have to set the deviceId before setting the identity keys, or they will throw for not being a linked device
        // But we also have to set the identity keys before calling registerAccountInternal, otherwise it will generate new ones when creating the prekeys
        SignalStore.account().setDeviceId(deviceId);
        SignalStore.account().setAciIdentityKeysFromPrimaryDevice(response.getAciIdentity());
        SignalStore.account().setPniIdentityKeyAfterChangeNumber(response.getPniIdentity());
        SignalStore.registrationValues().markNeedDownloadProfileAndAvatar();
        registerAccountInternal(new RegistrationData(registrationData.getCode(),
                                                     response.getNumber(),
                                                     registrationData.getPassword(),
                                                     registrationData.getRegistrationId(),
                                                     response.getProfileKey() == null ? ProfileKeyUtil.createNew() : response.getProfileKey(),
                                                     registrationData.getFcmToken(),
                                                     registrationData.getPniRegistrationId(),
                                                     registrationData.getRecoveryPassword()),
                                response.getAci(), response.getPni(),
                                generateSignedAndLastResortPreKeys(response.getAciIdentity(), SignalStore.account().aciPreKeys()),
                                generateSignedAndLastResortPreKeys(response.getPniIdentity(), SignalStore.account().pniPreKeys()),
                                false, deviceId, deviceName, null, null, false);

        JobManager jobManager = ApplicationDependencies.getJobManager();
        jobManager.add(new RotateCertificateJob());
        jobManager.add(new AllDataSyncRequestJob());
        jobManager.add(new RefreshOwnProfileJob());

        RotateSignedPreKeyListener.schedule(context);

        return ServiceResponse.forResult(response, 200, null);
      } catch (IOException e) {
        return ServiceResponse.forUnknownError(e);
      }
    }).subscribeOn(Schedulers.io());
  }

  @WorkerThread
  private void registerAccountInternal(@NonNull RegistrationData registrationData,
                                       @NonNull VerifyResponse response,
                                       boolean setRegistrationLockEnabled)
      throws IOException
  {
    SignalStore.registrationValues().clearNeedDownloadProfile();
    SignalStore.registrationValues().clearNeedDownloadProfileAvatar();
    registerAccountInternal(
        registrationData,
        ACI.parseOrThrow(response.getVerifyAccountResponse().getUuid()),
        PNI.parseOrThrow(response.getVerifyAccountResponse().getPni()),
        response.getAciPreKeyCollection(),
        response.getPniPreKeyCollection(),
        response.getVerifyAccountResponse().isStorageCapable(),
        SignalServiceAddress.DEFAULT_DEVICE_ID,
        null,
        response.getMasterKey(),
        response.getPin(),
        setRegistrationLockEnabled
    );
  }

  @WorkerThread
  private void registerAccountInternal(@NonNull RegistrationData registrationData,
                                       @NonNull ACI aci,
                                       @NonNull PNI pni,
                                       @Nullable PreKeyCollection aciPreKeyCollection,
                                       @Nullable PreKeyCollection pniPreKeyCollection,
                                       boolean hasPin,
                                       int deviceId,
                                       @Nullable String deviceName,
                                       @Nullable MasterKey masterKey,
                                       @Nullable String pin,
                                       boolean setRegistrationLockEnabled)
      throws IOException
  {
    Preconditions.checkNotNull(aciPreKeyCollection, "Missing ACI prekey collection!");
    Preconditions.checkNotNull(pniPreKeyCollection, "Missing PNI prekey collection!");

    SignalStore.account().setAci(aci);
    SignalStore.account().setPni(pni);
    SignalStore.account().setDeviceId(deviceId);
    SignalStore.account().setDeviceName(deviceName);
    if (deviceId != SignalServiceAddress.DEFAULT_DEVICE_ID) {
      TextSecurePreferences.setMultiDevice(context, true);
    }

    ApplicationDependencies.getProtocolStore().aci().sessions().archiveAllSessions();
    ApplicationDependencies.getProtocolStore().pni().sessions().archiveAllSessions();
    SenderKeyUtil.clearAllState();

    SignalServiceAccountDataStoreImpl aciProtocolStore = ApplicationDependencies.getProtocolStore().aci();
    PreKeyMetadataStore               aciMetadataStore = SignalStore.account().aciPreKeys();

    SignalServiceAccountDataStoreImpl pniProtocolStore = ApplicationDependencies.getProtocolStore().pni();
    PreKeyMetadataStore               pniMetadataStore = SignalStore.account().pniPreKeys();

    storeSignedAndLastResortPreKeys(aciProtocolStore, aciMetadataStore, aciPreKeyCollection);
    storeSignedAndLastResortPreKeys(pniProtocolStore, pniMetadataStore, pniPreKeyCollection);

    RecipientTable recipientTable = SignalDatabase.recipients();
    RecipientId    selfId         = Recipient.trustedPush(aci, pni, registrationData.getE164()).getId();

    recipientTable.setProfileSharing(selfId, true);
    recipientTable.markRegisteredOrThrow(selfId, aci);
    recipientTable.linkIdsForSelf(aci, pni, registrationData.getE164());
    recipientTable.setProfileKey(selfId, registrationData.getProfileKey());

    ApplicationDependencies.getRecipientCache().clearSelf();

    SignalStore.account().setE164(registrationData.getE164());
    SignalStore.account().setFcmToken(registrationData.getFcmToken());
    SignalStore.account().setFcmEnabled(registrationData.isFcm());

    long now = System.currentTimeMillis();
    saveOwnIdentityKey(selfId, aci, aciProtocolStore, now);
    saveOwnIdentityKey(selfId, pni, pniProtocolStore, now);

    SignalStore.account().setServicePassword(registrationData.getPassword());

    if (deviceId != SignalServiceAddress.DEFAULT_DEVICE_ID) {
      SignalServiceAccountManager accountManager = ApplicationDependencies.getSignalServiceAccountManager();
      accountManager.setPreKeys(
          new PreKeyUpload(ServiceIdType.ACI,
                           aciPreKeyCollection.getIdentityKey(),
                           aciPreKeyCollection.getSignedPreKey(),
                           PreKeyUtil.generateAndStoreOneTimeEcPreKeys(aciProtocolStore, aciMetadataStore),
                           null, null));
      accountManager.setPreKeys(
          new PreKeyUpload(ServiceIdType.PNI,
                           pniPreKeyCollection.getIdentityKey(),
                           pniPreKeyCollection.getSignedPreKey(),
                           PreKeyUtil.generateAndStoreOneTimeEcPreKeys(pniProtocolStore, pniMetadataStore),
                           null, null));
    }

    SignalStore.account().setRegistered(true);
    TextSecurePreferences.setPromptedPushRegistration(context, true);
    TextSecurePreferences.setUnauthorizedReceived(context, false);
    NotificationManagerCompat.from(context).cancel(NotificationIds.UNREGISTERED_NOTIFICATION_ID);

    SvrRepository.onRegistrationComplete(masterKey, pin, hasPin, setRegistrationLockEnabled);

    ApplicationDependencies.closeConnections();
    ApplicationDependencies.getIncomingMessageObserver();
    PreKeysSyncJob.enqueue();
  }

  public static PreKeyCollection generateSignedAndLastResortPreKeys(IdentityKeyPair identity, PreKeyMetadataStore metadataStore) {
    SignedPreKeyRecord      signedPreKey          = PreKeyUtil.generateSignedPreKey(metadataStore.getNextSignedPreKeyId(), identity.getPrivateKey());
    KyberPreKeyRecord       lastResortKyberPreKey = PreKeyUtil.generateLastRestortKyberPreKey(metadataStore.getNextKyberPreKeyId(), identity.getPrivateKey());

    return new PreKeyCollection(
        identity.getPublicKey(),
        signedPreKey,
        lastResortKyberPreKey
    );
  }

  private static void storeSignedAndLastResortPreKeys(SignalServiceAccountDataStoreImpl protocolStore, PreKeyMetadataStore metadataStore, PreKeyCollection preKeyCollection) {
    PreKeyUtil.storeSignedPreKey(protocolStore, metadataStore, preKeyCollection.getSignedPreKey());
    metadataStore.setSignedPreKeyRegistered(true);
    metadataStore.setActiveSignedPreKeyId(preKeyCollection.getSignedPreKey().getId());
    metadataStore.setLastSignedPreKeyRotationTime(System.currentTimeMillis());

    PreKeyUtil.storeLastResortKyberPreKey(protocolStore, metadataStore, preKeyCollection.getLastResortKyberPreKey());
    metadataStore.setLastResortKyberPreKeyId(preKeyCollection.getLastResortKyberPreKey().getId());
    metadataStore.setLastResortKyberPreKeyRotationTime(System.currentTimeMillis());
  }

  private void saveOwnIdentityKey(@NonNull RecipientId selfId, @NonNull ServiceId serviceId, @NonNull SignalServiceAccountDataStoreImpl protocolStore, long now) {
    protocolStore.identities().saveIdentityWithoutSideEffects(selfId,
                                                              serviceId,
                                                              protocolStore.getIdentityKeyPair().getPublicKey(),
                                                              IdentityTable.VerifiedStatus.VERIFIED,
                                                              true,
                                                              now,
                                                              true);
  }

  @WorkerThread
  private static @Nullable ProfileKey findExistingProfileKey(@NonNull String e164number) {
    RecipientTable        recipientTable = SignalDatabase.recipients();
    Optional<RecipientId> recipient      = recipientTable.getByE164(e164number);

    if (recipient.isPresent()) {
      return ProfileKeyUtil.profileKeyOrNull(Recipient.resolved(recipient.get()).getProfileKey());
    }

    return null;
  }

  public Single<BackupAuthCheckProcessor> getSvrAuthCredential(@NonNull RegistrationData registrationData, List<String> usernamePasswords, int deviceId) {
    SignalServiceAccountManager accountManager = AccountManagerFactory.getInstance().createUnauthenticated(context, registrationData.getE164(), deviceId, registrationData.getPassword());

    return accountManager.checkBackupAuthCredentials(registrationData.getE164(), usernamePasswords)
                         .map(BackupAuthCheckProcessor::new)
                         .doOnSuccess(processor -> {
                           if (SignalStore.svr().removeAuthTokens(processor.getInvalid())) {
                             new BackupManager(context).dataChanged();
                           }
                         });
  }

}
