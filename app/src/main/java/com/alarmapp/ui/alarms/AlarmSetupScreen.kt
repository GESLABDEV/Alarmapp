package com.alarmapp.ui.alarms

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.alarmapp.alarm.AlarmSync
import com.alarmapp.core.alarm.AlarmTimes
import com.alarmapp.data.AlarmEntity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmapp.app
import com.alarmapp.core.sleep.CycleCalculator
import com.alarmapp.core.sleep.SleepProfile
import com.alarmapp.data.AlarmMode
import com.alarmapp.ui.components.Card
import com.alarmapp.ui.components.GlowButton
import com.alarmapp.ui.components.GlowTab
import com.alarmapp.ui.components.Overline
import com.alarmapp.ui.components.formatTime
import com.alarmapp.ui.theme.Palette
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Crear ([alarmId] null) o editar una alarma. Modo 1 (hora fija) y modo 3 (inverso) de RF4. */
@Composable
fun AlarmSetupScreen(alarmId: Long?, onBack: () -> Unit) {
    val repo = LocalContext.current.app.repository
    var loaded by remember { mutableStateOf(alarmId == null) }
    var existing by remember { mutableStateOf<AlarmEntity?>(null) }
    LaunchedEffect(alarmId) {
        if (alarmId != null) existing = repo.alarm(alarmId)
        loaded = true
    }
    if (loaded) AlarmEditor(existing, onBack)
    else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(alarm: AlarmEntity?, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    val profile by remember { repo.observeProfile().map { p -> p?.let { SleepProfile(it.avgCycleMinutes, it.avgLatencyMinutes, it.windowMinutes) } ?: SleepProfile() } }
        .collectAsState(initial = SleepProfile())

    var mode by remember { mutableStateOf(alarm?.mode?.takeIf { it != AlarmMode.RECOMMENDED } ?: AlarmMode.FIXED) }
    var days by remember { mutableIntStateOf(alarm?.days ?: AlarmTimes.ALL_DAYS) }
    val initial = alarm?.minuteOfDay ?: (6 * 60)
    val picker = rememberTimePickerState(initial / 60, initial % 60, DateFormat.is24HourFormat(context))
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

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
            Overline(if (alarm == null) "Nueva alarma" else "Editar alarma")
        }
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHigh).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GlowTab("Hora fija", mode == AlarmMode.FIXED, Modifier.weight(1f)) { mode = AlarmMode.FIXED }
            GlowTab("Modo inverso", mode == AlarmMode.INVERSE, Modifier.weight(1f)) { mode = AlarmMode.INVERSE }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (mode == AlarmMode.FIXED) "Elige tu hora límite. La app te despertará antes, dentro de la ventana, en tu fase más ligera."
            else "Dinos a qué hora necesitas despertar y te decimos a qué hora acostarte.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state = picker,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Palette.SurfaceHigh,
                    clockDialSelectedContentColor = Palette.Surface,
                    clockDialUnselectedContentColor = Palette.TextPrimary,
                    selectorColor = Palette.Primary,
                    timeSelectorSelectedContainerColor = Palette.PrimaryText.copy(alpha = 0.18f),
                    timeSelectorSelectedContentColor = Palette.PrimaryText,
                    timeSelectorUnselectedContainerColor = Palette.SurfaceHigh,
                    timeSelectorUnselectedContentColor = Palette.TextPrimary,
                    periodSelectorSelectedContainerColor = Palette.PrimaryText.copy(alpha = 0.18f),
                    periodSelectorSelectedContentColor = Palette.PrimaryText,
                    periodSelectorUnselectedContentColor = Palette.TextSecondary,
                    periodSelectorBorderColor = Palette.Outline,
                ),
            )
        }

        // Días de la semana
        Overline("Repetir")
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AlarmTimes.DAY_LETTERS.forEachIndexed { i, letter ->
                val on = AlarmTimes.has(days, i)
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(CircleShape)
                        .background(if (on) Palette.PrimaryText.copy(alpha = 0.18f) else Color.Transparent)
                        .border(1.5.dp, if (on) Palette.PrimaryText else Palette.Outline, CircleShape)
                        .clickable {
                            val next = AlarmTimes.toggle(days, i)
                            if (next != 0) days = next // al menos un día
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(letter, style = MaterialTheme.typography.labelLarge, color = if (on) Palette.PrimaryText else Palette.TextSecondary)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(AlarmTimes.label(days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        if (mode == AlarmMode.INVERSE) {
            val wake = AlarmTimes.next(picker.hour * 60 + picker.minute, days, System.currentTimeMillis())
            val beds = CycleCalculator.recommendedBedtimes(wake, profile)
            Card {
                Overline("Acuéstate a las")
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    beds.forEachIndexed { i, t ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(formatTime(t), style = MaterialTheme.typography.titleLarge, color = if (i == 0) Palette.PrimaryText else MaterialTheme.colorScheme.onSurface)
                            Text("${6 - i} ciclos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        GlowButton(
            text = if (saving) "Guardando…" else "Guardar alarma",
            enabled = !saving,
            onClick = {
                saving = true
                scope.launch {
                    repo.saveAlarm(alarm?.id, mode, picker.hour * 60 + picker.minute, days)
                    AlarmSync.apply(context, repo.alarms())
                    Toast.makeText(context, "Alarma guardada", Toast.LENGTH_SHORT).show()
                    onBack() // vuelve sola a la lista
                }
            },
        )
        if (alarm != null) {
            Spacer(Modifier.height(12.dp))
            GlowButton(text = "Eliminar alarma", onClick = { confirmDelete = true }, color = Palette.Danger, fillAlpha = 0.08f)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Suena en los días elegidos aunque no presiones \"Me voy a dormir\". Si lo presionas, además mide tu noche y te despierta en fase ligera.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (confirmDelete && alarm != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Eliminar esta alarma?", color = Palette.TextPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        repo.deleteAlarm(alarm.id)
                        AlarmSync.apply(context, repo.alarms())
                        onBack()
                    }
                }) { Text("Eliminar", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar", color = Palette.PrimaryText) } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}
