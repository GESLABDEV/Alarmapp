package com.alarmapp.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alarmapp.BuildConfig
import com.alarmapp.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Envío opcional (sección 4): solo con consentimiento explícito, solo resúmenes de cada
 * noche y sin ningún identificador del usuario ni del dispositivo.
 * Destino: tabla `night_summaries` de Supabase (ver docs/supabase.sql).
 */
class SummaryUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val url = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (url.isBlank() || key.isBlank()) return Result.success()

        val repo = applicationContext.app.repository
        if (!repo.profileEntity().uploadConsent) return Result.success()

        for ((rating, s) in repo.pendingUploads()) {
            if (s.endTime == null) continue
            val body = JSONObject().apply {
                put("app_version", BuildConfig.VERSION_NAME)
                put("mode", s.mode.name)
                // Solo la hora del día en que se acostó (0–23), no la fecha.
                put("bed_hour", Calendar.getInstance().apply { timeInMillis = s.startTime }.get(Calendar.HOUR_OF_DAY))
                put("total_sleep_min", s.totalSleepMinutes ?: JSONObject.NULL)
                put("latency_min", s.latencyMinutes ?: JSONObject.NULL)
                put("light_min", s.lightMinutes ?: JSONObject.NULL)
                put("deep_min", s.deepMinutes ?: JSONObject.NULL)
                put("rem_min", s.remMinutes ?: JSONObject.NULL)
                put("awake_min", s.awakeMinutes ?: JSONObject.NULL)
                put("wake_stage", s.wakeStage?.name ?: JSONObject.NULL)
                put("window_min", s.windowMinutes)
                put("snoozes", s.snoozeCount)
                put("rating", rating.score)
            }
            val ok = withContext(Dispatchers.IO) { post("$url/rest/v1/night_summaries", key, body.toString()) }
            if (!ok) return Result.retry()
            repo.markUploaded(rating.id)
        }
        return Result.success()
    }

    private fun post(endpoint: String, key: String, json: String): Boolean = runCatching {
        val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", key)
            setRequestProperty("Authorization", "Bearer $key")
            setRequestProperty("Prefer", "return=minimal")
        }
        c.outputStream.use { it.write(json.toByteArray()) }
        val code = c.responseCode
        c.disconnect()
        code in 200..299
    }.getOrDefault(false)

    companion object {
        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<SummaryUploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("summary_upload", ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
