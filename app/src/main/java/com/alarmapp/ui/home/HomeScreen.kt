package com.alarmapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alarmapp.core.alarm.AlarmTimes
import com.alarmapp.core.sleep.CycleCalculator
import com.alarmapp.core.sleep.SmartWake
import com.alarmapp.data.AlarmMode
import com.alarmapp.ui.components.Card
import com.alarmapp.ui.components.GlowButton
import com.alarmapp.ui.components.GlowTab
import com.alarmapp.ui.components.Overline
import com.alarmapp.ui.components.formatDuration
import com.alarmapp.ui.components.formatTime
import com.alarmapp.ui.theme.Palette
import com.alarmapp.ui.theme.stageLabel
import com.alarmapp.util.Permissions
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(
    onOpenAlarms: () -> Unit,
    onNewAlarm: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInsights: () -> Unit,
    onOpenReport: (Long) -> Unit,
    onNightStarted: (Long) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Overline(greeting())
                Text("Alarmapp", style = MaterialTheme.typography.headlineMedium)
            }
            IconButton(onClick = onOpenInsights) {
                Icon(Icons.Outlined.BarChart, contentDescription = "Reportes", tint = Palette.PrimaryText)
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(28.dp))

        // Próxima alarma
        Card(Modifier.clickable(onClick = onOpenAlarms)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Alarm, null, tint = Palette.PrimaryText)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val next = vm.nextAlarm()
                    if (next == null) {
                        Text(if (state.totalAlarms == 0) "Sin alarmas" else "Alarmas apagadas", style = MaterialTheme.typography.titleMedium)
                        Text("Toca para crear o activar una", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val (alarm, at) = next
                        Overline("Próxima alarma")
                        Text(formatTime(at), style = MaterialTheme.typography.displayMedium)
                        Text(
                            "${AlarmTimes.label(alarm.days)} · ${state.alarms.size} activa${if (state.alarms.size == 1) "" else "s"} de ${state.totalAlarms}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(40.dp))

        // RF1: botón principal
        SleepButton(onClick = { sheetOpen = true })

        Spacer(Modifier.height(40.dp))

        // Última noche (cian: color de la mañana)
        state.lastNight?.let { s ->
            Card(Modifier.clickable { onOpenReport(s.id) }, accent = Palette.Accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WbTwilight, null, tint = Palette.Accent)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Overline("Última noche", Palette.Accent)
                        Text(formatDuration(s.totalSleepMinutes), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Latencia ${formatDuration(s.latencyMinutes)} · despertaste en ${stageLabel(s.wakeStage).lowercase()}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (state.lastNight != null) {
            TextButton(onClick = onOpenInsights, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Ver todos mis reportes", color = Palette.PrimaryText, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(20.dp))
        NewAlarmButton(onNewAlarm)
        Spacer(Modifier.height(20.dp))
    }

    if (sheetOpen) {
        BedtimeSheet(vm, state, onDismiss = { sheetOpen = false }, onOpenAlarms = { sheetOpen = false; onOpenAlarms() }, onOpenSettings = { sheetOpen = false; onOpenSettings() }, onStarted = { sheetOpen = false; onNightStarted(it) })
    }
}

/** Botón independiente, al final de la pantalla, para crear una alarma. */
@Composable
private fun NewAlarmButton(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(27.dp))
            .background(Palette.Background)
            .border(1.5.dp, Palette.Primary, RoundedCornerShape(27.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = Palette.PrimaryText, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Nueva alarma", style = MaterialTheme.typography.titleMedium, color = Palette.PrimaryText)
    }
}

@Composable
private fun SleepButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(Palette.PrimarySoft)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(168.dp)
                    .clip(CircleShape)
                    .background(Palette.Primary),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Bedtime, null, tint = Palette.Surface, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Me voy a\ndormir", style = MaterialTheme.typography.titleMedium, color = Palette.Surface, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedtimeSheet(
    vm: HomeViewModel,
    state: HomeState,
    onDismiss: () -> Unit,
    onOpenAlarms: () -> Unit,
    onOpenSettings: () -> Unit,
    onStarted: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = remember { System.currentTimeMillis() }
    // Alarmas activas ordenadas por cuál suena primero.
    val fixedOptions = remember(state.alarms) { state.alarms.map { it to vm.nextTime(it, now) }.sortedBy { it.second } }
    var chosenAlarm by remember(fixedOptions) { mutableStateOf(fixedOptions.firstOrNull()) }
    var mode by remember { mutableStateOf(if (fixedOptions.isNotEmpty()) AlarmMode.FIXED else AlarmMode.RECOMMENDED) }
    val options = remember { vm.recommendedWakeTimes() }
    var chosen by remember { mutableStateOf(options.getOrNull(1) ?: options.first()) }
    val (battery, charging) = remember { Permissions.battery(context) }
    val alarmReady = remember { Permissions.alarmReady(context) }
    val micOk = remember { Permissions.mic(context) }
    var starting by remember { mutableStateOf(false) }

    val deadline: Long? = when (mode) {
        AlarmMode.RECOMMENDED -> chosen
        else -> chosenAlarm?.second
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("¿Cómo quieres despertar?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHigh).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModeTab("Hora fija", mode != AlarmMode.RECOMMENDED, Modifier.weight(1f)) { mode = AlarmMode.FIXED }
                ModeTab("Recomendada", mode == AlarmMode.RECOMMENDED, Modifier.weight(1f)) { mode = AlarmMode.RECOMMENDED }
            }
            Spacer(Modifier.height(20.dp))

            if (mode == AlarmMode.RECOMMENDED) {
                Text(
                    "Final de un ciclo, contando ~${state.profile.avgLatencyMinutes.toInt()} min para dormirte y ciclos de ~${state.profile.avgCycleMinutes.toInt()} min.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                val now = remember { System.currentTimeMillis() }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    options.forEach { t ->
                        val cycles = Math.round(CycleCalculator.cyclesFor(now, t, state.profile))
                        TimeOption(formatTime(t), "$cycles ciclos", t == chosen, Modifier.weight(1f)) { chosen = t }
                    }
                }
            } else if (fixedOptions.isEmpty()) {
                Text("Aún no tienes alarmas activas.", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onOpenAlarms) { Text("Crear alarma", color = Palette.PrimaryText) }
            } else {
                Text("Elige la alarma de esta noche", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                fixedOptions.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        row.forEach { opt ->
                            TimeOption(formatTime(opt.second), dayLabel(opt.second, now), opt == chosenAlarm, Modifier.weight(1f)) { chosenAlarm = opt }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            deadline?.let { d ->
                Spacer(Modifier.height(16.dp))
                val from = SmartWake.windowStart(d, state.profile.windowMinutes)
                Text(
                    "Te despertará en fase ligera entre ${formatTime(from)} y ${formatTime(d)}. A las ${formatTime(d)} suena sí o sí.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (battery in 0..29 && !charging) {
                Spacer(Modifier.height(14.dp))
                Notice(Icons.Outlined.BatteryAlert, "Batería al $battery%. Conecta el celular para asegurar la alarma.", Palette.Danger)
            } else if (!charging) {
                Spacer(Modifier.height(14.dp))
                Text("Consejo: deja el celular conectado durante la noche.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!micOk) {
                Spacer(Modifier.height(8.dp))
                Text("Sin micrófono: se medirá solo el movimiento.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!alarmReady) {
                Spacer(Modifier.height(14.dp))
                Notice(Icons.Outlined.Alarm, "Faltan permisos para que la alarma suene de forma confiable.", Palette.Danger)
                TextButton(onClick = onOpenSettings) { Text("Revisar permisos", color = Palette.PrimaryText) }
            }

            Spacer(Modifier.height(24.dp))
            GlowButton(
                text = "Iniciar la noche",
                onClick = {
                    val d = deadline ?: return@GlowButton
                    starting = true
                    scope.launch {
                        val picked = chosenAlarm?.first
                        val id = if (mode == AlarmMode.RECOMMENDED) vm.startNight(context, AlarmMode.RECOMMENDED, d, null)
                        else vm.startNight(context, picked?.mode ?: AlarmMode.FIXED, d, picked?.id)
                        onStarted(id)
                    }
                },
                enabled = deadline != null && !starting,
            )
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) =
    GlowTab(label, selected, modifier, onClick)

@Composable
private fun TimeOption(time: String, caption: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, if (selected) Palette.PrimaryText else Palette.Outline, RoundedCornerShape(16.dp))
            .background(if (selected) Palette.PrimaryText.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(time, style = MaterialTheme.typography.titleMedium, color = if (selected) Palette.PrimaryText else MaterialTheme.colorScheme.onSurface)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Notice(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** "Hoy", "Mañana" o el día de la semana. */
private fun dayLabel(at: Long, now: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = at }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    val diff = ((c.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis -
        (n.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis) / 86_400_000L
    return when (diff) {
        0L -> "Hoy"
        1L -> "Mañana"
        else -> listOf("Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb")[c.get(Calendar.DAY_OF_WEEK) - 1]
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}
