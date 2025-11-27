package com.example.compow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class VolumeButtonAccessibilityService : AccessibilityService() {

    private var lastVolumeDownTime = 0L
    private var lastVolumeUpTime = 0L
    private val doublePressThreshold = 500L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (currentTime - lastVolumeDownTime < doublePressThreshold) {
                        triggerAlarm()
                        lastVolumeDownTime = 0L
                        return true
                    }
                    lastVolumeDownTime = currentTime
                }
                
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (currentTime - lastVolumeUpTime < doublePressThreshold) {
                        triggerAlarm()
                        lastVolumeUpTime = 0L
                        return true
                    }
                    lastVolumeUpTime = currentTime
                }
            }
        }
        return false
    }

    private fun triggerAlarm() {
        val intent = Intent(this, AlarmService::class.java)
        startService(intent)
    }
}