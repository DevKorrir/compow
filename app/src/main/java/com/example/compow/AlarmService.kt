package com.example.compow

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.compow.data.*
import com.example.compow.network.SocketIOManager
import com.example.compow.utils.LocationHelper
import kotlinx.coroutines.*
import androidx.core.content.edit

class AlarmService : Service() {

    private var isAlarmActive = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var locationHelper: LocationHelper
    private lateinit var contactDao: ContactDao
    private lateinit var userDao: UserDao
    private lateinit var alertLogDao: AlertLogDao
    private lateinit var socketManager: SocketIOManager

    companion object {
        const val ACTION_TRIGGER_ALARM = "com.example.compow.TRIGGER_ALARM"
        const val ACTION_STOP_ALARM = "com.example.compow.STOP_ALARM"
        const val CHANNEL_ID = "compow_emergency_channel"
        const val NOTIFICATION_ID = 1001

        fun triggerAlarm(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_TRIGGER_ALARM
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAlarm(context: Context) {
            val intent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationHelper = LocationHelper(this)
        socketManager = SocketIOManager.getInstance()

        val database = (application as ComPowApplication).database
        contactDao = database.contactDao()
        userDao = database.userDao()
        alertLogDao = database.alertLogDao()

        createNotificationChannel()

        // ✅ FIX: Access StateFlow value with .value
        if (!socketManager.isConnected.value) {
            socketManager.connect()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TRIGGER_ALARM -> {
                startForeground(NOTIFICATION_ID, createForegroundNotification())
                serviceScope.launch {
                    triggerAlarm()
                }
            }
            ACTION_STOP_ALARM -> {
                serviceScope.launch {
                    stopAlarm()
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }

                try {
                    NotificationManagerCompat.from(this).cancel(NOTIFICATION_ID + 1)
                } catch (e: SecurityException) {
                    Log.e("AlarmService", "Failed to cancel notification: ${e.message}")
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency alert notifications"
                enableVibration(true)
                enableLights(true)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🚨 Emergency Alarm Active")
            .setContentText("Notifying emergency contacts via Socket.IO...")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Alarm",
                stopPendingIntent
            )
            .build()
    }

    private suspend fun triggerAlarm() {
        if (isAlarmActive) return
        isAlarmActive = true

        Log.d("AlarmService", "🚨 EMERGENCY ALARM TRIGGERED")

        // 1. Trigger vibration feedback
        triggerVibration()

        // 2. Get current location
        val location = locationHelper.getLocationWithFallback()
        val locationUrl = if (location != null) {
            locationHelper.getGoogleMapsUrl(location)
        } else {
            null
        }

        // 3. Get user information
        val user = userDao.getCurrentUser()
        val userId = user?.userId ?: "unknown"
        val userName = user?.fullName ?: "A ComPow User"

        // 4. Get custom emergency message
        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
        val customMessage = prefs.getString("default_message", "")

        val baseMessage = if (!customMessage.isNullOrEmpty()) {
            customMessage
        } else {
            "I'm in an EMERGENCY! I need help immediately!"
        }

        val fullMessage = buildString {
            append("$userName: $baseMessage")
            if (locationUrl != null) {
                append("\n\n📍 Location: $locationUrl")
            } else {
                append("\n\n📍 Location: Unavailable")
            }
            append("\n⏰ Time: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
        }

        // 5. Get contacts to notify
        val contactsToNotify = getContactsToNotify()

        if (contactsToNotify.isEmpty()) {
            Log.w("AlarmService", "⚠️ No emergency contacts configured!")
            showEmergencyNotification("⚠️ No emergency contacts configured!")
            return
        }

        // 6. Extract contact IDs for Socket.IO
        val contactIds = contactsToNotify.map { it.id.toString() }
        val contactPhones = contactsToNotify.map { it.phoneNumber }

        Log.d("AlarmService", "📤 Sending alert to ${contactsToNotify.size} contacts")

        // 7. Send via Socket.IO first (primary method)
        var socketSuccess = false
        // ✅ FIX: Access StateFlow value with .value
        if (socketManager.isConnected.value) {
            socketManager.sendEmergencyAlert(
                fromUserId = userId,
                fromUserName = userName,
                message = fullMessage,
                latitude = location?.latitude,
                longitude = location?.longitude,
                contactIds = contactIds
            ) { success, error ->
                socketSuccess = success
                if (success) {
                    Log.d("AlarmService", "✅ Socket.IO alert sent successfully")
                } else {
                    Log.e("AlarmService", "❌ Socket.IO failed: $error")
                }
            }

            // Wait a bit for Socket.IO response
            delay(2000)
        } else {
            Log.w("AlarmService", "⚠️ Socket.IO not connected, falling back to SMS")
        }

        // 8. Fallback to SMS if Socket.IO failed or not connected
        if (!socketSuccess) {
            Log.d("AlarmService", "📱 Sending SMS fallback")
            sendAlertsViaSMS(contactPhones, fullMessage)
        }

        // 9. Log the alert in database
        val alertId = alertLogDao.insertAlertLog(
            AlertLogEntity(
                alertType = AlertType.EMERGENCY,
                message = fullMessage,
                latitude = location?.latitude,
                longitude = location?.longitude,
                contactsNotified = contactsToNotify.size,
                isResolved = false
            )
        )

        // 10. Save alert ID
        prefs.edit {
            putLong("current_alert_id", alertId)
            putBoolean("alarm_was_active", true)
        }

        // 11. Show local notification
        val method = if (socketSuccess) "Socket.IO" else "SMS"
        showEmergencyNotification(
            "✅ Alert sent to ${contactsToNotify.size} contact(s) via $method"
        )

        Log.d("AlarmService", "✅ Emergency alert process completed")
    }

    private suspend fun stopAlarm() {
        if (!isAlarmActive) return
        isAlarmActive = false

        Log.d("AlarmService", "🛑 Stopping emergency alarm")

        // 1. Get user info
        val user = userDao.getCurrentUser()
        val userId = user?.userId ?: "unknown"
        val userName = user?.fullName ?: "A ComPow User"

        val safeMessage = "$userName: ✅ I am now SAFE. Thank you for your concern.\n⏰ ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}"

        // 2. Get contacts
        val contactsToNotify = getContactsToNotify()
        val contactIds = contactsToNotify.map { it.id.toString() }
        val contactPhones = contactsToNotify.map { it.phoneNumber }

        // 3. Send safe message via Socket.IO
        var socketSuccess = false
        // ✅ FIX: Access StateFlow value with .value
        if (socketManager.isConnected.value) {
            socketManager.sendSafeAlert(
                fromUserId = userId,
                fromUserName = userName,
                message = safeMessage,
                contactIds = contactIds
            ) { success, error ->
                socketSuccess = success
                if (success) {
                    Log.d("AlarmService", "✅ Safe alert sent via Socket.IO")
                } else {
                    Log.e("AlarmService", "❌ Socket.IO failed: $error")
                }
            }
            delay(2000)
        }

        // 4. Fallback to SMS
        if (!socketSuccess && contactPhones.isNotEmpty()) {
            sendAlertsViaSMS(contactPhones, safeMessage)
        }

        // 5. Resolve alert in database
        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
        val alertId = prefs.getLong("current_alert_id", -1)
        if (alertId != -1L) {
            alertLogDao.resolveAlert(alertId)
        }

        // 6. Clear active alert flags
        prefs.edit {
            remove("current_alert_id")
            putBoolean("alarm_was_active", false)
        }

        Log.d("AlarmService", "✅ Safe alert sent to ${contactsToNotify.size} contacts")
    }

    private suspend fun getContactsToNotify(): List<ContactEntity> {
        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
        val allContacts = mutableListOf<ContactEntity>()

        // Get enabled contacts from each category
        if (prefs.getBoolean("circle_enabled", false)) {
            allContacts.addAll(contactDao.getContactsByCategory(ContactCategory.CIRCLE))
        }
        if (prefs.getBoolean("group_enabled", false)) {
            allContacts.addAll(contactDao.getContactsByCategory(ContactCategory.GROUP))
        }
        if (prefs.getBoolean("community_enabled", false)) {
            allContacts.addAll(contactDao.getContactsByCategory(ContactCategory.COMMUNITY))
        }

        // Return only enabled contacts, remove duplicates
        return allContacts.filter { it.isEnabled }.distinctBy { it.phoneNumber }
    }

    private fun triggerVibration() {
        try {
            val vibrator = getSystemService(Vibrator::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }
            Log.d("AlarmService", "📳 Vibration triggered")
        } catch (e: Exception) {
            Log.e("AlarmService", "Vibration failed: ${e.message}")
        }
    }

    private fun sendAlertsViaSMS(phoneNumbers: List<String>, message: String) {
        phoneNumbers.forEach { phoneNumber ->
            sendSMS(phoneNumber, message)
        }
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                phoneNumber,
                null,
                parts,
                null,
                null
            )

            Log.d("AlarmService", "📱 SMS sent to $phoneNumber")
        } catch (_: SecurityException) {
            Log.e("AlarmService", "❌ SMS permission denied for $phoneNumber")
        } catch (e: Exception) {
            Log.e("AlarmService", "❌ Failed to send SMS to $phoneNumber: ${e.message}")
        }
    }

    private fun showEmergencyNotification(message: String) {
        try {
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Emergency Alert")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID + 1, notification)
        } catch (e: SecurityException) {
            Log.e("AlarmService", "Notification permission denied: ${e.message}")
        }
    }
}