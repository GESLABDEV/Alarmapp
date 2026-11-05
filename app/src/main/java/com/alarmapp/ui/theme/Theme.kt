package com.alarmapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alarmapp.core.sleep.SleepStage

/**
 * Paleta con fundamento (ver README, "Colores"):
 *
 * - Fondo casi negro azulado: el modo oscuro emite poca luz de noche y ahorra batería en OLED.
 * - Ámbar (≈590 nm): las longitudes de onda largas estimulan poco a la melanopsina, el
 *   fotorreceptor que más suprime la melatonina (pico ≈480 nm). Por eso todo lo que se usa
 *   antes de dormir es ámbar.
 * - Cian (≈480 nm): justo en la zona de mayor sensibilidad de la melanopsina; se reserva
 *   para la mañana (reporte), cuando conviene favorecer el estado de alerta.
 * - Amanecer: la pantalla de alarma pasa de rojo profundo a blanco cálido, imitando la
 *   simulación de amanecer estudiada en laboratorio.
 * - Fases: cuatro tonos bien separados en el círculo cromático para distinguirlas de un
 *   vistazo; todos superan 4.5:1 de contraste sobre el fondo (WCAG AA).
 */
object Palette {
    val Night = Color(0xFF0B0E1A)
    val Surface = Color(0xFF151A2B)
    val SurfaceHigh = Color(0xFF1E2438)
    val Outline = Color(0xFF2A3150)
    val TextPrimary = Color(0xFFEDEFF7)
    val TextSecondary = Color(0xFF9AA3BF)

    val Amber = Color(0xFFFFB547)
    val AmberDeep = Color(0xFFFF8A3D)
    val Cyan = Color(0xFF3DD6F5)
    val Danger = Color(0xFFFF6B6B)

    val Deep = Color(0xFF8B7CF6)
    val Light = Color(0xFF48C6EF)
    val Rem = Color(0xFFD08BFA)
    val Awake = Amber

    /** De noche a día: rojo profundo → naranja → ámbar → blanco cálido. */
    val Sunrise = listOf(
        Color(0xFF0B0E1A), Color(0xFF3A0F14), Color(0xFF8A2A17),
        Color(0xFFE8572A), Color(0xFFFFB547), Color(0xFFFFF3D6),
    )

    fun stage(s: SleepStage): Color = when (s) {
        SleepStage.DEEP -> Deep
        SleepStage.LIGHT -> Light
        SleepStage.REM -> Rem
        SleepStage.AWAKE -> Awake
    }
}

fun stageLabel(s: SleepStage?): String = when (s) {
    SleepStage.DEEP -> "Profundo"
    SleepStage.LIGHT -> "Ligero"
    SleepStage.REM -> "REM"
    SleepStage.AWAKE -> "Despierto"
    null -> "—"
}

private val scheme = darkColorScheme(
    primary = Palette.Amber,
    onPrimary = Palette.Night,
    secondary = Palette.Cyan,
    onSecondary = Palette.Night,
    tertiary = Palette.Rem,
    background = Palette.Night,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Danger,
)

private val base = Typography()
private val typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraLight, fontSize = 76.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light, fontSize = 52.sp, letterSpacing = (-1).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    labelMedium = base.labelMedium.copy(letterSpacing = 1.2.sp),
)

/** Modo oscuro siempre (RNF4). */
@Composable
fun AlarmappTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
