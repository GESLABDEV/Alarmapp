package com.alarmapp.monitor

import com.alarmapp.core.sleep.Epoch
import kotlinx.coroutines.flow.Flow

/**
 * Capa de datos desacoplada (documento, sección 2): cualquier fuente que entregue
 * intervalos de 30 s sirve. Hoy: el celular. Mañana: un wearable vía Health Connect,
 * sin tocar la lógica de sueño ni la interfaz.
 */
interface SleepDataSource {
    val id: String
    /** Emite un [Epoch] cada 30 segundos mientras se recolecte. */
    fun epochs(): Flow<Epoch>
}
