package org.thoughtcrime.securesms.components.reminder;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;

import im.molly.unifiedpush.util.UnifiedPushHelper;

@SuppressLint("BatteryLife")
public class DozeReminder extends Reminder {

  @RequiresApi(api = 23)
  public DozeReminder(@NonNull final Context context) {
    super(getDozeTitle(context),
          getDozeText(context));

    setOkListener(v -> {
      TextSecurePreferences.setPromptedOptimizeDoze(context, true);
      Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                 Uri.parse("package:" + context.getPackageName()));
      context.startActivity(intent);
    });

    setDismissListener(v -> TextSecurePreferences.setPromptedOptimizeDoze(context, true));
  }

  public static boolean isEligible(Context context) {
    return !SignalStore.account().isFcmEnabled()                   &&
           !TextSecurePreferences.hasPromptedOptimizeDoze(context) &&
           !UnifiedPushHelper.isUnifiedPushAvailable()             &&
           !((PowerManager)context.getSystemService(Context.POWER_SERVICE)).isIgnoringBatteryOptimizations(context.getPackageName());
  }

  private static String getDozeTitle(Context context) {
    if (BuildConfig.USE_PLAY_SERVICES) {
      return context.getString(R.string.DozeReminder_optimize_for_missing_play_services);
    } else {
      return context.getString(R.string.DozeReminder_optimize_for_timely_notifications);
    }
  }

  private static String getDozeText(Context context) {
    if (BuildConfig.USE_PLAY_SERVICES) {
      return context.getString(R.string.DozeReminder_this_device_does_not_support_play_services_tap_to_disable_system_battery);
    } else {
      return context.getString(R.string.DozeReminder_tap_to_allow_molly_to_retrieve_messages_while_the_device_is_in_standby);
    }
  }
}
