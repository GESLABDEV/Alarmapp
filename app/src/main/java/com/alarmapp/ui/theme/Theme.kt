package com.alarmapp.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.alarmapp.core.sleep.SleepStage

/**
 * Paleta (ver README, "Colores"):
 *
 * - DÍA · Lavanda: fondo lila muy claro, botones lavanda y durazno como acento. Suave y
 *   minimalista para usar la app con luz.
 * - NOCHE · Arena y cielo: al presionar "Me voy a dormir" la app pasa a un azul noche profundo
 *   (no negro) con texto en tonos cálidos: poca luz emitida y sin blancos que encandilen.
 * - ALARMA · Amanecer: rojo profundo → brasa, imitando la simulación de amanecer.
 * - Todo texto cumple contraste ≥ 4.5:1 (WCAG AA) sobre su fondo.
 */
object Palette {
    // ---------------------------------------------------------------- Día · Lavanda
    val Background = Color(0xFFF6F3FB)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceHigh = Color(0xFFECE7F8)
    val Outline = Color(0xFFDDD6EE)
    val TextPrimary = Color(0xFF2D2840)
    val TextSecondary = Color(0xFF625C78)
    /** Relleno del botón principal (texto blanco encima, 5.2:1). */
    val Primary = Color(0xFF6E5BB8)
    /** Lavanda oscuro: texto, íconos y bordes de botones secundarios. */
    val PrimaryText = Color(0xFF4E3F95)
    val PrimarySoft = Color(0xFFE8E2F7)
    /** Durazno: la mañana (última noche, reporte). */
    val Accent = Color(0xFFA04F30)
    val AccentSoft = Color(0xFFFBE3D8)
    val Danger = Color(0xFFB3261E)

    // ---------------------------------------------------------------- Noche · Arena y cielo
    val NightBg = Color(0xFF1A1F27)
    val NightSurface = Color(0xFF242A33)
    val NightOutline = Color(0xFF343C48)
    val NightText = Color(0xFFE7C98F)
    val NightTextSecondary = Color(0xFFB8AE98)
    val NightTextTertiary = Color(0xFF8C97A6)
    val NightSky = Color(0xFF8FB8DE)

    // ---------------------------------------------------------------- Alarma · Amanecer
    /** Naranja brasa: fondo de selección donde el texto es crema (contraste 5.3:1). */
    val Ember = Color(0xFFC2410C)
    /** Crema luminoso: texto sobre el amanecer, en lugar de negro. */
    val Cream = Color(0xFFFFF3D6)

    /**
     * De noche a amanecer: azul noche → rojo profundo → brasa. Se detiene en un naranja
     * oscuro para que el texto crema siempre se lea; la intensidad la aporta el brillo.
     */
    val Sunrise = listOf(
        Color(0xFF1A1F27), Color(0xFF2A0B12), Color(0xFF5C1A14),
        Color(0xFF8A2A17), Color(0xFFB23A12), Color(0xFFC2410C),
    )

    // ---------------------------------------------------------------- Fases (sobre fondo claro)
    val Deep = Color(0xFF4E3F95)
    val Light = Color(0xFF3D7FB8)
    val Rem = Color(0xFFA8457F)
    val Awake = Color(0xFFB8652A)

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

private val dayScheme = lightColorScheme(
    primary = Palette.Primary,
    onPrimary = Palette.Surface,
    primaryContainer = Palette.PrimarySoft,
    onPrimaryContainer = Palette.PrimaryText,
    secondary = Palette.Accent,
    onSecondary = Palette.Surface,
    tertiary = Palette.Rem,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceHigh,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.Surface,
    surfaceContainerHighest = Palette.SurfaceHigh,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Palette.Danger,
    onError = Palette.Surface,
)

private val nightScheme = darkColorScheme(
    primary = Palette.NightText,
    onPrimary = Palette.NightBg,
    secondary = Palette.NightSky,
    onSecondary = Palette.NightBg,
    background = Palette.NightBg,
    onBackground = Palette.NightText,
    surface = Palette.NightSurface,
    onSurface = Palette.NightText,
    surfaceVariant = Palette.NightSurface,
    onSurfaceVariant = Palette.NightTextSecondary,
    surfaceContainer = Palette.NightSurface,
    surfaceContainerHigh = Palette.NightSurface,
    surfaceContainerHighest = Palette.NightSurface,
    outline = Palette.NightOutline,
    outlineVariant = Palette.NightOutline,
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

/**
 * Claro de día; [night] = true para la noche y la alarma (oscuro cálido, RNF4 actualizado:
 * "claro de día, oscuro cálido durante la noche").
 */
@Composable
fun AlarmappTheme(night: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (night) nightScheme else dayScheme, typography = typography) {
        // Sin esto, los textos sin color explícito tomarían el color por defecto.
        CompositionLocalProvider(
            LocalContentColor provides if (night) Palette.NightText else Palette.TextPrimary,
            content = content,
        )
    }
}
