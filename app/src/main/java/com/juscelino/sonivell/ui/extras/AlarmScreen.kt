package com.juscelino.sonivell.ui.extras

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.juscelino.sonivell.R
import com.juscelino.sonivell.data.Playlist
import com.juscelino.sonivell.system.AlarmConfig
import com.juscelino.sonivell.system.AlarmSource
import com.juscelino.sonivell.system.MusicAlarm
import com.juscelino.sonivell.ui.components.IconBtn
import com.juscelino.sonivell.ui.theme.SonivellTheme
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Despertador com música: hora, dias, o que toca, volume subindo e parada automática. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(playlists: List<Playlist>, contentPadding: PaddingValues, onBack: () -> Unit) {
    val c = SonivellTheme.colors
    val context = LocalContext.current
    var config by remember { mutableStateOf(MusicAlarm.load(context)) }
    var canSchedule by remember { mutableStateOf(MusicAlarm.canSchedule(context)) }
    val time = rememberTimePickerState(initialHour = config.hour, initialMinute = config.minute, is24Hour = true)

    fun update(next: AlarmConfig) {
        config = next
        MusicAlarm.save(context, next)
        canSchedule = MusicAlarm.canSchedule(context)
    }
    LaunchedEffect(time) {
        snapshotFlow { time.hour to time.minute }.collect { (h, m) ->
            if (h != config.hour || m != config.minute) update(config.copy(hour = h, minute = m))
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp, end = 20.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconBtn(SonivellTheme.icons.back, stringResource(R.string.cd_back), onBack, background = c.surface)
            Text(stringResource(R.string.alarm_title), style = MaterialTheme.typography.headlineSmall, color = c.textPrimary, modifier = Modifier.weight(1f))
            Switch(
                checked = config.enabled,
                onCheckedChange = { update(config.copy(enabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent),
            )
        }
        val next = MusicAlarm.nextTrigger(config)
        Text(
            if (next == null) stringResource(R.string.alarm_off)
            else stringResource(R.string.alarm_next, DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT).format(Date(next))),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
        )
        if (config.enabled && !canSchedule) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface).clickable {
                    runCatching { context.startActivity(MusicAlarm.permissionIntent()) }
                }.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.alarm_permission), style = MaterialTheme.typography.titleSmall, color = c.textPrimary)
                Text(stringResource(R.string.alarm_permission_hint), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state = time,
                colors = TimePickerDefaults.colors(
                    clockDialColor = c.surface,
                    selectorColor = c.accent,
                    timeSelectorSelectedContainerColor = c.accent,
                    timeSelectorSelectedContentColor = c.onAccent,
                    timeSelectorUnselectedContainerColor = c.surface,
                    timeSelectorUnselectedContentColor = c.textPrimary,
                ),
            )
        }

        Label(stringResource(R.string.alarm_days))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val names = weekdayInitials()
            (1..7).forEach { day ->
                val on = day in config.days
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (on) c.accent else c.surface)
                        .clickable(role = Role.Checkbox) { update(config.copy(days = if (on) config.days - day else config.days + day)) }
                        .semantics { selected = on },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(names[day - 1], style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = if (on) c.onAccent else c.textSecondary)
                }
            }
        }

        Label(stringResource(R.string.alarm_wake_with))
        Option(stringResource(R.string.source_liked), config.source == AlarmSource.Curtidas) { update(config.copy(source = AlarmSource.Curtidas)) }
        Option(stringResource(R.string.alarm_shuffle_library), config.source == AlarmSource.Aleatorio) { update(config.copy(source = AlarmSource.Aleatorio)) }
        playlists.forEach { p ->
            Option(p.name, config.source == AlarmSource.Playlist && config.playlistId == p.id) {
                update(config.copy(source = AlarmSource.Playlist, playlistId = p.id))
            }
        }

        Label(stringResource(R.string.alarm_ramp))
        Chips(listOf(0, 60, 120, 300), config.rampSec, { if (it == 0) stringResource(R.string.alarm_ramp_off) else stringResource(R.string.alarm_minutes, it / 60) }) {
            update(config.copy(rampSec = it))
        }
        Label(stringResource(R.string.alarm_stop))
        Chips(listOf(0, 15, 30, 60), config.stopMin, { if (it == 0) stringResource(R.string.alarm_stop_never) else stringResource(R.string.alarm_minutes, it) }) {
            update(config.copy(stopMin = it))
        }
    }
}

@Composable
private fun weekdayInitials(): List<String> {
    val cal = Calendar.getInstance()
    return (1..7).map { d ->
        cal.set(Calendar.DAY_OF_WEEK, d)
        (cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, java.util.Locale.getDefault()) ?: "?").take(1).uppercase()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = SonivellTheme.colors.textSecondary)
}

@Composable
private fun Option(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = SonivellTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(c.surface)
            .border(if (selected) 2.dp else 0.dp, if (selected) c.accent else c.surface, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = c.textPrimary)
    }
}

@Composable
private fun Chips(values: List<Int>, selected: Int, label: @Composable (Int) -> String, onPick: (Int) -> Unit) {
    val c = SonivellTheme.colors
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v ->
            val on = v == selected
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) c.accent else c.surface)
                    .clickable(role = Role.RadioButton) { onPick(v) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(v), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (on) c.onAccent else c.textTertiary)
            }
        }
    }
}
