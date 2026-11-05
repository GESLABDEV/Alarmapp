package com.alarmapp.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.alarmapp.ui.theme.Palette
import com.alarmapp.util.Permissions

private data class Item(
    val title: String,
    val why: String,
    val granted: (Context) -> Boolean,
    val request: () -> Unit,
)

/** Lista de permisos con su explicación; se actualiza al volver de los ajustes del sistema. */
@Composable
fun PermissionList() {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }

    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    fun open(intent: Intent?) {
        val i = intent ?: Permissions.appSettings(context)
        runCatching { context.startActivity(i) }.onFailure { context.startActivity(Permissions.appSettings(context)) }
    }

    val items = listOf(
        Item("Notificaciones", "Para mostrar la alarma y la notificación fija de la noche.", Permissions::notifications) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runtime.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        Item("Alarmas exactas", "Para que la alarma suene a la hora justa.", Permissions::exactAlarms) {
            open(Permissions.exactAlarmSettings(context))
        },
        Item("Pantalla completa", "Para mostrar la alarma con el celular bloqueado.", Permissions::fullScreen) {
            open(Permissions.fullScreenSettings(context))
        },
        Item(
            "Mostrar sobre otras apps",
            "Para que la pantalla de la alarma se abra sola con el celular bloqueado o la pantalla apagada.",
            Permissions::overlay,
        ) { open(Permissions.overlaySettings(context)) },
        Item(
            "Sonar en No molestar y silencio",
            "Para que la alarma y \"¿Sigues despierto?\" suenen aunque el celular esté en silencio o en No molestar. Busca Alarmapp y actívalo.",
            Permissions::dnd,
        ) { open(Permissions.dndSettings()) },
        Item("Micrófono", "Analiza el sonido en el celular para estimar tu respiración. Nunca se graba ni se guarda audio.", Permissions::mic) {
            runtime.launch(Manifest.permission.RECORD_AUDIO)
        },
        Item("Sin optimización de batería", "Algunos fabricantes (Xiaomi, Samsung…) cierran apps en segundo plano. Busca Alarmapp y elige \"No optimizar\".", Permissions::batteryUnrestricted) {
            open(Permissions.batterySettings())
        },
    )

    Column {
        if (Permissions.isXiaomi()) {
            Text(
                "En Xiaomi, Redmi o POCO activa también en Ajustes → Apps → Alarmapp → Otros permisos: " +
                    "\"Mostrar en pantalla de bloqueo\" y \"Mostrar ventanas emergentes mientras se ejecuta en segundo plano\".",
                style = MaterialTheme.typography.bodyMedium, color = Palette.Amber,
                modifier = Modifier.padding(vertical = 8.dp).clickable { open(Permissions.appSettings(context)) },
            )
        }
        items.forEach { item ->
            val ok = remember(refresh) { item.granted(context) }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !ok) { item.request() }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                    tint = if (ok) Palette.Cyan else Palette.Amber, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    Text(item.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!ok) Text("Toca para activar", style = MaterialTheme.typography.labelLarge, color = Palette.Amber, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
