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
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.alarmapp.core.sleep.CycleCalculator
import com.alarmapp.core.sleep.SmartWake
import com.alarmapp.data.AlarmMode
import com.alarmapp.ui.components.Card
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
    onOpenSettings: () -> Unit,
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
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Ajustes", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(28.dp))

        // Próxima alarma
        Card(Modifier.clickable(onClick = onOpenAlarms)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Alarm, null, tint = Palette.Amber)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    val alarm = state.alarm
                    if (alarm == null) {
                        Text("Sin hora fija", style = MaterialTheme.typography.titleMedium)
                        Text("Usa la hora recomendada o configura una", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val deadline = vm.nextFixedDeadline(alarm)
                        Text(formatTime(deadline), style = MaterialTheme.typography.displayMedium)
                        Text(
                            "${if (alarm.mode == AlarmMode.INVERSE) "Modo inverso" else "Hora fija"} · ventana ${state.profile.windowMinutes} min",
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
            Card(Modifier.clickable { onOpenReport(s.id) }, accent = Palette.Cyan) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WbTwilight, null, tint = Palette.Cyan)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Overline("Última noche", Palette.Cyan)
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
        Spacer(Modifier.height(24.dp))
    }

    if (sheetOpen) {
        BedtimeSheet(vm, state, onDismiss = { sheetOpen = false }, onOpenAlarms = { sheetOpen = false; onOpenAlarms() }, onOpenSettings = { sheetOpen = false; onOpenSettings() }, onStarted = { sheetOpen = false; onNightStarted(it) })
    }
}

@Composable
private fun SleepButton(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Palette.Amber.copy(alpha = 0.22f), Color.Transparent)))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(168.dp).clip(CircleShape).background(Palette.Amber),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Bedtime, null, tint = Palette.Night, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Me voy a\ndormir", style = MaterialTheme.typography.titleMedium, color = Palette.Night, textAlign = TextAlign.Center)
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
    val alarm = state.alarm
    var mode by remember { mutableStateOf(if (alarm != null) AlarmMode.FIXED else AlarmMode.RECOMMENDED) }
    val options = remember { vm.recommendedWakeTimes() }
    var chosen by remember { mutableStateOf(options.getOrNull(1) ?: options.first()) }
    val (battery, charging) = remember { Permissions.battery(context) }
    val alarmReady = remember { Permissions.alarmReady(context) }
    val micOk = remember { Permissions.mic(context) }
    var starting by remember { mutableStateOf(false) }

    val deadline: Long? = when (mode) {
        AlarmMode.RECOMMENDED -> chosen
        else -> alarm?.let { vm.nextFixedDeadline(it) }
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
            } else if (alarm == null) {
                Text("Aún no tienes una hora fija.", style = MaterialTheme.typography.bodyLarge)
                TextButton(onClick = onOpenAlarms) { Text("Configurar hora", color = Palette.Amber) }
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
                TextButton(onClick = onOpenSettings) { Text("Revisar permisos", color = Palette.Amber) }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val d = deadline ?: return@Button
                    starting = true
                    scope.launch {
                        val id = vm.startNight(context, if (mode == AlarmMode.RECOMMENDED) AlarmMode.RECOMMENDED else alarm!!.mode, d)
                        onStarted(id)
                    }
                },
                enabled = deadline != null && !starting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Amber, contentColor = Palette.Night),
            ) { Text("Iniciar la noche", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Palette.Amber else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Night else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TimeOption(time: String, caption: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, if (selected) Palette.Amber else Palette.Outline, RoundedCornerShape(16.dp))
            .background(if (selected) Palette.Amber.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(time, style = MaterialTheme.typography.titleMedium, color = if (selected) Palette.Amber else MaterialTheme.colorScheme.onSurface)
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

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}
