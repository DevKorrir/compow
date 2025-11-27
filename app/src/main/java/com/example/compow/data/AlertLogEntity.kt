package com.example.compow.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_logs")
data class AlertLogEntity(
    @PrimaryKey(autoGenerate = true)
    val logId: Long = 0,

    @ColumnInfo(name = "alert_type")
    val alertType: AlertType,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "latitude")
    val latitude: Double?,

    @ColumnInfo(name = "longitude")
    val longitude: Double?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "contacts_notified")
    val contactsNotified: Int = 0,

    @ColumnInfo(name = "is_resolved")
    val isResolved: Boolean = false,

    @ColumnInfo(name = "resolved_at")
    val resolvedAt: Long? = null
)

enum class AlertType {
    EMERGENCY,
    SAFE,
    TEST
}