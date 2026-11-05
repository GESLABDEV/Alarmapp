package com.alarmapp.ui.report

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alarmapp.app
import com.alarmapp.core.sleep.SleepStage
import com.alarmapp.data.SleepSessionEntity
import com.alarmapp.sync.SummaryUploadWorker
import com.alarmapp.ui.components.Card
import com.alarmapp.ui.components.Hypnogram
import com.alarmapp.ui.components.Metric
import com.alarmapp.ui.components.Overline
import com.alarmapp.ui.components.StageBar
import com.alarmapp.ui.components.formatDuration
import com.alarmapp.ui.components.formatTime
import com.alarmapp.ui.theme.Palette
import com.alarmapp.ui.theme.stageLabel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReportState(
    val session: SleepSessionEntity? = null,
    val stages: List<SleepStage> = emptyList(),
    val rating: Int? = null,
)

class ReportViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {
    private val repo = application.app.repository
    private val sessionId: Long = when (val v = handle.get<Any>("sessionId")) {
        is Long -> v
        is String -> v.toLongOrNull() ?: -1L
        else -> -1L
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<ReportState> = combine(repo.observeSession(sessionId), repo.observeRating(sessionId)) { s, r -> s to r }
        .flatMapLatest { (s, r) ->
            flow {
                // Las fases se leen cuando la noche ya se cerró (reclasificada con contexto completo).
                val stages = if (s?.endTime != null) repo.indicators(sessionId).map { it.stage } else emptyList()
                emit(ReportState(s, stages, r))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportState())

    fun rate(score: Int) = viewModelScope.launch {
        val consent = repo.rate(sessionId, score)
        if (consent) SummaryUploadWorker.enqueue(getApplication())
    }
}

/** Reporte matutino (RF6, HU6). Usa cian: color de alerta para la mañana. */
@Composable
fun ReportScreen(onBack: () -> Unit, vm: ReportViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val s = state.session

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
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") }
            Overline("Reporte matutino", Palette.Cyan)
        }

        if (s == null || s.endTime == null) {
            Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Palette.Cyan)
                    Spacer(Modifier.height(16.dp))
                    Text("Calculando tu noche…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@Column
        }

        Spacer(Modifier.height(8.dp))
        Text("Dormiste", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatDuration(s.totalSleepMinutes), style = MaterialTheme.typography.displayMedium, color = Palette.Cyan)
        Text(
            "${formatTime(s.startTime)} → ${formatTime(s.endTime)}",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        RatingCard(state.rating, onRate = vm::rate)

        Spacer(Modifier.height(16.dp))
        Card {
            Row(Modifier.fillMaxWidth()) {
                Metric("Tardaste en dormirte", formatDuration(s.latencyMinutes), Modifier.weight(1f))
                Metric("Despertaste en", stageLabel(s.wakeStage), Modifier.weight(1f), color = s.wakeStage?.let(Palette::stage) ?: MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth()) {
                Metric("Alarma", s.actualAlarmTime?.let { formatTime(it) } ?: "Terminaste tú", Modifier.weight(1f))
                Metric("Pospuestas", "${s.snoozeCount}", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))
        Card {
            Overline("Fases estimadas")
            Spacer(Modifier.height(14.dp))
            Hypnogram(state.stages)
            Spacer(Modifier.height(18.dp))
            StageBar(
                mapOf(
                    SleepStage.DEEP to (s.deepMinutes ?: 0),
                    SleepStage.LIGHT to (s.lightMinutes ?: 0),
                    SleepStage.REM to (s.remMinutes ?: 0),
                    SleepStage.AWAKE to (s.awakeMinutes ?: 0),
                )
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            "Estimación hecha solo con el celular: es orientativa y menos precisa que un dispositivo dedicado.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RatingCard(rating: Int?, onRate: (Int) -> Unit) {
    // Gradiente ámbar → cian: de "cansado" a "con energía".
    val colors = listOf(Color(0xFFFF8A3D), Color(0xFFFFB547), Color(0xFFD9D27A), Color(0xFF8FD8B8), Palette.Cyan)
    val labels = listOf("Agotado", "Cansado", "Normal", "Bien", "Con energía")
    Card(accent = Palette.Cyan) {
        Text("¿Cómo te sientes?", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            if (rating == null) "Tu respuesta ajusta la alarma a ti." else labels[rating - 1],
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n ->
                val selected = rating == n
                val c = colors[n - 1]
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.copy(alpha = if (selected) 0.28f else 0.10f))
                        .border(if (selected) 2.dp else 1.dp, c.copy(alpha = if (selected) 1f else 0.4f), RoundedCornerShape(16.dp))
                        .clickable { onRate(n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$n", style = MaterialTheme.typography.titleLarge, color = c)
                }
            }
        }
    }
}
