package com.alarmapp.ui.profile

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.alarmapp.app
import com.alarmapp.core.sleep.AgeRange
import com.alarmapp.core.sleep.Sex
import com.alarmapp.data.ProfileEntity
import com.alarmapp.ui.components.Card
import com.alarmapp.ui.components.GlowButton
import com.alarmapp.ui.components.Overline
import com.alarmapp.ui.theme.Palette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Date

/** Ajustes → Mis datos: editar lo respondido en las preguntas iniciales. */
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val repo = LocalContext.current.app.repository
    var loaded by remember { mutableStateOf<ProfileEntity?>(null) }
    LaunchedEffect(Unit) { loaded = repo.observeProfile().first() ?: repo.profileEntity() }
    val p = loaded
    if (p == null) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    else ProfileEditor(p, onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(p: ProfileEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = context.app.repository
    val scope = rememberCoroutineScope()
    val periods by repo.observePeriods().collectAsState(initial = emptyList())

    var name by remember { mutableStateOf(p.name.orEmpty()) }
    var sex by remember { mutableStateOf(p.sex?.let { runCatching { Sex.valueOf(it) }.getOrNull() }) }
    var age by remember { mutableStateOf(p.ageRange?.let { runCatching { AgeRange.valueOf(it) }.getOrNull() }) }
    var wakeOn by remember { mutableStateOf(p.wakeMinute != null) }
    val wake = rememberTimePickerState((p.wakeMinute ?: 360) / 60, (p.wakeMinute ?: 360) % 60, DateFormat.is24HourFormat(context))
    var cycleDays by remember { mutableIntStateOf(p.cycleDays) }
    var periodDays by remember { mutableIntStateOf(p.periodDays) }
    var newPeriod by remember { mutableStateOf<Long?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver", tint = Palette.TextPrimary) }
            Overline("Mis datos")
        }
        Spacer(Modifier.height(8.dp))

        Card {
            NameField(name) { name = it }
            Spacer(Modifier.height(16.dp))
            Overline("Sexo")
            SexChoice(sex) { sex = it }
            Spacer(Modifier.height(16.dp))
            Overline("Edad")
            AgeChoice(age) { age = it }
        }
        Spacer(Modifier.height(16.dp))

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hora habitual de despertar", style = MaterialTheme.typography.titleMedium)
                    Hint("Con ella Reportes te sugiere a qué hora acostarte.")
                }
                Switch(
                    checked = wakeOn, onCheckedChange = { wakeOn = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Primary, checkedThumbColor = Palette.Surface, checkedBorderColor = Palette.Primary),
                )
            }
            if (wakeOn) {
                Spacer(Modifier.height(12.dp))
                WakeTimePicker(wake)
            }
        }

        if (sex == Sex.FEMALE) {
            Spacer(Modifier.height(16.dp))
            Card(accent = Palette.Rem) {
                Overline("Ciclo menstrual", Palette.Rem)
                Spacer(Modifier.height(6.dp))
                Stepper("Duración del ciclo", cycleDays, 21..45, "días") { cycleDays = it }
                Stepper("Días de sangrado", periodDays, 2..10, "días") { periodDays = it }
                Spacer(Modifier.height(8.dp))
                DateField("Agregar inicio de un periodo", newPeriod) { newPeriod = it }
                if (periods.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Overline("Periodos registrados")
                    periods.sortedByDescending { it.startTime }.take(8).forEach { period ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                DateFormat.getLongDateFormat(context).format(Date(period.startTime)),
                                style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { scope.launch { repo.deletePeriod(period.id) } }) {
                                Icon(Icons.Outlined.Close, "Eliminar", tint = Palette.TextSecondary)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Hint("Estimación orientativa; no sirve como método anticonceptivo. Se guarda cifrado solo en tu celular.")
            }
        }

        Spacer(Modifier.height(20.dp))
        GlowButton(
            text = "Guardar",
            onClick = {
                scope.launch {
                    repo.updatePersonal(name, sex, age, if (wakeOn) wake.hour * 60 + wake.minute else null, cycleDays, periodDays)
                    if (sex == Sex.FEMALE) newPeriod?.let { repo.addPeriodStart(it) }
                    Toast.makeText(context, "Datos guardados", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            },
        )
        Spacer(Modifier.height(28.dp))
    }
}
