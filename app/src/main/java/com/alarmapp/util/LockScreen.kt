package com.alarmapp.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.view.WindowManager

/**
 * Hace que la alarma aparezca aunque la pantalla esté apagada o bloqueada:
 *  1. Enciende la pantalla unos segundos.
 *  2. Abre la pantalla de la alarma directamente (además del aviso de pantalla completa).
 *  3. La pantalla de la alarma se dibuja encima del bloqueo.
 */
object LockScreen {

    /** Llamar en onCreate de cada pantalla de alarma, antes de setContent. */
    fun showOverLock(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            activity.window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** Enciende la pantalla si está apagada. */
    @Suppress("DEPRECATION")
    fun wakeScreen(context: Context) {
        val pm = context.getSystemService(PowerManager::class.java)
        if (pm.isInteractive) return
        pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "alarmapp:screen",
        ).acquire(10_000)
    }

    /**
     * Abre una pantalla desde segundo plano. Android solo lo permite si la app tiene
     * "Mostrar sobre otras apps"; si no, queda el aviso de pantalla completa como respaldo.
     */
    fun launch(context: Context, cls: Class<out Activity>) {
        runCatching {
            context.startActivity(
                Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            )
        }
    }
}
