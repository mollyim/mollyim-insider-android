package org.thoughtcrime.securesms.components.settings.app.privacy

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.thoughtcrime.securesms.ScreenLockController
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.jobs.RefreshAttributesJob
import org.thoughtcrime.securesms.jobs.RefreshOwnProfileJob
import org.thoughtcrime.securesms.keyvalue.PhoneNumberPrivacyValues
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.storage.StorageSyncHelper
import org.thoughtcrime.securesms.util.TextSecurePreferences
import org.thoughtcrime.securesms.util.livedata.Store

class PrivacySettingsViewModel(
  private val sharedPreferences: SharedPreferences,
  private val repository: PrivacySettingsRepository
) : ViewModel() {

  private val application: Application = ApplicationDependencies.getApplication()

  private val store = Store(getState())

  val state: LiveData<PrivacySettingsState> = store.stateLiveData

  fun refreshBlockedCount() {
    repository.getBlockedCount { count ->
      store.update { it.copy(blockedCount = count) }
      refresh()
    }
  }

  fun setBlockUnknownEnabled(enabled: Boolean) {
    TextSecurePreferences.setBlockUnknownEnabled(application, enabled)
    refresh()
  }

  fun setReadReceiptsEnabled(enabled: Boolean) {
    if (SignalStore.account().isLinkedDevice) {
      return
    }
    sharedPreferences.edit().putBoolean(TextSecurePreferences.READ_RECEIPTS_PREF, enabled).apply()
    repository.syncReadReceiptState()
    refresh()
  }

  fun setTypingIndicatorsEnabled(enabled: Boolean) {
    if (SignalStore.account().isLinkedDevice) {
      return
    }
    sharedPreferences.edit().putBoolean(TextSecurePreferences.TYPING_INDICATORS, enabled).apply()
    repository.syncTypingIndicatorsState()
    refresh()
  }

  fun setPassphraseLockEnabled(enabled: Boolean) {
    TextSecurePreferences.setPassphraseLockEnabled(application, enabled)
    refresh()
  }

  fun setPassphraseLockTrigger(resultSet: Set<String>) {
    sharedPreferences.edit().putStringSet(TextSecurePreferences.PASSPHRASE_LOCK_TRIGGER, resultSet).apply()
    refresh()
  }

  fun setPassphraseLockTimeout(seconds: Long) {
    sharedPreferences.edit().putLong(TextSecurePreferences.PASSPHRASE_LOCK_TIMEOUT, seconds).apply()
    refresh()
  }

  fun setBiometricScreenLock(enabled: Boolean) {
    TextSecurePreferences.setBiometricScreenLockEnabled(application, enabled)
    ScreenLockController.enableAutoLock(enabled)
    ScreenLockController.lockScreenAtStart = false
    refresh()
  }

  fun setScreenSecurityEnabled(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.SCREEN_SECURITY_PREF, enabled).apply()
    refresh()
  }

  fun setPhoneNumberSharingMode(phoneNumberSharingMode: PhoneNumberPrivacyValues.PhoneNumberSharingMode) {
    SignalStore.phoneNumberPrivacy().phoneNumberSharingMode = phoneNumberSharingMode
    StorageSyncHelper.scheduleSyncForDataChange()
    refresh()
  }

  fun setPhoneNumberListingMode(phoneNumberListingMode: PhoneNumberPrivacyValues.PhoneNumberListingMode) {
    SignalStore.phoneNumberPrivacy().phoneNumberListingMode = phoneNumberListingMode
    StorageSyncHelper.scheduleSyncForDataChange()
    ApplicationDependencies.getJobManager().startChain(RefreshAttributesJob()).then(RefreshOwnProfileJob()).enqueue()
    refresh()
  }

  fun setIncognitoKeyboard(enabled: Boolean) {
    sharedPreferences.edit().putBoolean(TextSecurePreferences.INCOGNITO_KEYBORAD_PREF, enabled).apply()
    refresh()
  }

  fun refresh() {
    store.update(this::updateState)
  }

  private fun getState(): PrivacySettingsState {
    return PrivacySettingsState(
      blockedCount = 0,
      blockUnknown = TextSecurePreferences.isBlockUnknownEnabled(application),
      readReceipts = TextSecurePreferences.isReadReceiptsEnabled(application),
      typingIndicators = TextSecurePreferences.isTypingIndicatorsEnabled(application),
      passphraseLock = TextSecurePreferences.isPassphraseLockEnabled(application),
      passphraseLockTriggerValues = TextSecurePreferences.getPassphraseLockTrigger(application).triggers,
      passphraseLockTimeout = TextSecurePreferences.getPassphraseLockTimeout(application),
      biometricScreenLock = TextSecurePreferences.isBiometricScreenLockEnabled(application),
      screenSecurity = TextSecurePreferences.isScreenSecurityEnabled(application),
      incognitoKeyboard = TextSecurePreferences.isIncognitoKeyboardEnabled(application),
      seeMyPhoneNumber = SignalStore.phoneNumberPrivacy().phoneNumberSharingMode,
      findMeByPhoneNumber = SignalStore.phoneNumberPrivacy().phoneNumberListingMode,
      universalExpireTimer = SignalStore.settings().universalExpireTimer
    )
  }

  private fun updateState(state: PrivacySettingsState): PrivacySettingsState {
    return getState().copy(blockedCount = state.blockedCount)
  }

  class Factory(
    private val sharedPreferences: SharedPreferences,
    private val repository: PrivacySettingsRepository
  ) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      return requireNotNull(modelClass.cast(PrivacySettingsViewModel(sharedPreferences, repository)))
    }
  }
}
