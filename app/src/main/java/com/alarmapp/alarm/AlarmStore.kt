package com.alarmapp.alarm

import android.content.Context
import android.content.SharedPreferences

/**
 * Estado mínimo de la alarma en almacenamiento protegido por dispositivo: se puede leer
 * antes de que el usuario desbloquee el celular tras un reinicio (Direct Boot).
 * Solo guarda horas e identificadores, ningún dato de sueño.
 */
object AlarmStore {

    enum class Kind { DEADLINE, SNOOZE, GUARANTEED }

    data class Fire(val sessionId: Long, val fireAt: Long, val soundAt: Long, val kind: Kind)
    data class WakeCheck(val sessionId: Long, val at: Long)

    private const val FILE = "alarm_state"

    private fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun saveFire(context: Context, sessionId: Long, fireAt: Long, soundAt: Long, kind: Kind) {
        prefs(context).edit()
            .putLong("fire_session", sessionId)
            .putLong("fire_at", fireAt)
            .putLong("fire_sound_at", soundAt)
            .putString("fire_kind", kind.name)
            .commit()
    }

    fun fire(context: Context): Fire? {
        val p = prefs(context)
        if (!p.contains("fire_at")) return null
        return Fire(
            sessionId = p.getLong("fire_session", -1),
            fireAt = p.getLong("fire_at", 0),
            soundAt = p.getLong("fire_sound_at", 0),
            kind = runCatching { Kind.valueOf(p.getString("fire_kind", null)!!) }.getOrDefault(Kind.DEADLINE),
        )
    }

    fun clearFire(context: Context) {
        prefs(context).edit().remove("fire_session").remove("fire_at").remove("fire_sound_at").remove("fire_kind").commit()
    }

    fun saveWakeCheck(context: Context, sessionId: Long, at: Long) {
        prefs(context).edit().putLong("check_session", sessionId).putLong("check_at", at).commit()
    }

    fun wakeCheck(context: Context): WakeCheck? {
        val p = prefs(context)
        if (!p.contains("check_at")) return null
        return WakeCheck(p.getLong("check_session", -1), p.getLong("check_at", 0))
    }

    fun clearWakeCheck(context: Context) {
        prefs(context).edit().remove("check_session").remove("check_at").commit()
    }

    // Sesión activa y su sonido (para que la alarma suene aunque la base cifrada no esté disponible).
    fun setActiveSession(context: Context, sessionId: Long?, soundUri: String?) {
        prefs(context).edit().apply {
            if (sessionId == null) remove("active_session") else putLong("active_session", sessionId)
            if (soundUri == null) remove("sound_uri") else putString("sound_uri", soundUri)
        }.commit()
    }

    fun activeSession(context: Context): Long? =
        prefs(context).getLong("active_session", -1).takeIf { it >= 0 }

    fun soundUri(context: Context): String? = prefs(context).getString("sound_uri", null)

    fun setSoundUri(context: Context, uri: String?) {
        prefs(context).edit().apply { if (uri == null) remove("sound_uri") else putString("sound_uri", uri) }.commit()
    }

    /** Hora diaria guardada (minutos desde medianoche) para la alarma de respaldo. */
    fun setDailyMinute(context: Context, minute: Int?) {
        prefs(context).edit().apply { if (minute == null) remove("daily_minute") else putInt("daily_minute", minute) }.commit()
    }

    fun dailyMinute(context: Context): Int? =
        prefs(context).getInt("daily_minute", -1).takeIf { it >= 0 }

    fun snoozeCount(context: Context): Int = prefs(context).getInt("snoozes", 0)

    fun setSnoozeCount(context: Context, n: Int) {
        prefs(context).edit().putInt("snoozes", n).commit()
    }
}
