package com.example.compow.utils

object Constants {

    // SharedPreferences Keys
    const val PREFS_NAME = "compow_prefs"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_USER_PHONE = "user_phone"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_FIRST_TIME = "first_time"

    // Contact Settings Keys
    const val KEY_CIRCLE_ENABLED = "circle_enabled"
    const val KEY_GROUP_ENABLED = "group_enabled"
    const val KEY_COMMUNITY_ENABLED = "community_enabled"
    const val KEY_DEFAULT_MESSAGE = "default_message"
    const val KEY_PROFILE_IN_NOTIFICATION = "profile_in_notification"
    const val KEY_PROFILE_PICTURE_URI = "profile_picture_uri"

    // Alarm Settings
    const val KEY_ALARM_ACTIVE = "alarm_active"
    const val KEY_LAST_ALARM_TIME = "last_alarm_time"
    const val KEY_ALARM_COUNT = "alarm_count"

    // Location Settings
    const val KEY_SHARE_LOCATION = "share_location"
    const val KEY_LAST_LATITUDE = "last_latitude"
    const val KEY_LAST_LONGITUDE = "last_longitude"

    // Default Values
    const val DEFAULT_MESSAGE = "EMERGENCY! I need help immediately!"
    const val SAFE_MESSAGE = "I am safe now. Thank you for your concern."
    const val DEFAULT_PHONE_PREFIX = "+254"

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "compow_emergency_channel"
    const val NOTIFICATION_CHANNEL_NAME = "Emergency Alerts"
    const val NOTIFICATION_ID_ALARM = 1001
    const val NOTIFICATION_ID_GENERAL = 1002

    // Request Codes
    const val REQUEST_CODE_PERMISSIONS = 100
    const val REQUEST_CODE_LOCATION = 101
    const val REQUEST_CODE_SMS = 102
    const val REQUEST_CODE_CONTACTS = 103
    const val REQUEST_CODE_CALL = 104

    // Alarm Configuration
    const val VOLUME_BUTTON_LONG_PRESS_DURATION = 1000L // 1 second
    const val VIBRATION_DURATION = 1000L // 1 second
    const val ALARM_RETRY_DELAY = 3000L // 3 seconds

    // SMS Configuration
    const val SMS_MAX_LENGTH = 160
    const val SMS_RETRY_COUNT = 3

    // Location Configuration
    const val LOCATION_UPDATE_INTERVAL = 10000L // 10 seconds
    const val LOCATION_FASTEST_INTERVAL = 5000L // 5 seconds
    const val LOCATION_REQUEST_TIMEOUT = 30000L // 30 seconds
    const val LOCATION_ACCURACY_THRESHOLD = 100f // 100 meters

    // Firebase Collections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_CONTACTS = "contacts"
    const val COLLECTION_ALERTS = "alerts"
    const val COLLECTION_NOTIFICATIONS = "notifications"

    // User Profile
    const val MIN_PASSWORD_LENGTH = 6
    const val MAX_NAME_LENGTH = 50
    const val MAX_MESSAGE_LENGTH = 300

    // Course Options (Meru University)
    val COURSES = listOf(
        "BCSF - Bachelor of Computer Science",
        "BIT - Bachelor of Information Technology",
        "BCOM - Bachelor of Commerce",
        "BA - Bachelor of Arts",
        "BSC - Bachelor of Science",
        "BEng - Bachelor of Engineering",
        "BBA - Bachelor of Business Administration",
        "BSN - Bachelor of Science in Nursing",
        "BED - Bachelor of Education",
        "BARCH - Bachelor of Architecture"
    )

    // Year of Study Options
    val YEARS_OF_STUDY = listOf("1", "2", "3", "4")

    // Contact Categories
    const val CATEGORY_CIRCLE = "CIRCLE"
    const val CATEGORY_GROUP = "GROUP"
    const val CATEGORY_COMMUNITY = "COMMUNITY"

    // Alert Types
    const val ALERT_TYPE_EMERGENCY = "EMERGENCY"
    const val ALERT_TYPE_SAFE = "SAFE"
    const val ALERT_TYPE_TEST = "TEST"

    // Network
    const val NETWORK_TIMEOUT = 30000L // 30 seconds
    const val RETRY_ATTEMPTS = 3

    // Date Formats
    const val DATE_FORMAT_FULL = "yyyy-MM-dd HH:mm:ss"
    const val DATE_FORMAT_SHORT = "dd/MM/yyyy"
    const val TIME_FORMAT = "HH:mm"

    // Error Messages
    const val ERROR_EMPTY_FIELD = "This field cannot be empty"
    const val ERROR_INVALID_PHONE = "Please enter a valid Kenyan phone number (+254)"
    const val ERROR_INVALID_EMAIL = "Please enter a valid email address"
    const val ERROR_PASSWORD_SHORT = "Password must be at least 6 characters"
    const val ERROR_LOCATION_UNAVAILABLE = "Location unavailable"
    const val ERROR_PERMISSION_DENIED = "Permission denied"
    const val ERROR_NETWORK = "Network error. Please check your connection"

    // Success Messages
    const val SUCCESS_MESSAGE_SAVED = "Message saved successfully"
    const val SUCCESS_CONTACT_ADDED = "Contact added successfully"
    const val SUCCESS_CONTACT_REMOVED = "Contact removed successfully"
    const val SUCCESS_SIGNUP = "Account created successfully"
    const val SUCCESS_LOGIN = "Login successful"
    const val SUCCESS_ALARM_TRIGGERED = "Emergency alert sent"
    const val SUCCESS_ALARM_STOPPED = "Alarm stopped"

    // Google Maps
    const val GOOGLE_MAPS_ZOOM_DEFAULT = 15f
    const val GOOGLE_MAPS_ZOOM_CLOSE = 18f
    const val GOOGLE_MAPS_MARKER_TITLE = "Your Location"

    // Meru, Kenya Coordinates
    const val MERU_LATITUDE = -0.0469
    const val MERU_LONGITUDE = 37.6494

    // Intent Actions
    const val ACTION_TRIGGER_ALARM = "com.example.compow.TRIGGER_ALARM"
    const val ACTION_STOP_ALARM = "com.example.compow.STOP_ALARM"
    const val ACTION_NOTIFICATION_CLICK = "com.example.compow.NOTIFICATION_CLICK"

    // Extras
    const val EXTRA_CONTACT_ID = "contact_id"
    const val EXTRA_CATEGORY = "category"
    const val EXTRA_ALERT_ID = "alert_id"
    const val EXTRA_MESSAGE = "message"

    // Worker Tags
    const val WORKER_TAG_CLEANUP = "cleanup_worker"
    const val WORKER_TAG_SYNC = "sync_worker"

    // Cache Duration
    const val CACHE_DURATION_CONTACTS = 3600000L // 1 hour
    const val CACHE_DURATION_USER = 86400000L // 24 hours

    // Validation Patterns
    const val PATTERN_EMAIL = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
    const val PATTERN_PHONE_KENYA = "^\\+254[0-9]{9}$"

    // Database
    const val DATABASE_NAME = "compow_database"
    const val DATABASE_VERSION = 1

    // Testing
    const val TEST_MODE = false
    const val DEBUG_MODE = true
}