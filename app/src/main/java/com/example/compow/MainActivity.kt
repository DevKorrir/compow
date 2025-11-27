package com.example.compow

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.compow.network.SocketIOManager
import com.example.compow.ui.theme.ComPowTheme
import com.example.compow.screens.*
import com.example.compow.utils.PermissionsManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var volumeDetector: VolumeButtonDetector
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var socketManager: SocketIOManager

    // Permission launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "✅ All permissions granted", Toast.LENGTH_SHORT).show()
            initializeApp()
        } else {
            Toast.makeText(
                this,
                "⚠️ Some permissions denied. App may not work properly.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize managers
        permissionsManager = PermissionsManager(this)
        // Corrected Code
        socketManager = SocketIOManager.getInstance()


        // Initialize volume button detector
        volumeDetector = VolumeButtonDetector { // REMOVED the (this) argument
            // The 'this' keyword inside the lambda still correctly refers to MainActivity
            AlarmService.triggerAlarm(this)
            Toast.makeText(this, "🚨 Emergency Alarm Triggered!", Toast.LENGTH_SHORT).show()
        }


        // Request permissions if not granted
        if (!permissionsManager.hasAllPermissions()) {
            requestRequiredPermissions()
        } else {
            initializeApp()
        }

        setContent {
            ComPowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ComPowApp()
                }
            }
        }
    }

    private fun initializeApp() {
        // Connect to Socket.IO server
        lifecycleScope.launch {
            socketManager.connect()

            // Get user ID from preferences
            val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
            val userId = prefs.getString("user_id", null)
            val userName = prefs.getString("user_name", null)

            if (userId != null && userName != null) {
                // Join user room and set online status
                socketManager.joinUserRoom(userId)
                socketManager.setUserOnline(userId, userName)
            }
        }
    }

    private fun requestRequiredPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.VIBRATE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CALL_PHONE
        )

        // Add notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeDetector.onKeyDown(keyCode)) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (volumeDetector.onKeyUp(keyCode)) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()

        // Reconnect Socket.IO if disconnected
        // Corrected version
        if (!socketManager.isConnected) {
            socketManager.connect()
        }


        // Update online status
        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)
        val userName = prefs.getString("user_name", null)

        if (userId != null && userName != null) {
            socketManager.setUserOnline(userId, userName)
        }
    }

    override fun onPause() {
        super.onPause()

        // Set user offline
        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)

        if (userId != null) {
            socketManager.setUserOffline(userId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        volumeDetector.cleanup()

        // Disconnect Socket.IO
        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)

        if (userId != null) {
            socketManager.setUserOffline(userId)
        }

        socketManager.disconnect()
    }
}

@Composable
fun ComPowApp() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
    val isLoggedIn = prefs.getBoolean("is_logged_in", false)

    // Determine start destination
    val startDestination = if (isLoggedIn) "home" else "first"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("first") { FirstPage(navController) }
        composable("signup") { SignupPage(navController) }
        composable("login") { LoginPage(navController) }
        composable("home") { HomePage(navController) }
        composable("settings") { SettingsPage(navController) }
        composable("destination") { DestinationPage(navController) }
    }
}