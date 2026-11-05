package com.alarmapp.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.alarmapp.alarm.AlarmScheduler

/** Estado de cada permiso del documento (sección 6, "Permisos de Android"). */
object Permissions {

    fun mic(c: Context) = granted(c, Manifest.permission.RECORD_AUDIO)

    fun notifications(c: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(c, Manifest.permission.POST_NOTIFICATIONS)

    fun exactAlarms(c: Context) = AlarmScheduler.canScheduleExact(c)

    fun fullScreen(c: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun batteryUnrestricted(c: Context): Boolean =
        c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

    /** Lo indispensable para que la alarma suene (RNF2). */
    fun alarmReady(c: Context) = notifications(c) && exactAlarms(c)

    fun exactAlarmSettings(c: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${c.packageName}"))
        else null

    fun fullScreenSettings(c: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${c.packageName}"))
        else null

    /** Lista del sistema donde el usuario excluye la app de la optimización de batería. */
    fun batterySettings(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appSettings(c: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))

    /** Nivel de batería y si está cargando (HU7: aviso de batería baja). */
    fun battery(c: Context): Pair<Int, Boolean> {
        val bm = c.getSystemService(BatteryManager::class.java)
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to bm.isCharging
    }

    private fun granted(c: Context, p: String) =
        ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED
}
