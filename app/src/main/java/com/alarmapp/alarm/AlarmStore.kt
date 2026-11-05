package com.alarmapp.alarm

import android.content.Context
import android.content.SharedPreferences
import com.alarmapp.core.alarm.AlarmTimes

/**
 * Estado mínimo de la alarma en almacenamiento protegido por dispositivo: se puede leer
 * antes de que el usuario desbloquee el celular tras un reinicio (Direct Boot).
 * Solo guarda horas e identificadores, ningún dato de sueño.
 */
object AlarmStore {

    /** TEST = prueba desde Ajustes; se comporta como una alarma normal pero no reemplaza a las guardadas. */
    enum class Kind { DEADLINE, SNOOZE, GUARANTEED, TEST }

    /**
     * @param soundAt cuándo empieza el sonido (antes sube la luz).
     * @param deadline hora límite original de esa alarma; se usa para saber cuál es la siguiente.
     */
    data class Fire(val sessionId: Long, val fireAt: Long, val soundAt: Long, val kind: Kind, val deadline: Long)
    data class WakeCheck(val sessionId: Long, val at: Long)

    private const val FILE = "alarm_state"

    private fun prefs(context: Context): SharedPreferences =
        context.createDeviceProtectedStorageContext().getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- alarma pendiente

    fun saveFire(context: Context, f: Fire) {
        prefs(context).edit()
            .putLong("fire_session", f.sessionId)
            .putLong("fire_at", f.fireAt)
            .putLong("fire_sound_at", f.soundAt)
            .putLong("fire_deadline", f.deadline)
            .putString("fire_kind", f.kind.name)
            .commit()
    }

    fun fire(context: Context): Fire? {
        val p = prefs(context)
        if (!p.contains("fire_at")) return null
        val soundAt = p.getLong("fire_sound_at", 0)
        return Fire(
            sessionId = p.getLong("fire_session", -1),
            fireAt = p.getLong("fire_at", 0),
            soundAt = soundAt,
            kind = runCatching { Kind.valueOf(p.getString("fire_kind", null)!!) }.getOrDefault(Kind.DEADLINE),
            deadline = p.getLong("fire_deadline", soundAt),
        )
    }

    fun clearFire(context: Context) {
        prefs(context).edit()
            .remove("fire_session").remove("fire_at").remove("fire_sound_at")
            .remove("fire_deadline").remove("fire_kind")
            .commit()
    }

    /** Hora límite de la última alarma que sonó; la siguiente se busca después de esta. */
    fun setLastDeadline(context: Context, at: Long) {
        prefs(context).edit().putLong("last_deadline", at).commit()
    }

    fun lastDeadline(context: Context): Long = prefs(context).getLong("last_deadline", 0)

    // ---------------------------------------------------------------- verificación

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

    // ---------------------------------------------------------------- sesión y sonido

    fun setActiveSession(context: Context, sessionId: Long?) {
        prefs(context).edit().apply {
            if (sessionId == null) remove("active_session") else putLong("active_session", sessionId)
        }.commit()
    }

    fun activeSession(context: Context): Long? =
        prefs(context).getLong("active_session", -1).takeIf { it >= 0 }

    /** Único lugar donde vive el tono elegido. */
    fun soundUri(context: Context): String? = prefs(context).getString("sound_uri", null)

    fun setSoundUri(context: Context, uri: String?) {
        prefs(context).edit().apply { if (uri == null) remove("sound_uri") else putString("sound_uri", uri) }.commit()
    }

    // ---------------------------------------------------------------- alarmas guardadas

    /** Copia de las alarmas activas (hora y días), disponible incluso tras un reinicio. */
    fun setAlarms(context: Context, alarms: List<AlarmTimes.Spec>) {
        prefs(context).edit().putString("alarms", AlarmTimes.encode(alarms)).remove("daily_minute").commit()
    }

    fun alarms(context: Context): List<AlarmTimes.Spec> {
        val p = prefs(context)
        // Compatibilidad con la versión anterior, que guardaba una sola hora diaria.
        if (!p.contains("alarms") && p.contains("daily_minute")) {
            return listOf(AlarmTimes.Spec(p.getInt("daily_minute", 0), AlarmTimes.ALL_DAYS))
        }
        return AlarmTimes.decode(p.getString("alarms", null))
    }

    fun snoozeCount(context: Context): Int = prefs(context).getInt("snoozes", 0)

    fun setSnoozeCount(context: Context, n: Int) {
        prefs(context).edit().putInt("snoozes", n).commit()
    }
}
