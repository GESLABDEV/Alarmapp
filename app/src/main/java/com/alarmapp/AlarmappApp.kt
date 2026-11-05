package com.alarmapp

import android.app.Application
import android.content.Context
import com.alarmapp.data.AppDatabase
import com.alarmapp.data.SleepRepository
import com.alarmapp.util.Notifications

/** Contenedor manual de dependencias: simple y suficiente para el MVP. */
class AlarmappApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: SleepRepository by lazy { SleepRepository(database) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}

val Context.app: AlarmappApp get() = applicationContext as AlarmappApp
