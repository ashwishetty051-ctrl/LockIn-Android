package com.lockin.app

import android.app.Application
import androidx.room.Room

class LockInApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, LockInDatabase::class.java, "lockin-local.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val settings by lazy { SettingsRepository(this) }
}
