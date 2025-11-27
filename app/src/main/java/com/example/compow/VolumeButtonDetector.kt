package com.example.compow

import android.view.KeyEvent
import kotlinx.coroutines.*

/**
 * Detects a rapid double-press of both volume buttons.
 * This should be integrated into an Activity to handle key events.
 */
class VolumeButtonDetector(
    private val onAlarmTrigger: () -> Unit
) {
    private var volumeUpPressed = false
    private var volumeDownPressed = false
    private var pressJob: Job? = null
    private val doublePressDelay = 300L // 300 milliseconds for a double-press

    fun onKeyDown(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeUpPressed = true
                checkBothPressed()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeDownPressed = true
                checkBothPressed()
                return true
            }
        }
        return false
    }

    fun onKeyUp(keyCode: Int): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volumeUpPressed = false
                cancelCheck()
                return true
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volumeDownPressed = false
                cancelCheck()
                return true
            }
        }
        return false
    }

    private fun checkBothPressed() {
        if (volumeUpPressed && volumeDownPressed) {
            pressJob?.cancel()
            pressJob = CoroutineScope(Dispatchers.Main).launch {
                delay(doublePressDelay)
                if (volumeUpPressed && volumeDownPressed) {
                    onAlarmTrigger()
                }
            }
        }
    }

    private fun cancelCheck() {
        if (!volumeUpPressed || !volumeDownPressed) {
            pressJob?.cancel()
            pressJob = null
        }
    }

    fun cleanup() {
        pressJob?.cancel()
        volumeUpPressed = false
        volumeDownPressed = false
    }
}