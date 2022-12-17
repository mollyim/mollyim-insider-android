package org.thoughtcrime.securesms.keyvalue;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

public final class RegistrationValues extends SignalStoreValues {

  private static final String REGISTRATION_COMPLETE        = "registration.complete";
  private static final String PIN_REQUIRED                 = "registration.pin_required";
  private static final String HAS_UPLOADED_PROFILE         = "registration.has_uploaded_profile";
  private static final String NEED_DOWNLOAD_PROFILE        = "registration.need_download_profile";
  private static final String NEED_DOWNLOAD_PROFILE_AVATAR = "registration.need_download_profile_avatar";

  RegistrationValues(@NonNull KeyValueStore store) {
    super(store);
  }

  public synchronized void onFirstEverAppLaunch() {
    getStore().beginWrite()
              .putBoolean(HAS_UPLOADED_PROFILE, false)
              .putBoolean(NEED_DOWNLOAD_PROFILE, false)
              .putBoolean(NEED_DOWNLOAD_PROFILE_AVATAR, false)
              .putBoolean(REGISTRATION_COMPLETE, false)
              .putBoolean(PIN_REQUIRED, true)
              .commit();
  }

  @Override
  @NonNull List<String> getKeysToIncludeInBackup() {
    return Collections.emptyList();
  }

  public synchronized void clearRegistrationComplete() {
    onFirstEverAppLaunch();
  }

  public synchronized void setRegistrationComplete() {
    getStore().beginWrite()
              .putBoolean(REGISTRATION_COMPLETE, true)
              .commit();
  }

  @CheckResult
  public synchronized boolean pinWasRequiredAtRegistration() {
    return getStore().getBoolean(PIN_REQUIRED, false);
  }

  @CheckResult
  public synchronized boolean isRegistrationComplete() {
    return getStore().getBoolean(REGISTRATION_COMPLETE, true);
  }

  public boolean hasUploadedProfile() {
    return getBoolean(HAS_UPLOADED_PROFILE, true);
  }

  public void markHasUploadedProfile() {
    putBoolean(HAS_UPLOADED_PROFILE, true);
  }

  public void clearHasUploadedProfile() {
    putBoolean(HAS_UPLOADED_PROFILE, false);
  }

  public void markNeedDownloadProfileAndAvatar() {
    putBoolean(NEED_DOWNLOAD_PROFILE, true);
    putBoolean(NEED_DOWNLOAD_PROFILE_AVATAR, true);
  }

  public boolean needDownloadProfileOrAvatar() {
    return getBoolean(NEED_DOWNLOAD_PROFILE, true) || getBoolean(NEED_DOWNLOAD_PROFILE_AVATAR, true);
  }

  public void clearNeedDownloadProfile() {
    putBoolean(NEED_DOWNLOAD_PROFILE, false);
  }

  public void clearNeedDownloadProfileAvatar() {
    putBoolean(NEED_DOWNLOAD_PROFILE_AVATAR, false);
  }
}
