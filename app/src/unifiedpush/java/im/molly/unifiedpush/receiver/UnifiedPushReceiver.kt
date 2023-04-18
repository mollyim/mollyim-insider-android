package im.molly.unifiedpush.receiver

import android.content.Context
import android.os.Build
import android.os.PowerManager
import im.molly.unifiedpush.events.UnifiedPushRegistrationEvent
import im.molly.unifiedpush.model.FetchStrategy
import im.molly.unifiedpush.model.UnifiedPushStatus
import im.molly.unifiedpush.model.saveStatus
import im.molly.unifiedpush.util.MollySocketRequest
import im.molly.unifiedpush.util.UnifiedPushHelper
import im.molly.unifiedpush.util.UnifiedPushNotificationBuilder
import org.greenrobot.eventbus.EventBus
import org.signal.core.util.concurrent.SignalExecutors
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.gcm.FcmFetchManager
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.util.FeatureFlags
import org.thoughtcrime.securesms.util.concurrent.SerialMonoLifoExecutor
import org.unifiedpush.android.connector.MessagingReceiver
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.schedule

class UnifiedPushReceiver : MessagingReceiver() {
  private val TAG = Log.tag(UnifiedPushReceiver::class.java)
  private val TIMEOUT = 20_000L // 20secs
  private val WAKE_LOCK_TAG = "${UnifiedPushReceiver::class.java}::wake_lock"
  private val EXECUTOR = SerialMonoLifoExecutor(SignalExecutors.UNBOUNDED)
  // Same than org.thoughtcrime.securesms.gcm.FcmReceiveService.FCM_FOREGROUND_INTERVAL
  private val FOREGROUND_INTERVAL = TimeUnit.MINUTES.toMillis(3)

  override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
    Log.d(TAG, "New endpoint: $endpoint")
    if (SignalStore.unifiedpush().endpoint != endpoint) {
      SignalStore.unifiedpush().endpoint = endpoint
      when (SignalStore.unifiedpush().status) {
        UnifiedPushStatus.AIR_GAPED -> {
          EventBus.getDefault().post(UnifiedPushRegistrationEvent)
          UnifiedPushNotificationBuilder(context).setNotificationEndpointChangedAirGaped()
        }
        UnifiedPushStatus.OK -> {
          EXECUTOR.enqueue {
            MollySocketRequest.registerToMollySocketServer().saveStatus()
            EventBus.getDefault().post(UnifiedPushRegistrationEvent)
            if (SignalStore.unifiedpush().status != UnifiedPushStatus.OK)
              UnifiedPushNotificationBuilder(context).setNotificationEndpointChangedError()
          }
        }
        in listOf(
          UnifiedPushStatus.INTERNAL_ERROR,
          UnifiedPushStatus.MISSING_ENDPOINT,
        ) -> {
          EXECUTOR.enqueue {
            MollySocketRequest.registerToMollySocketServer().saveStatus()
            EventBus.getDefault().post(UnifiedPushRegistrationEvent)
          }
        }
        else -> {
          EventBus.getDefault().post(UnifiedPushRegistrationEvent)
        }
      }
    }
  }

  override fun onRegistrationFailed(context: Context, instance: String) {
    // called when the registration is not possible, eg. no network
    UnifiedPushNotificationBuilder(context).setNotificationRegistrationFailed()
  }

  override fun onUnregistered(context: Context, instance: String) {
    // called when this application is unregistered from receiving push messages
    // isPushAvailable becomes false => The websocket starts
    SignalStore.unifiedpush().endpoint = null
    EventBus.getDefault().post(UnifiedPushRegistrationEvent)
  }

  override fun onMessage(context: Context, message: ByteArray, instance: String) {
    if (UnifiedPushHelper.isUnifiedPushAvailable()) {
      when (SignalStore.unifiedpush().fetchStrategy) {
        FetchStrategy.WEBSOCKET -> messageWebSocket()
        FetchStrategy.REST -> messageRest(context)
      }
      Log.d(TAG, "New message")
    }
  }

  private fun messageRest(context: Context) {
    EXECUTOR.enqueue {
      val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).run {
        newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
          acquire(5000L /*5secs*/)
        }
      }
      val timeSinceLastRefresh = System.currentTimeMillis() - SignalStore.misc().lastFcmForegroundServiceTime
      val enqueueSuccessful = try {
        if (FeatureFlags.useFcmForegroundService()) {
          SignalStore.misc().lastFcmForegroundServiceTime = System.currentTimeMillis()
          FcmFetchManager.enqueue(context, true)
        } else if (Build.VERSION.SDK_INT >= 31 && timeSinceLastRefresh > FOREGROUND_INTERVAL) {
          SignalStore.misc().lastFcmForegroundServiceTime = System.currentTimeMillis()
          FcmFetchManager.enqueue(context, true)
        } else {
          FcmFetchManager.enqueue(context, false)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to start service.", e)
        false
      }
      if (!enqueueSuccessful) {
        Log.w(TAG, "Unable to start service. Falling back to legacy approach.")
        FcmFetchManager.retrieveMessages(context)
      }
      wakeLock?.let {
        if (it.isHeld) {
          it.release()
        }
      }
    }
  }

  private fun messageWebSocket() {
    ApplicationDependencies.getIncomingMessageObserver().registerKeepAliveToken(UnifiedPushReceiver::class.java.name)
    Timer().schedule(TIMEOUT) {
      ApplicationDependencies.getIncomingMessageObserver().removeKeepAliveToken(UnifiedPushReceiver::class.java.name)
    }
  }
}
