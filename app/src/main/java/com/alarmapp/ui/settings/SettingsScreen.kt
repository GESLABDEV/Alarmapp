package com.alarmapp.ui.settings

import android.app.Activity
import android.widget.Toast
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmapp.BuildConfig
import com.alarmapp.alarm.AlarmScheduler
import com.alarmapp.alarm.AlarmStore
import com.alarmapp.app
import com.alarmapp.ui.components.Card
import com.alarmapp.ui.components.GlowButton
import com.alarmapp.ui.components.Metric
import com.alarmapp.ui.components.Overline
import com.alarmapp.ui.theme.Palette
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    val profile by repo.observeProfile().collectAsState(initial = null)
    var soundName by remember { mutableStateOf(soundTitle(context, AlarmStore.soundUri(context))) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        AlarmStore.setSoundUri(context, uri?.toString())
        soundName = soundTitle(context, uri?.toString())
    }

    // Canción propia: se pide permiso permanente para leerla aunque la app esté cerrada.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        AlarmStore.setSoundUri(context, uri.toString())
        soundName = soundTitle(context, uri.toString())
    }
    var preview by remember { mutableStateOf<android.media.Ringtone?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { preview?.stop() } }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Palette.TextPrimary) }
            Overline("Ajustes")
        }

        Spacer(Modifier.height(8.dp))
        var testScheduled by remember { mutableStateOf(false) }
        val nightActive = AlarmStore.activeSession(context) != null
        GlowButton(
            text = when {
                nightActive -> "Prueba no disponible durante la noche"
                testScheduled -> "Sonará en 1 minuto · bloquea el celular"
                else -> "Probar alarma en 1 minuto"
            },
            enabled = !nightActive && !testScheduled,
            onClick = {
                AlarmScheduler.scheduleTest(context)
                testScheduled = true
            },
        )
        Spacer(Modifier.height(16.dp))
        Card {
            Overline("Lo que la app aprendió de ti")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Metric("Ciclo", "${profile?.avgCycleMinutes?.toInt() ?: 90} min", Modifier.weight(1f))
                Metric("Latencia", "${profile?.avgLatencyMinutes?.toInt() ?: 15} min", Modifier.weight(1f))
                Metric("Ventana", "${profile?.windowMinutes ?: 30} min", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Se ajusta con cada noche y con tu respuesta de la mañana. La ventana se mantiene entre 10 y 45 minutos.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))
        Card {
            Overline("Sonido de todas las alarmas")
            Spacer(Modifier.height(6.dp))
            Text(soundName, style = MaterialTheme.typography.titleMedium)
            Text("Sube de volumen poco a poco y vibra sin parar.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlowButton(
                    text = "Tonos",
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    onClick = {
                        val existing = AlarmStore.soundUri(context)?.let(Uri::parse)
                        picker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Sonido de la alarma")
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                        )
                    },
                )
                GlowButton(
                    text = "Mis canciones",
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    onClick = { filePicker.launch(arrayOf("audio/*")) },
                )
                GlowButton(
                    text = if (preview != null) "Detener" else "Escuchar",
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    color = Palette.Accent,
                    onClick = {
                        val current = preview
                        if (current != null) {
                            current.stop(); preview = null
                        } else {
                            val uri = AlarmStore.soundUri(context)?.let(Uri::parse)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            preview = RingtoneManager.getRingtone(context, uri)?.also { r ->
                                // Por el canal de alarma, igual que la alarma real: así se oye
                                // aunque el timbre esté en silencio o con volumen bajo.
                                r.audioAttributes = android.media.AudioAttributes.Builder()
                                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                    .build()
                                val am = context.getSystemService(android.media.AudioManager::class.java)
                                if (am.getStreamVolume(android.media.AudioManager.STREAM_ALARM) == 0) {
                                    Toast.makeText(context, "El volumen de alarma está en cero", Toast.LENGTH_SHORT).show()
                                }
                                r.play()
                                scope.launch { kotlinx.coroutines.delay(6_000); r.stop(); if (preview === r) preview = null }
                            }
                        }
                    },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Card {
            Overline("Permisos")
            PermissionList()
        }

        Spacer(Modifier.height(16.dp))
        Card {
            Overline("Privacidad")
            Spacer(Modifier.height(10.dp))
            val serverReady = BuildConfig.SUPABASE_URL.isNotBlank()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Compartir resúmenes anónimos", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (serverReady) "Solo totales de cada noche, sin datos crudos ni identificadores. Ayuda a mejorar la app."
                        else "El servidor aún no está configurado en esta versión.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = profile?.uploadConsent == true,
                    enabled = serverReady,
                    onCheckedChange = { v ->
                        scope.launch {
                            repo.setUploadConsent(v)
                            if (v) com.alarmapp.sync.SummaryUploadWorker.enqueue(context)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Primary, checkedThumbColor = Palette.Surface, checkedBorderColor = Palette.Primary),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Tus datos se guardan cifrados en este celular. Tratamiento conforme a la Ley 1581 de 2012.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { confirmDelete = true }) {
                Text(if (deleted) "Datos borrados ✓" else "Borrar todos mis datos", color = Palette.Danger)
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar todos tus datos?") },
            text = { Text("Se eliminan tus noches, calificaciones, alarmas y lo aprendido. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    // Se borran también la alarma diaria y el sonido guardados fuera de la base.
                    AlarmStore.setAlarms(context, emptyList())
                    AlarmStore.setSoundUri(context, null)
                    if (AlarmStore.activeSession(context) == null) AlarmScheduler.cancelAll(context)
                    scope.launch { repo.deleteAllData(); deleted = true }
                }) { Text("Borrar", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

private fun soundTitle(context: android.content.Context, uri: String?): String {
    val u = uri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return "Predeterminado"
    if (u.scheme == "content" && u.authority?.contains("documents") == true) {
        runCatching {
            context.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
            }
        }
    }
    return runCatching { RingtoneManager.getRingtone(context, u)?.getTitle(context) }.getOrNull() ?: "Predeterminado"
}
