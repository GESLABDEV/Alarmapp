package com.alarmapp.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.alarmapp.ui.MainActivity
import com.alarmapp.util.LockScreen
import com.alarmapp.ui.components.GlowButton
import com.alarmapp.ui.components.formatTime
import com.alarmapp.ui.theme.AlarmappTheme
import com.alarmapp.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * Pantalla de alarma sobre la pantalla de bloqueo. El fondo y el brillo suben como un
 * amanecer (RF5): primero rojos profundos, luego ámbar y al final blanco cálido.
 */
class AlarmActivity : ComponentActivity() {

    private var openingReport = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockScreen.showOverLock(this)
        enableEdgeToEdge()
        setContent {
            AlarmappTheme {
                val state by AlarmController.state.collectAsState()
                LaunchedEffect(state.light, state.flashing) { setBrightness(if (state.flashing) 1f else state.light) }
                LaunchedEffect(state.dismissedSessionId, state.phase) {
                    val dismissed = state.dismissedSessionId
                    if (dismissed != null) {
                        openingReport = true
                        AlarmController.consumeDismissed()
                        openReport(dismissed)
                    } else if (state.phase == AlarmController.Phase.IDLE && !openingReport) {
                        finish() // pospuesta o ya detenida
                    }
                }
                AlarmScreen(
                    state = state,
                    onSnooze = { AlarmService.command(this, AlarmService.ACTION_SNOOZE) },
                    onDismiss = { AlarmService.command(this, AlarmService.ACTION_DISMISS) },
                )
            }
        }
    }

    private fun setBrightness(light: Float) {
        window.attributes = window.attributes.apply { screenBrightness = (0.02f + 0.98f * light).coerceIn(0.02f, 1f) }
    }

    private fun openReport(sessionId: Long) {
        val launch = {
            if (sessionId >= 0) {
                startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_REPORT, sessionId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            finish()
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km.isKeyguardLocked) {
            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() { launch() }
                override fun onDismissCancelled() { finish() }
                override fun onDismissError() { finish() }
            })
        } else launch()
    }
}

@Composable
private fun AlarmScreen(state: AlarmController.State, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val light by animateFloatAsState(state.light, tween(900), label = "light")
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(10_000) } }

    // Destellos si la persona no responde: 1 por segundo (muy por debajo de 3 por segundo,
    // el límite de seguridad para personas fotosensibles).
    var flashOn by remember { mutableStateOf(false) }
    LaunchedEffect(state.flashing) {
        flashOn = false
        while (state.flashing) { delay(500); flashOn = !flashOn }
    }
    val flashColor = if (state.phase == AlarmController.Phase.ESCALATED) Color.White else Palette.Cream

    val top = if (flashOn) flashColor else sunrise(light * 0.85f)
    val bottom = if (flashOn) flashColor else sunrise(light)
    // Texto crema sobre el amanecer: nunca negro. Durante los destellos va sobre un panel oscuro.
    val content = Palette.Cream
    val panel = if (state.flashing) Modifier
        .clip(RoundedCornerShape(24.dp))
        .background(Palette.Night.copy(alpha = 0.72f))
        .padding(16.dp) else Modifier

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Spacer(Modifier.height(40.dp))
        Column(panel, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (state.phase) {
                    AlarmController.Phase.LIGHT -> "Amaneciendo…"
                    AlarmController.Phase.ESCALATED -> "¡Es hora de levantarte!"
                    else -> "Buenos días"
                },
                style = MaterialTheme.typography.titleMedium, color = content.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(8.dp))
            Text(formatTime(now), style = MaterialTheme.typography.displayLarge, color = content)
        }

        Column(Modifier.fillMaxWidth().then(panel)) {
            GlowButton(
                text = "Apagar",
                onClick = onDismiss,
                color = Palette.Cream,
                height = 72.dp,
                textStyle = MaterialTheme.typography.titleLarge,
                fillAlpha = 0.22f,
            )
            Spacer(Modifier.height(12.dp))
            if (state.snoozesLeft > 0) {
                GlowButton(
                    text = "Posponer 5 min · quedan ${state.snoozesLeft}",
                    onClick = onSnooze,
                    color = Palette.Cream.copy(alpha = 0.85f),
                    fillAlpha = 0.08f,
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().border(1.dp, content.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("Ya no puedes posponer más", color = content.copy(alpha = 0.7f)) }
            }
        }
    }
}

/** Interpola el degradado de amanecer: 0 = noche, 1 = día. */
private fun sunrise(t: Float): Color {
    val stops = Palette.Sunrise
    val x = t.coerceIn(0f, 1f) * (stops.size - 1)
    val i = x.toInt().coerceAtMost(stops.size - 2)
    return lerp(stops[i], stops[i + 1], x - i)
}
