package com.example.compow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Broadcast receiver for handling system events
 * Currently handles device boot to restore alarm monitoring
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d("AlarmReceiver", "Device booted - ComPow alarm monitoring ready")

                // Check if there was an active alarm before reboot
                val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
                val wasAlarmActive = prefs.getBoolean("alarm_was_active", false)

                if (wasAlarmActive) {
                    // Restart the alarm service
                    AlarmService.triggerAlarm(context)
                    Log.d("AlarmReceiver", "Restarted active alarm after boot")
                }
            }
        }
    }
}