package com.alarmapp.ui.night

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alarmapp.alarm.AlarmScheduler
import com.alarmapp.alarm.AlarmStore
import com.alarmapp.app
import com.alarmapp.core.sleep.SmartWake
import com.alarmapp.monitor.SleepMonitorService
import com.alarmapp.ui.components.formatTime
import com.alarmapp.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pantalla durante la noche: brillo al mínimo y solo tonos ámbar/rojizos,
 * para emitir la menor cantidad posible de luz de onda corta (RF1).
 */
@Composable
fun NightScreen(sessionId: Long, onFinished: (Long) -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    val running by SleepMonitorService.running.collectAsState()
    val session by produceState<com.alarmapp.data.SleepSessionEntity?>(null, sessionId) { value = repo.session(sessionId) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var ending by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(15_000) } }

    // Baja el brillo mientras la pantalla esté visible.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val attrs = window?.attributes
        val previous = attrs?.screenBrightness
        if (window != null && attrs != null) {
            attrs.screenBrightness = 0.01f
            window.attributes = attrs
        }
        onDispose {
            if (window != null && attrs != null) {
                attrs.screenBrightness = previous ?: -1f
                window.attributes = attrs
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Palette.Night).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(24.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(formatTime(now), style = MaterialTheme.typography.displayLarge, color = Palette.AmberDeep.copy(alpha = 0.75f))
            Spacer(Modifier.height(12.dp))
            session?.let { s ->
                val from = SmartWake.windowStart(s.deadlineTime, s.windowMinutes)
                Text(
                    "Despertar entre ${formatTime(from)} y ${formatTime(s.deadlineTime)}",
                    style = MaterialTheme.typography.bodyLarge, color = Palette.AmberDeep.copy(alpha = 0.5f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                if (running == sessionId) "Midiendo tu noche · deja el celular sobre el colchón"
                else "La medición se detuvo, pero la alarma sigue programada",
                style = MaterialTheme.typography.bodyMedium, color = Palette.AmberDeep.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .border(1.dp, Palette.AmberDeep.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                .pointerInput(ending) {
                    detectTapGestures(onLongPress = {
                        if (ending) return@detectTapGestures
                        ending = true
                        // Fin manual de la noche (el usuario ya se despertó).
                        AlarmScheduler.cancelAll(context)
                        SleepMonitorService.stop(context)
                        AlarmStore.setActiveSession(context, null)
                        AlarmStore.setLastDeadline(context, session?.deadlineTime ?: System.currentTimeMillis())
                        AlarmScheduler.armAfterWake(context)
                        scope.launch {
                            repo.finishSession(sessionId, System.currentTimeMillis(), actualAlarmTime = null)
                            onFinished(sessionId)
                        }
                    })
                }
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (ending) "Calculando tu noche…" else "Mantén presionado para terminar la noche",
                style = MaterialTheme.typography.labelLarge, color = Palette.AmberDeep.copy(alpha = 0.6f),
            )
        }
    }
}
