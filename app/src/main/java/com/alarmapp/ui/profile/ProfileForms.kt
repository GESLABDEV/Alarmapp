package com.alarmapp.ui.profile

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.alarmapp.core.sleep.AgeRange
import com.alarmapp.core.sleep.Sex
import com.alarmapp.ui.theme.Palette
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/** Piezas de formulario compartidas por las preguntas iniciales y "Mis datos". */

@Composable
fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(30)) },
        label = { Text("Tu nombre") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Primary,
            focusedLabelColor = Palette.PrimaryText,
            cursorColor = Palette.Primary,
            unfocusedBorderColor = Palette.Outline,
        ),
    )
}

/** Opción seleccionable de una sola elección. */
@Composable
fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clip(shape)
            .background(if (selected) Palette.PrimarySoft else Palette.Surface)
            .border(1.5.dp, if (selected) Palette.Primary else Palette.Outline, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .border(2.dp, if (selected) Palette.Primary else Palette.Outline, CircleShape)
                .padding(4.dp)
                .clip(CircleShape)
                .background(if (selected) Palette.Primary else Color.Transparent)
        )
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (selected) Palette.PrimaryText else Palette.TextPrimary)
    }
}

@Composable
fun SexChoice(value: Sex?, onChange: (Sex) -> Unit) {
    Column { Sex.entries.forEach { s -> ChoiceRow(s.label, value == s) { onChange(s) } } }
}

@Composable
fun AgeChoice(value: AgeRange?, onChange: (AgeRange) -> Unit) {
    Column { AgeRange.entries.forEach { a -> ChoiceRow(a.label, value == a) { onChange(a) } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeTimePicker(state: TimePickerState) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TimePicker(
            state = state,
            colors = TimePickerDefaults.colors(
                clockDialColor = Palette.SurfaceHigh,
                clockDialSelectedContentColor = Palette.Surface,
                clockDialUnselectedContentColor = Palette.TextPrimary,
                selectorColor = Palette.Primary,
                timeSelectorSelectedContainerColor = Palette.PrimarySoft,
                timeSelectorSelectedContentColor = Palette.PrimaryText,
                timeSelectorUnselectedContainerColor = Palette.SurfaceHigh,
                timeSelectorUnselectedContentColor = Palette.TextPrimary,
                periodSelectorSelectedContainerColor = Palette.PrimarySoft,
                periodSelectorSelectedContentColor = Palette.PrimaryText,
                periodSelectorUnselectedContentColor = Palette.TextSecondary,
                periodSelectorBorderColor = Palette.Outline,
            ),
        )
    }
}

/** Número con botones − y +. */
@Composable
fun Stepper(label: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RoundButton("−", value > range.first) { onChange(value - 1) }
        Text("$value $unit", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(84.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        RoundButton("+", value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun RoundButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .background(if (enabled) Palette.PrimarySoft else Palette.SurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.titleLarge, color = if (enabled) Palette.PrimaryText else Palette.TextSecondary) }
}

/** Campo de fecha (inicio del último periodo). [value] en milisegundos o null. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: Long?, onChange: (Long?) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).border(1.5.dp, Palette.Outline, shape).clickable { open = true }.padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            value?.let { DateFormat.getLongDateFormat(context).format(Date(it)) } ?: "Elegir fecha",
            style = MaterialTheme.typography.titleMedium,
            color = if (value != null) Palette.TextPrimary else Palette.PrimaryText,
        )
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.let(::localToUtcMidnight),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(state.selectedDateMillis?.let(::utcMidnightToLocalNoon))
                    open = false
                }) { Text("Listo", color = Palette.PrimaryText) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar", color = Palette.TextSecondary) } },
        ) { DatePicker(state = state) }
    }
}

/** El selector de fecha trabaja en UTC; se guarda el mediodía local de ese día. */
private fun utcMidnightToLocalNoon(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
    }.timeInMillis
}

private fun localToUtcMidnight(local: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SpacerS() = Spacer(Modifier.height(12.dp))
