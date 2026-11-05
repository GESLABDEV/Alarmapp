package com.alarmapp.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.alarmapp.R
import com.alarmapp.alarm.AlarmActivity
import com.alarmapp.alarm.WakeCheckActivity
import com.alarmapp.ui.MainActivity

object Notifications {
    const val CHANNEL_MONITOR = "monitor"
    /** Canal nuevo con "ignorar No molestar"; el anterior ("alarm") se elimina. */
    const val CHANNEL_ALARM = "alarm_v2"
    private const val OLD_CHANNEL_ALARM = "alarm"
    const val ID_MONITOR = 10
    const val ID_ALARM = 20
    const val ID_WAKE_CHECK = 30
    const val ID_UPCOMING = 40
    const val CHANNEL_UPCOMING = "upcoming"

    private const val AMBER = 0xFF6E5BB8.toInt() // lavanda de la marca

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_monitor_desc)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_alarm_desc)
                // El sonido y la vibración los controla AlarmService para el despertar progresivo.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                lightColor = Color.rgb(255, 181, 71)
                setBypassDnd(true)
            }
        )
        nm.deleteNotificationChannel(OLD_CHANNEL_ALARM)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_UPCOMING, "Próxima alarma", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Aviso unas horas antes de que suene una alarma"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    /** Notificación fija durante la sesión de sueño (HU2). */
    fun monitor(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(AMBER)
            .setContentTitle("Midiendo tu noche")
            .setContentText("Solo se guardan indicadores, nunca audio.")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp(context))
            .build()

    fun alarm(context: Context, text: String): Notification {
        val full = PendingIntent.getActivity(
            context, 1, Intent(context, AlarmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(AMBER)
            .setContentTitle("Hora de despertar")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()
    }

    fun wakeCheck(context: Context): Notification {
        val full = PendingIntent.getActivity(
            context, 2, Intent(context, WakeCheckActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(AMBER)
            .setContentTitle("¿Sigues despierto?")
            .setContentText("Toca para confirmarlo o la alarma volverá a sonar.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .setAutoCancel(true)
            .build()
    }

    /** Aviso silencioso de la próxima alarma, con opción de omitirla esta vez. */
    fun postUpcoming(context: Context, soundAt: Long) {
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(soundAt))
        val n = NotificationCompat.Builder(context, CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setColor(AMBER)
            .setContentTitle("Próxima alarma: $time")
            .setContentText("Presiona \"Me voy a dormir\" para que también mida tu noche.")
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context))
            .addAction(0, "Omitir esta vez", com.alarmapp.alarm.AlarmScheduler.skipIntent(context))
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(ID_UPCOMING, n) }
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
