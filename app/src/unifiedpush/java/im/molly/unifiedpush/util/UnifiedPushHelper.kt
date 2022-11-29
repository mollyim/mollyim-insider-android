package im.molly.unifiedpush.util

import im.molly.unifiedpush.device.MollySocketLinkedDevice
import im.molly.unifiedpush.model.FetchStrategy
import im.molly.unifiedpush.model.UnifiedPushStatus
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.unifiedpush.android.connector.UnifiedPush

object UnifiedPushHelper{
  private val TAG = Log.tag(UnifiedPushHelper::class.java)
  private val context = ApplicationDependencies.getApplication()

  // return false if the initialization failed
  fun initializeMollySocketLinkedDevice(): Boolean {
    if (SignalStore.account().isRegistered) {
      Log.d(TAG, "Initializing UnifiedPush")
      MollySocketLinkedDevice().device ?: run {
        Log.w(TAG, "Can't initialize the linked device for MollySocket")
        return false
      }
      Log.d(TAG, "MollyDevice found")
    } else {
      return false
    }
    return true
  }

  @JvmStatic
  fun isUnifiedPushAvailable(): Boolean {
    return SignalStore.account().isRegistered &&
      SignalStore.unifiedpush().status in listOf(UnifiedPushStatus.OK, UnifiedPushStatus.AIR_GAPED)
  }

  @JvmStatic
  fun isPushAvailable(): Boolean {
    return SignalStore.account().fcmEnabled || isUnifiedPushAvailable()
  }

  @JvmStatic
  fun pushRequireForeground(): Boolean {
    // (A) if (no FCM) and (no UnifiedPush) -> foreground
    // (B) if (no FCM) and (UnifiedPush and websocket) -> foreground
    // (A or B) if (no FCM) and (no UnifiedPush or (UnifiedPush and websocket) -> foreground
    // not (A or B) = (FCM or (unifiedpush and REST)) -> no foreground
    return !(SignalStore.account().fcmEnabled || (isUnifiedPushAvailable() && SignalStore.unifiedpush().fetchStrategy == FetchStrategy.REST))
  }

  fun checkDistributorPresence() {
    if (UnifiedPush.getDistributor(context).isEmpty()) {
      SignalStore.unifiedpush().endpoint = null
    }
  }
}
