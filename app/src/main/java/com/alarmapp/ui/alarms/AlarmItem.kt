package com.alarmapp.ui.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmapp.alarm.AlarmSync
import com.alarmapp.app
import com.alarmapp.core.alarm.AlarmTimes
import com.alarmapp.data.AlarmEntity
import com.alarmapp.data.AlarmMode
import com.alarmapp.ui.components.Card
import com.alarmapp.ui.components.Overline
import com.alarmapp.ui.components.formatTime
import com.alarmapp.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Una alarma de la lista de la pantalla principal: tocar para editar, interruptor para
 * activar/desactivar. [isNext] marca la que sonará primero.
 */
@Composable
fun AlarmItem(alarm: AlarmEntity, isNext: Boolean, onEdit: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    Card(Modifier.clickable { onEdit(alarm.id) }, accent = if (alarm.active) Palette.PrimaryText else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (isNext) Overline("Próxima", Palette.PrimaryText)
                Text(
                    formatTime(AlarmTimes.next(alarm.minuteOfDay, AlarmTimes.ALL_DAYS, System.currentTimeMillis())),
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
}
