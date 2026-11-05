package com.alarmapp.monitor

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.alarmapp.core.sleep.BreathingAnalyzer
import com.alarmapp.core.sleep.EPOCH_MS
import com.alarmapp.core.sleep.Epoch
import com.alarmapp.core.sleep.SignalMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.math.sqrt

/**
 * Fuente de datos del celular (RF2): acelerómetro + micrófono.
 *
 * Privacidad: el audio se lee en memoria, se reduce a números (nivel de ruido y
 * envolvente para la respiración) y se descarta de inmediato. Nunca se escribe en disco.
 *
 * Batería (RNF1): el acelerómetro va a 10 Hz con eventos agrupados por el hardware,
 * y el micrófono escucha 20 s de cada intervalo de 30 s.
 */
class PhoneSensorSource(private val context: Context) : SleepDataSource {

    override val id: String = "phone"

    private val sensorManager = context.getSystemService(SensorManager::class.java)

    override fun epochs(): Flow<Epoch> = channelFlow {
        val buffer = MagnitudeBuffer()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                buffer.add(sqrt(x * x + y * y + z * z))
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accel != null) {
            sensorManager.registerListener(listener, accel, ACCEL_PERIOD_US, ACCEL_BATCH_US)
        }
        val audio = if (hasMicPermission()) MicSampler() else null

        try {
            val start = System.currentTimeMillis()
            var index = 0L
            while (isActive) {
                val epochEnd = start + (index + 1) * EPOCH_MS
                val sound = audio?.listen(LISTEN_MS)
                val wait = epochEnd - System.currentTimeMillis()
                if (wait > 0) delay(wait)
                if (accel != null) sensorManager.flush(listener)
                send(
                    Epoch(
                        timestamp = start + index * EPOCH_MS,
                        movement = buffer.drainMovement(),
                        noiseDb = sound?.noiseDb ?: 0f,
                        breathingRate = sound?.breathing?.ratePerMinute,
                        breathingRegularity = sound?.breathing?.regularity,
                    )
                )
                index++
            }
        } finally {
            sensorManager.unregisterListener(listener)
            audio?.release()
        }
    }.flowOn(Dispatchers.IO)

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private class MagnitudeBuffer {
        private var data = FloatArray(512)
        private var size = 0

        @Synchronized fun add(v: Float) {
            if (size == data.size) data = data.copyOf(size * 2)
            data[size++] = v
        }

        @Synchronized fun drainMovement(): Float {
            val m = SignalMath.movementIndex(data, size)
            size = 0
            return m
        }
    }

    private data class SoundStats(val noiseDb: Float, val breathing: BreathingAnalyzer.Result)

    /** Escucha unos segundos, calcula indicadores y descarta el audio. */
    @SuppressLint("MissingPermission")
    private class MicSampler {
        private val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        private val record: AudioRecord? = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, FRAME_SAMPLES * 4),
            ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.getOrNull()

        fun listen(durationMs: Long): SoundStats? {
            val rec = record ?: return null
            val frames = (durationMs / FRAME_MS).toInt()
            val envelope = FloatArray(frames)
            val frame = ShortArray(FRAME_SAMPLES)
            var rmsSum = 0.0
            var got = 0
            runCatching { rec.startRecording() }.onFailure { return null }
            try {
                for (f in 0 until frames) {
                    var read = 0
                    while (read < FRAME_SAMPLES) {
                        val n = rec.read(frame, read, FRAME_SAMPLES - read)
                        if (n <= 0) break
                        read += n
                    }
                    if (read == 0) break
                    val rms = SignalMath.rms(frame, 0, read)
                    envelope[f] = rms.toFloat()
                    rmsSum += rms
                    got++
                }
            } finally {
                runCatching { rec.stop() }
                frame.fill(0) // el audio no sobrevive a este método
            }
            if (got == 0) return null
            return SoundStats(
                noiseDb = SignalMath.rmsToDb(rmsSum / got),
                breathing = BreathingAnalyzer.analyze(envelope.copyOf(got), 1000f / FRAME_MS),
            )
        }

        fun release() { record?.release() }
    }

    companion object {
        private const val ACCEL_PERIOD_US = 100_000      // 10 Hz
        private const val ACCEL_BATCH_US = 10_000_000    // agrupar hasta 10 s
        private const val LISTEN_MS = 20_000L
        private const val SAMPLE_RATE = 8_000
        private const val FRAME_MS = 100L
        private const val FRAME_SAMPLES = (SAMPLE_RATE * FRAME_MS / 1000).toInt()
    }
}
