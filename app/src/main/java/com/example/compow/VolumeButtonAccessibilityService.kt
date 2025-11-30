package com.example.compow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeButtonAccessibilityService : AccessibilityService() {

    private var lastVolumeDownTime = 0L
    private var lastVolumeUpTime = 0L
    private val doublePressThreshold = 300L // 300ms for a more responsive double-press

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()

            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (currentTime - lastVolumeDownTime < doublePressThreshold) {
                        triggerAlarm()
                        lastVolumeDownTime = 0L // Reset after triggering
                        return true // Consume the event
                    }
                    lastVolumeDownTime = currentTime
                }

                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (currentTime - lastVolumeUpTime < doublePressThreshold) {
                        triggerAlarm()
                        lastVolumeUpTime = 0L // Reset after triggering
                        return true // Consume the event
                    }
                    lastVolumeUpTime = currentTime
                }
            }
        }
        return super.onKeyEvent(event) // Pass unhandled events to the system
    }

    private fun triggerAlarm() {
        val intent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_TRIGGER_ALARM
        }

        // Use startForegroundService for modern Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}