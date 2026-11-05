package com.alarmapp.ui.insights

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmapp.app
import com.alarmapp.core.sleep.Insights
import com.alarmapp.core.sleep.SleepStage
import com.alarmapp.ui.components.Card
import com.alarmapp.ui.components.GlowTab
import com.alarmapp.ui.components.Overline
import com.alarmapp.ui.components.StageBar
import com.alarmapp.ui.components.formatDuration
import com.alarmapp.ui.components.formatTime
import com.alarmapp.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PERIODS = listOf(7 to "7 días", 30 to "30 días", 90 to "90 días")

/** Sección Reportes: resumen de varias noches, observaciones e historial. */
@Composable
fun InsightsScreen(onBack: () -> Unit, onOpenNight: (Long) -> Unit) {
    val repo = LocalContext.current.app.repository
    var days by rememberSaveable { mutableIntStateOf(7) }
    val since = remember(days) { System.currentTimeMillis() - days * 86_400_000L }
    val all by remember(since) { repo.observeNights(since) }.collectAsState(initial = null)
    val summary = remember(all) { all?.let { Insights.summarize(it) } }

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
            Overline("Reportes")
        }
        Text("Tu sueño", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Palette.SurfaceHigh).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PERIODS.forEach { (d, label) -> GlowTab(label, days == d, Modifier.weight(1f)) { days = d } }
        }
        Spacer(Modifier.height(18.dp))

        val nights = all ?: return@Column
        val s = summary ?: return@Column
        val counted = nights.filter { it.counts }

        if (counted.isEmpty()) {
            Card {
                Text("Aún no hay noches en este periodo", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Presiona \"Me voy a dormir\" antes de acostarte y apaga la alarma al despertar. " +
                        "Las pruebas de alarma y las sesiones de menos de 2 horas no cuentan.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Cifras principales
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Sueño promedio", formatDuration(s.avgSleepMinutes), Modifier.weight(1f))
                StatCard("Para dormirte", formatDuration(s.avgLatencyMinutes), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    "Cómo te sientes",
                    s.avgRating?.let { String.format(Locale.US, "%.1f", it).replace('.', ',') + " / 5" } ?: "—",
                    Modifier.weight(1f),
                )
                StatCard("Regularidad", s.bedtimeSpreadMinutes?.let { "± ${formatDuration(it)}" } ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))

            Card {
                Overline("Horas dormidas por noche")
                Spacer(Modifier.height(14.dp))
                SleepBars(counted.sortedBy { it.start })
                Spacer(Modifier.height(8.dp))
                Text(
                    "La línea marca 7 h, el mínimo recomendado para adultos. ${s.nights} noche${if (s.nights == 1) "" else "s"} en el periodo.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            Card {
                Overline("Fases en una noche típica")
                Spacer(Modifier.height(14.dp))
                StageBar(
                    mapOf(
                        SleepStage.DEEP to s.deepMinutes,
                        SleepStage.LIGHT to s.lightMinutes,
                        SleepStage.REM to s.remMinutes,
                        SleepStage.AWAKE to s.awakeMinutes,
                    )
                )
            }
            Spacer(Modifier.height(16.dp))

            Card(accent = Palette.Accent) {
                Overline("Lo que vemos", Palette.Accent)
                Spacer(Modifier.height(10.dp))
                if (s.observations.isEmpty()) {
                    Text(
                        "Con ${Insights.MIN_NIGHTS} noches o más te mostramos observaciones sobre tu descanso. Llevas ${s.nights}.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    s.observations.forEach { ObservationRow(it) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Son estimaciones hechas con el celular y referencias generales para adultos; no son un diagnóstico médico.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (nights.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card {
                Overline("Tus noches")
                Spacer(Modifier.height(6.dp))
                nights.forEach { n -> NightRow(n) { onOpenNight(n.id) } }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = Palette.PrimaryText)
    }
}

/** Barras de horas dormidas, una por noche, con línea de 7 h. */
@Composable
private fun SleepBars(nights: List<Insights.Night>) {
    val maxH = maxOf(9.0, nights.maxOf { it.totalSleepMinutes } / 60.0)
    val dayFmt = remember { SimpleDateFormat("EEE", Locale("es")) }
    Canvas(Modifier.fillMaxWidth().height(140.dp)) {
        val n = nights.size
        val slot = size.width / n
        val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
        nights.forEachIndexed { i, night ->
            val h = (night.totalSleepMinutes / 60.0 / maxH * size.height).toFloat().coerceAtLeast(2f)
            val color = if (night.totalSleepMinutes >= 7 * 60) Palette.Primary else Palette.Accent
            drawRoundRect(
                color = color,
                topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 3, barW / 3),
            )
        }
        val y = (size.height - 7.0 / maxH * size.height).toFloat()
        drawLine(
            Palette.TextSecondary, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
    if (nights.size <= 10) {
        Row(Modifier.fillMaxWidth()) {
            nights.forEach { n ->
                Text(
                    dayFmt.format(Date(n.start)).take(3).replaceFirstChar { it.uppercase() },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ObservationRow(o: Insights.Observation) {
    val (icon, color) = when (o.tone) {
        Insights.Tone.GOOD -> Icons.Outlined.CheckCircle to Palette.Primary
        Insights.Tone.NEUTRAL -> Icons.Outlined.Info to Palette.TextSecondary
        Insights.Tone.ATTENTION -> Icons.Outlined.ErrorOutline to Palette.Accent
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(o.title, style = MaterialTheme.typography.titleMedium)
            Text(o.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NightRow(n: Insights.Night, onClick: () -> Unit) {
    val dateFmt = remember { SimpleDateFormat("EEE d MMM", Locale("es")) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(10.dp).clip(CircleShape)
                .background(if (!n.counts) Palette.Outline else if (n.totalSleepMinutes >= 7 * 60) Palette.Primary else Palette.Accent)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(dateFmt.format(Date(n.start)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
            Text(
                if (n.counts) "${formatTime(n.start)} → ${formatTime(n.end)}" + (n.rating?.let { " · te sentiste $it/5" } ?: "")
                else "Sesión corta · no cuenta en el resumen",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatDuration(n.totalSleepMinutes), style = MaterialTheme.typography.titleMedium, color = Palette.PrimaryText)
        Icon(Icons.Outlined.ChevronRight, null, tint = Palette.TextSecondary)
    }
}
