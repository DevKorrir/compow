package com.example.compow.network

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.URISyntaxException

class SocketIOManager private constructor() {

    private var socket: Socket? = null

    var isConnected = false
        private set

    companion object {
        @Volatile
        private var INSTANCE: SocketIOManager? = null

        // Your Socket.IO server URL - CHANGE THIS to your actual server
        private const val SERVER_URL = "http://your-server-url.com:3000"

        // Socket events (only used ones)
        const val EVENT_CONNECT = Socket.EVENT_CONNECT
        const val EVENT_DISCONNECT = Socket.EVENT_DISCONNECT
        const val EVENT_EMERGENCY_ALERT = "emergency_alert"
        const val EVENT_SAFE_ALERT = "safe_alert"
        const val EVENT_JOIN_ROOM = "join_room"
        const val EVENT_USER_ONLINE = "user_online"
        const val EVENT_USER_OFFLINE = "user_offline"

        fun getInstance(): SocketIOManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SocketIOManager().also {
                    INSTANCE = it
                }
            }
        }
    }

    // Define listeners as properties BEFORE init block
    private val onConnect = Emitter.Listener {
        isConnected = true
        Log.d("SocketIOManager", "Socket connected successfully")
    }

    private val onDisconnect = Emitter.Listener {
        isConnected = false
        Log.d("SocketIOManager", "Socket disconnected")
    }

    private val onEmergencyAlert = Emitter.Listener { args ->
        if (args.isNotEmpty()) {
            val data = args[0] as? JSONObject
            Log.d("SocketIOManager", "Emergency alert received: $data")
            // Handle emergency alert
            CoroutineScope(Dispatchers.Main).launch {
                // Show notification or update UI
            }
        }
    }

    private val onSafeAlert = Emitter.Listener { args ->
        if (args.isNotEmpty()) {
            val data = args[0] as? JSONObject
            Log.d("SocketIOManager", "Safe alert received: $data")
            // Handle safe alert
        }
    }

    init {
        try {
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Integer.MAX_VALUE
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                timeout = 20000
            }

            socket = IO.socket(SERVER_URL, opts)
            setupSocketListeners()

        } catch (e: URISyntaxException) {
            Log.e("SocketIOManager", "Socket initialization error: ${e.message}")
        }
    }

    private fun setupSocketListeners() {
        socket?.apply {
            on(EVENT_CONNECT, onConnect)
            on(EVENT_DISCONNECT, onDisconnect)
            on(EVENT_EMERGENCY_ALERT, onEmergencyAlert)
            on(EVENT_SAFE_ALERT, onSafeAlert)
        }
    }

    /**
     * Connect to Socket.IO server
     */
    fun connect() {
        if (!isConnected) {
            socket?.connect()
            Log.d("SocketIOManager", "Connecting to server...")
        }
    }

    /**
     * Disconnect from Socket.IO server
     */
    fun disconnect() {
        socket?.disconnect()
        isConnected = false
        Log.d("SocketIOManager", "Disconnected from server")
    }

    /**
     * Join a user room for receiving personal messages
     */
    fun joinUserRoom(userId: String) {
        if (isConnected) {
            val data = JSONObject().apply {
                put("userId", userId)
            }
            socket?.emit(EVENT_JOIN_ROOM, data)
            Log.d("SocketIOManager", "Joined room: $userId")
        }
    }

    /**
     * Send emergency alert to contacts via Socket.IO
     */
    fun sendEmergencyAlert(
        fromUserId: String,
        fromUserName: String,
        message: String,
        latitude: Double?,
        longitude: Double?,
        contactIds: List<String>,
        callback: (Boolean, String?) -> Unit
    ) {
        if (!isConnected) {
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, "Not connected to server")
            }
            return
        }

        try {
            val data = JSONObject().apply {
                put("fromUserId", fromUserId)
                put("fromUserName", fromUserName)
                put("message", message)
                put("latitude", latitude)
                put("longitude", longitude)
                put("contactIds", JSONArray(contactIds))
                put("timestamp", System.currentTimeMillis())
                put("type", "emergency")
            }

            socket?.emit(EVENT_EMERGENCY_ALERT, data, io.socket.client.Ack { args ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (args.isNotEmpty()) {
                        val response = args[0] as? JSONObject
                        val success = response?.optBoolean("success", false) ?: false
                        val error = response?.optString("error")
                        callback(success, error)
                    } else {
                        callback(false, "No response from server")
                    }
                }
            })

            Log.d("SocketIOManager", "Emergency alert sent to ${contactIds.size} contacts")

        } catch (e: Exception) {
            Log.e("SocketIOManager", "Error sending emergency alert: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, e.message)
            }
        }
    }

    /**
     * Send safe/resolved alert to contacts
     */
    fun sendSafeAlert(
        fromUserId: String,
        fromUserName: String,
        message: String,
        contactIds: List<String>,
        callback: (Boolean, String?) -> Unit
    ) {
        if (!isConnected) {
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, "Not connected to server")
            }
            return
        }

        try {
            val data = JSONObject().apply {
                put("fromUserId", fromUserId)
                put("fromUserName", fromUserName)
                put("message", message)
                put("contactIds", JSONArray(contactIds))
                put("timestamp", System.currentTimeMillis())
                put("type", "safe")
            }

            socket?.emit(EVENT_SAFE_ALERT, data, io.socket.client.Ack { args ->
                CoroutineScope(Dispatchers.Main).launch {
                    if (args.isNotEmpty()) {
                        val response = args[0] as? JSONObject
                        val success = response?.optBoolean("success", false) ?: false
                        val error = response?.optString("error")
                        callback(success, error)
                    } else {
                        callback(false, "No response from server")
                    }
                }
            })

            Log.d("SocketIOManager", "Safe alert sent to ${contactIds.size} contacts")

        } catch (e: Exception) {
            Log.e("SocketIOManager", "Error sending safe alert: ${e.message}")
            CoroutineScope(Dispatchers.Main).launch {
                callback(false, e.message)
            }
        }
    }

    /**
     * Set user online status
     */
    fun setUserOnline(userId: String, userName: String) {
        if (isConnected) {
            val data = JSONObject().apply {
                put("userId", userId)
                put("userName", userName)
            }
            socket?.emit(EVENT_USER_ONLINE, data)
            Log.d("SocketIOManager", "User $userName is now online")
        }
    }

    /**
     * Set user offline status
     */
    fun setUserOffline(userId: String) {
        if (isConnected) {
            val data = JSONObject().apply {
                put("userId", userId)
            }
            socket?.emit(EVENT_USER_OFFLINE, data)
            Log.d("SocketIOManager", "User $userId is now offline")
        }
    }
}