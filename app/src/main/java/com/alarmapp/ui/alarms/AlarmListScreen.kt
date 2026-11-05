package com.alarmapp.ui.alarms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmapp.alarm.AlarmSync
import com.alarmapp.app
import com.alarmapp.core.alarm.AlarmTimes
import com.alarmapp.data.AlarmMode
import com.alarmapp.ui.components.Card
import com.alarmapp.ui.components.GlowButton
import com.alarmapp.ui.components.Overline
import com.alarmapp.ui.components.formatTime
import com.alarmapp.ui.theme.Palette
import kotlinx.coroutines.launch

/** Lista de alarmas guardadas: activar/desactivar, editar o crear nuevas. */
@Composable
fun AlarmListScreen(onBack: () -> Unit, onEdit: (Long?) -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    val alarms by repo.observeAlarms().collectAsState(initial = emptyList())

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
            Overline("Alarmas")
        }
        Spacer(Modifier.height(8.dp))

        if (alarms.isEmpty()) {
            Text(
                "Aún no tienes alarmas. Crea una para cada horario que necesites (por ejemplo, entre semana y fines de semana).",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        alarms.forEach { alarm ->
            Card(Modifier.clickable { onEdit(alarm.id) }, accent = if (alarm.active) Palette.PrimaryText else null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val minute = alarm.minuteOfDay
                        Text(
                            formatTime(AlarmTimes.next(minute, AlarmTimes.ALL_DAYS, System.currentTimeMillis())),
                            style = MaterialTheme.typography.displayMedium,
                            color = if (alarm.active) Palette.TextPrimary else Palette.TextSecondary,
                        )
                        Text(
                            "${AlarmTimes.label(alarm.days)} · ${if (alarm.mode == AlarmMode.INVERSE) "Modo inverso" else "Hora fija"}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = alarm.active,
                        onCheckedChange = { on ->
                            scope.launch {
                                repo.setAlarmActive(alarm.id, on)
                                AlarmSync.apply(context, repo.alarms())
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Palette.Primary,
                            checkedThumbColor = Palette.Surface,
                            checkedBorderColor = Palette.Primary,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(8.dp))
        GlowButton(text = "+ Nueva alarma", onClick = { onEdit(null) })
        Spacer(Modifier.height(24.dp))
    }
}
