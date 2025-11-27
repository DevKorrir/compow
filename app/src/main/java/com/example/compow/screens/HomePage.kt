package com.example.compow.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.compow.AlarmService
import com.example.compow.ComPowApplication
import com.example.compow.data.AlertLogEntity
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = (context.applicationContext as ComPowApplication).database
    val alertLogDao = database.alertLogDao()

    var showMenu by remember { mutableStateOf(false) }
    var showAlertHistory by remember { mutableStateOf(false) }
    var alarmActive by remember { mutableStateOf(false) }

    // Get settings from SharedPreferences
    val prefs = context.getSharedPreferences("compow_prefs", Context.MODE_PRIVATE)
    val circleEnabled = prefs.getBoolean("circle_enabled", true)
    val groupEnabled = prefs.getBoolean("group_enabled", true)
    val communityEnabled = prefs.getBoolean("community_enabled", false)

    // Alert history - USES: getRecentAlerts()
    var recentAlerts by remember { mutableStateOf<List<AlertLogEntity>>(emptyList()) }
    var activeAlert by remember { mutableStateOf<AlertLogEntity?>(null) }
    var alertCount by remember { mutableIntStateOf(0) }
    var activeAlertCount by remember { mutableIntStateOf(0) }

    // Load alert data
    LaunchedEffect(Unit) {
        scope.launch {
            withContext(Dispatchers.IO) {
                // USES: getActiveAlert()
                activeAlert = alertLogDao.getActiveAlert()

                // USES: getRecentAlerts()
                recentAlerts = alertLogDao.getRecentAlerts(10)

                // USES: getAlertLogCount()
                alertCount = alertLogDao.getAlertLogCount()

                // USES: getActiveAlertCount()
                activeAlertCount = alertLogDao.getActiveAlertCount()
            }
        }
    }

    // Check if there's an active alarm
    alarmActive = activeAlert != null && !activeAlert!!.isResolved

    // Permission launcher for calling
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(Intent.ACTION_DIAL)
            context.startActivity(intent)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB3E5FC))
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top Section with Icons and Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Contact Type Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FlickeringContactIcon(
                        icon = Icons.Default.Person,
                        label = "Circle",
                        enabled = circleEnabled
                    )

                    FlickeringContactIcon(
                        icon = Icons.Default.Group,
                        label = "Group",
                        enabled = groupEnabled
                    )

                    FlickeringContactIcon(
                        icon = Icons.Default.Groups,
                        label = "Community",
                        enabled = communityEnabled
                    )
                }

                // Menu Button
                Box {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Badge(
                            containerColor = if (activeAlertCount > 0) Color.Red else Color.Transparent
                        ) {
                            if (activeAlertCount > 0) {
                                Text("$activeAlertCount", color = Color.White, fontSize = 10.sp)
                            }
                        }
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = {
                                showMenu = false
                                navController.navigate("settings")
                            },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Contact") },
                            onClick = {
                                showMenu = false
                                navController.navigate("destination")
                            },
                            leadingIcon = { Icon(Icons.Default.Contacts, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Alert History")
                                    Badge { Text("$alertCount") }
                                }
                            },
                            onClick = {
                                showMenu = false
                                showAlertHistory = true
                            },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Alert Banner
            if (alarmActive && activeAlert != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🚨 ACTIVE EMERGENCY",
                                fontWeight = FontWeight.Bold,
                                color = Color.Red,
                                fontSize = 14.sp
                            )
                            Text(
                                "${activeAlert!!.contactsNotified} contacts notified",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Google Maps Section
            MapSection(modifier = Modifier
                .fillMaxWidth()
                .height(if (alarmActive) 320.dp else 380.dp)
                .padding(horizontal = 16.dp))

            Spacer(modifier = Modifier.weight(1f))

            // Bottom Control Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stop Alarm Button
                Button(
                    onClick = {
                        alarmActive = false
                        AlarmService.stopAlarm(context)

                        // Reload alert status
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                activeAlert = alertLogDao.getActiveAlert()
                                recentAlerts = alertLogDao.getRecentAlerts(10)
                                activeAlertCount = alertLogDao.getActiveAlertCount()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(65.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (alarmActive) Color.Red else Color(0xFF2962FF)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Text(
                        if (alarmActive) "STOP ALARM" else "Stop Alarm",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Call Button
                FloatingActionButton(
                    onClick = {
                        when (PackageManager.PERMISSION_GRANTED) {
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CALL_PHONE
                            ) -> {
                                val intent = Intent(Intent.ACTION_DIAL)
                                context.startActivity(intent)
                            }
                            else -> {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        }
                    },
                    containerColor = Color(0xFF4CAF50),
                    modifier = Modifier.size(65.dp),
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Alert History Dialog - USES: getRecentAlerts()
    val onDismiss = { showAlertHistory = false }
    if (showAlertHistory) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Alert History")
                    Badge { Text("$alertCount") }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentAlerts) { alert ->
                        AlertHistoryItem(alert)
                    }

                    if (recentAlerts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No alerts yet",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
fun AlertHistoryItem(alert: AlertLogEntity) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (alert.isResolved) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        if (alert.isResolved) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (alert.isResolved) Color(0xFF4CAF50) else Color.Red,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        alert.alertType.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Text(
                    dateFormat.format(Date(alert.timestamp)),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Text(
                "${alert.contactsNotified} contacts notified",
                fontSize = 12.sp,
                color = Color.Gray
            )

            if (alert.isResolved && alert.resolvedAt != null) {
                Text(
                    "Resolved at ${dateFormat.format(Date(alert.resolvedAt))}",
                    fontSize = 11.sp,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun FlickeringContactIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flicker_$label")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha_$label"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(
                    color = if (enabled)
                        Color(0xFF4CAF50).copy(alpha = alpha)
                    else
                        Color.Gray.copy(alpha = 0.5f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            label,
            fontSize = 11.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun MapSection(modifier: Modifier = Modifier) {
    val defaultLocation = LatLng(-0.0469, 37.6494)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 14f)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = true,
                    myLocationButtonEnabled = true
                )
            ) {

                Marker(
                    state = rememberMarkerState(position = defaultLocation),
                    title = "Your Location",
                    snippet = "You are here"
                )
                Marker(
                    state = rememberMarkerState(position = LatLng(-0.0450, 37.6500)),
                    title = "Meru Hospital",
                    snippet = "2.1 km away"
                )
                Marker(
                    state = rememberMarkerState(position = LatLng(-0.0480, 37.6510)),
                    title = "Police Station",
                    snippet = "1.5 km away"
                )
            }

            // Overlay UI outside GoogleMap
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.9f)
                    ),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color(0xFF2962FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Nearby facilities",
                            fontSize = 12.sp,
                            color = Color.Black
                        )
                    }
                }
            }
        }
    }
}