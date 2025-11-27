package com.example.compow.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromContactCategory(category: ContactCategory): String {
        return category.name
    }

    @TypeConverter
    fun toContactCategory(categoryString: String): ContactCategory {
        return try {
            ContactCategory.valueOf(categoryString)
        } catch (e: IllegalArgumentException) {
            ContactCategory.CIRCLE // Default fallback
        }
    }

    @TypeConverter
    fun fromAlertType(alertType: AlertType): String {
        return alertType.name
    }

    @TypeConverter
    fun toAlertType(alertTypeString: String): AlertType {
        return try {
            AlertType.valueOf(alertTypeString)
        } catch (e: IllegalArgumentException) {
            AlertType.EMERGENCY // Default fallback
        }
    }
}