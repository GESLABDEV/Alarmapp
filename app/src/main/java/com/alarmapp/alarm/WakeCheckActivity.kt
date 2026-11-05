package com.alarmapp.alarm

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alarmapp.ui.components.GlowButton
import com.alarmapp.ui.theme.AlarmappTheme
import com.alarmapp.ui.theme.Palette
import com.alarmapp.util.LockScreen
import com.alarmapp.util.Notifications
import kotlinx.coroutines.delay

/** HU7: "¿Sigues despierto?". Si nadie responde, la alarma vuelve a sonar. */
class WakeCheckActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LockScreen.showOverLock(this)
        enableEdgeToEdge()
        setContent {
            AlarmappTheme {
                var remaining by remember { mutableLongStateOf(secondsLeft()) }
                LaunchedEffect(Unit) {
                    while (remaining > 0) { delay(1_000); remaining = secondsLeft() }
                    finish() // la alarma de verificación ya se disparó
                }
                Column(
                    Modifier.fillMaxSize().background(Palette.Night).statusBarsPadding().navigationBarsPadding().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("¿Sigues despierto?", style = MaterialTheme.typography.headlineMedium, color = Palette.Cyan)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Si no respondes en $remaining s, la alarma volverá a sonar.",
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(40.dp))
                    GlowButton(
                        text = "Sí, estoy despierto",
                        onClick = ::confirm,
                        color = Palette.Cyan,
                        height = 64.dp,
                        textStyle = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    private fun secondsLeft(): Long {
        val fire = AlarmStore.fire(this) ?: return 0
        if (fire.kind != AlarmStore.Kind.GUARANTEED) return 0
        return ((fire.fireAt - System.currentTimeMillis()) / 1_000).coerceAtLeast(0)
    }

    private fun confirm() {
        AlarmScheduler.cancelFire(this)
        AlarmService.command(this, AlarmService.ACTION_CHECK_OK)
        AlarmScheduler.armAfterWake(this)
        getSystemService(NotificationManager::class.java).cancel(Notifications.ID_WAKE_CHECK)
        finish()
    }
}
