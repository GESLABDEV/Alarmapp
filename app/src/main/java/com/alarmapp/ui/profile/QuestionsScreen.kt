package com.alarmapp.ui.profile

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmapp.alarm.AlarmSync
import com.alarmapp.app
import com.alarmapp.core.alarm.AlarmTimes
import com.alarmapp.core.sleep.AgeRange
import com.alarmapp.core.sleep.MenstrualCycle
import com.alarmapp.core.sleep.Sex
import com.alarmapp.data.AlarmMode
import com.alarmapp.ui.components.GlowButton
import com.alarmapp.ui.components.Overline
import com.alarmapp.ui.theme.Palette
import kotlinx.coroutines.launch

private enum class Step { NAME, SEX, AGE, WAKE, CYCLE }

/**
 * Preguntas iniciales, una por pantalla y todas opcionales. Lo que responda la persona
 * ajusta el saludo, la primera alarma y los Reportes. Se pueden cambiar en Ajustes → Mis datos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionsScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf<Sex?>(null) }
    var age by rememberSaveable { mutableStateOf<AgeRange?>(null) }
    var wakeSet by rememberSaveable { mutableStateOf(false) }
    val wake = rememberTimePickerState(6, 0, DateFormat.is24HourFormat(context))
    var lastPeriod by rememberSaveable { mutableStateOf<Long?>(null) }
    var cycleDays by rememberSaveable { mutableIntStateOf(MenstrualCycle.DEFAULT_CYCLE_DAYS) }
    var periodDays by rememberSaveable { mutableIntStateOf(MenstrualCycle.DEFAULT_PERIOD_DAYS) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }

    val steps = buildList {
        add(Step.NAME); add(Step.SEX); add(Step.AGE); add(Step.WAKE)
        if (sex == Sex.FEMALE) add(Step.CYCLE)
    }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val last = index >= steps.lastIndex

    fun finish() {
        if (saving) return
        saving = true
        scope.launch {
            val wakeMinute = if (wakeSet) wake.hour * 60 + wake.minute else null
            repo.updatePersonal(name, sex, age, wakeMinute, cycleDays, periodDays)
            if (sex == Sex.FEMALE) lastPeriod?.let { repo.addPeriodStart(it) }
            // Primera alarma a partir de la hora habitual de despertar, si aún no hay ninguna.
            if (wakeMinute != null && repo.alarms().isEmpty()) {
                repo.saveAlarm(null, AlarmMode.FIXED, wakeMinute, AlarmTimes.ALL_DAYS)
                AlarmSync.apply(context, repo.alarms())
            }
            onFinish()
        }
    }

    fun next() { if (last) finish() else index++ }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
    ) {
        // Progreso
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 12.dp)) {
            steps.indices.forEach { i ->
                Box(
                    Modifier.height(6.dp).width(if (i == index) 28.dp else 12.dp).clip(CircleShape)
                        .background(if (i <= index) Palette.Primary else Palette.Outline)
                )
            }
        }
        Spacer(Modifier.height(28.dp))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            when (step) {
                Step.NAME -> {
                    Overline("Conozcámonos", Palette.PrimaryText)
                    Title("¿Cómo te llamas?")
                    Hint("Lo usamos para saludarte.")
                    SpacerS()
                    NameField(name) { name = it }
                }
                Step.SEX -> {
                    Overline("Sobre ti", Palette.PrimaryText)
                    Title("¿Cuál es tu sexo?")
                    Hint("Si eliges Mujer, podrás registrar tu ciclo menstrual para ver cómo influye en tu sueño.")
                    SpacerS()
                    SexChoice(sex) { sex = it }
                }
                Step.AGE -> {
                    Overline("Sobre ti", Palette.PrimaryText)
                    Title("¿Cuál es tu edad?")
                    Hint("Las horas de sueño recomendadas cambian con la edad.")
                    SpacerS()
                    AgeChoice(age) { age = it }
                }
                Step.WAKE -> {
                    Overline("Tu rutina", Palette.PrimaryText)
                    Title("¿A qué hora sueles despertarte?")
                    Hint("Creamos tu primera alarma a esa hora y te sugerimos a qué hora acostarte.")
                    SpacerS()
                    WakeTimePicker(wake)
                }
                Step.CYCLE -> {
                    Overline("Tu ciclo", Palette.PrimaryText)
                    Title("Ciclo menstrual")
                    Hint("Es una estimación orientativa para relacionar tu ciclo con tu sueño; no sirve como método anticonceptivo. Se guarda cifrado solo en tu celular.")
                    SpacerS()
                    DateField("Inicio de tu último periodo", lastPeriod) { lastPeriod = it }
                    SpacerS()
                    Stepper("Duración del ciclo", cycleDays, 21..45, "días") { cycleDays = it }
                    Stepper("Días de sangrado", periodDays, 2..10, "días") { periodDays = it }
                }
            }
        }

        GlowButton(
            text = if (saving) "Guardando…" else if (last) "Listo" else "Siguiente",
            enabled = !saving,
            color = Palette.PrimaryText,
            onClick = {
                if (step == Step.WAKE) wakeSet = true
                next()
            },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (index > 0) index-- }, enabled = index > 0) {
                Text(if (index > 0) "Atrás" else "", color = Palette.TextSecondary)
            }
            TextButton(onClick = {
                if (step == Step.WAKE) wakeSet = false
                next()
            }) { Text("Omitir", color = Palette.TextSecondary) }
        }
    }
}

@Composable
private fun Title(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text, style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(8.dp))
}
