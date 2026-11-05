package com.alarmapp.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.alarmapp.core.sleep.SleepStage
import com.alarmapp.ui.theme.Palette
import com.alarmapp.ui.theme.stageLabel
import java.util.Date

@Composable
fun formatTime(ms: Long): String {
    val ctx = LocalContext.current
    return DateFormat.getTimeFormat(ctx).format(Date(ms))
}

fun formatDuration(minutes: Int?): String {
    if (minutes == null) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h} h ${m} min" else "$m min"
}

/**
 * Botón principal: relleno translúcido del color, borde y texto luminosos.
 * Evita el texto negro y se lee bien en la oscuridad sin deslumbrar.
 */
@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Palette.Amber,
    enabled: Boolean = true,
    height: Dp = 56.dp,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    fillAlpha: Float = 0.16f,
) {
    val c = if (enabled) color else color.copy(alpha = 0.35f)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(c.copy(alpha = fillAlpha))
            .border(1.5.dp, c, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = textStyle, color = c) }
}

/** Pestaña seleccionable con el mismo estilo luminoso. */
@Composable
fun GlowTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (selected) Palette.Amber.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (selected) Palette.Amber else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Palette.Amber else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Tarjeta minimalista: superficie apenas más clara que el fondo, sin sombras. */
@Composable
fun Card(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, accent?.copy(alpha = 0.35f) ?: Palette.Outline, RoundedCornerShape(24.dp))
            .padding(20.dp),
        content = content,
    )
}

@Composable
fun Overline(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun Metric(label: String, value: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
    }
}

/** Hipnograma: una fila por fase, de despierto (arriba) a profundo (abajo). */
@Composable
fun Hypnogram(stages: List<SleepStage>, modifier: Modifier = Modifier) {
    val rows = listOf(SleepStage.AWAKE, SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)
    Row(modifier.fillMaxWidth()) {
        Column(Modifier.height(140.dp), verticalArrangement = Arrangement.SpaceAround) {
            rows.forEach {
                Text(stageLabel(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(10.dp))
        Canvas(Modifier.weight(1f).height(140.dp)) {
            if (stages.isEmpty()) return@Canvas
            val rowH = size.height / rows.size
            val barH = rowH * 0.62f
            val w = size.width / stages.size
            // Guías horizontales
            rows.indices.forEach { r ->
                drawLine(Palette.Outline, Offset(0f, rowH * (r + 0.5f)), Offset(size.width, rowH * (r + 0.5f)), strokeWidth = 1f)
            }
            // Bloques contiguos de la misma fase
            var i = 0
            while (i < stages.size) {
                var j = i
                while (j + 1 < stages.size && stages[j + 1] == stages[i]) j++
                val r = rows.indexOf(stages[i])
                drawRoundRect(
                    color = Palette.stage(stages[i]),
                    topLeft = Offset(i * w, rowH * r + (rowH - barH) / 2),
                    size = Size(((j - i + 1) * w).coerceAtLeast(2f), barH),
                    cornerRadius = CornerRadius(barH / 2, barH / 2),
                )
                i = j + 1
            }
        }
    }
}

/** Distribución de fases en una barra apilada con leyenda. */
@Composable
fun StageBar(minutes: Map<SleepStage, Int>) {
    val total = minutes.values.sum().coerceAtLeast(1)
    val order = listOf(SleepStage.DEEP, SleepStage.LIGHT, SleepStage.REM, SleepStage.AWAKE)
    Row(
        Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Palette.SurfaceHigh),
    ) {
        order.forEach { s ->
            val m = minutes[s] ?: 0
            if (m > 0) Box(Modifier.weight(m.toFloat()).height(14.dp).background(Palette.stage(s)))
        }
    }
    Spacer(Modifier.height(14.dp))
    order.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            pair.forEach { s ->
                val m = minutes[s] ?: 0
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Palette.stage(s)))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${stageLabel(s)}  ${formatDuration(m)} · ${Math.round(m * 100f / total)}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
