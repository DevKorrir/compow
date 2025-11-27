package com.example.compow

import android.app.Application
import com.example.compow.data.AppDatabase

class ComPowApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}