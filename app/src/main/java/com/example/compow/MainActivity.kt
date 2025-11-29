package com.example.compow

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.compow.network.SocketIOManager
import com.example.compow.screens.*
import com.example.compow.ui.theme.ComPowTheme
import com.example.compow.utils.PermissionsManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var permissionsManager: PermissionsManager
    private lateinit var socketManager: SocketIOManager

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

        permissionsManager = PermissionsManager(this)
        socketManager = SocketIOManager.getInstance()

        if (permissionsManager.hasAllPermissions()) {
            initializeApp()
        } else {
            requestRequiredPermissions()
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
        lifecycleScope.launch {
            socketManager.connect()

            val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
            val userId = prefs.getString("user_id", null)
            val userName = prefs.getString("user_name", null)

            if (userId != null && userName != null) {
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            if (!socketManager.isConnected.value) {
                socketManager.connect()
            }

            val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
            val userId = prefs.getString("user_id", null)
            val userName = prefs.getString("user_name", null)

            if (userId != null && userName != null) {
                socketManager.setUserOnline(userId, userName)
            }
        }
    }

    override fun onPause() {
        super.onPause()

        val prefs = getSharedPreferences("compow_prefs", MODE_PRIVATE)
        val userId = prefs.getString("user_id", null)

        if (userId != null) {
            socketManager.setUserOffline(userId)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

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
