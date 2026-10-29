package com.juscelino.sonivell.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.juscelino.sonivell.MainActivity
import com.juscelino.sonivell.data.MusicRepository
import com.juscelino.sonivell.data.PlaylistStore
import com.juscelino.sonivell.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

/** O que tocar ao despertar. */
enum class AlarmSource { Curtidas, Aleatorio, Playlist }

/** Despertador com música: hora, dias, o que toca, volume subindo e parada automática. */
data class AlarmConfig(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    /** Dias da semana no formato do Calendar (1 = domingo … 7 = sábado); vazio = só uma vez. */
    val days: Set<Int> = setOf(2, 3, 4, 5, 6),
    val source: AlarmSource = AlarmSource.Curtidas,
    val playlistId: Long = 0L,
    /** Segundos para o volume chegar ao normal (0 = já começa no normal). */
    val rampSec: Int = 120,
    /** Para de tocar sozinho depois disso, em minutos (0 = não para). */
    val stopMin: Int = 30,
)

object MusicAlarm {
    private const val PREFS = "sonivell_alarm"
    private const val ACTION_FIRE = "com.juscelino.sonivell.alarm.FIRE"

    fun load(context: Context): AlarmConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AlarmConfig(
            enabled = p.getBoolean("enabled", false),
            hour = p.getInt("hour", 7),
            minute = p.getInt("minute", 0),
            days = p.getStringSet("days", null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(2, 3, 4, 5, 6),
            source = AlarmSource.entries.firstOrNull { it.name == p.getString("source", null) } ?: AlarmSource.Curtidas,
            playlistId = p.getLong("playlist", 0L),
            rampSec = p.getInt("ramp", 120),
            stopMin = p.getInt("stop", 30),
        )
    }

    fun save(context: Context, c: AlarmConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", c.enabled)
            .putInt("hour", c.hour)
            .putInt("minute", c.minute)
            .putStringSet("days", c.days.map { it.toString() }.toSet())
            .putString("source", c.source.name)
            .putLong("playlist", c.playlistId)
            .putInt("ramp", c.rampSec)
            .putInt("stop", c.stopMin)
            .apply()
        schedule(context, c)
    }

    /** No Android 12+ alarmes exatos precisam de permissão que a pessoa pode negar. */
    fun canSchedule(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun permissionIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Próximo disparo em milissegundos, ou null se desligado. */
    fun nextTrigger(c: AlarmConfig, now: Long = System.currentTimeMillis()): Long? {
        if (!c.enabled) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, c.hour)
            set(Calendar.MINUTE, c.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= now) cal.add(Calendar.DAY_OF_YEAR, 1)
        if (c.days.isNotEmpty()) {
            repeat(7) { if (cal.get(Calendar.DAY_OF_WEEK) !in c.days) cal.add(Calendar.DAY_OF_YEAR, 1) }
        }
        return cal.timeInMillis
    }

    fun schedule(context: Context, c: AlarmConfig = load(context)) {
        val am = context.getSystemService(AlarmManager::class.java)
        val fire = firePending(context)
        am.cancel(fire)
        val at = nextTrigger(c) ?: return
        if (!canSchedule(context)) return
        val show = PendingIntent.getActivity(
            context, 41, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire) }
    }

    private fun firePending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 40, Intent(context, AlarmReceiver::class.java).setAction(ACTION_FIRE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Monta a fila do despertador. */
    suspend fun trackIds(context: Context, c: AlarmConfig): List<Long> = withContext(Dispatchers.IO) {
        when (c.source) {
            AlarmSource.Curtidas -> context.getSharedPreferences("sonivell", Context.MODE_PRIVATE)
                .getStringSet("favorites", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.shuffled()
            AlarmSource.Playlist -> {
                val store = PlaylistStore(context)
                store.load()
                store.playlists.value.firstOrNull { it.id == c.playlistId }?.trackIds.orEmpty()
            }
            AlarmSource.Aleatorio -> emptyList()
        }.ifEmpty { MusicRepository(context).loadTracks().shuffled().take(60).map { it.id } }
    }

    /** Hora do despertador: toca, faz o volume subir e agenda o próximo. */
    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val app = context.applicationContext
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                    schedule(app)
                    return
                }
                ACTION_FIRE -> Unit
                else -> return
            }
            val pending = goAsync()
            CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
                try {
                    val config = load(app)
                    // Só uma vez: desliga depois de tocar.
                    if (config.days.isEmpty()) save(app, config.copy(enabled = false)) else schedule(app, config)
                    val ids = trackIds(app, config)
                    withTimeoutOrNull(8_000) {
                        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
                        val controller = MediaController.Builder(app, token).buildAsync().await()
                        val args = Bundle().apply {
                            putLongArray(PlaybackService.EXTRA_IDS, ids.toLongArray())
                            putInt(PlaybackService.EXTRA_RAMP_SEC, config.rampSec)
                            putInt(PlaybackService.EXTRA_STOP_MIN, config.stopMin)
                        }
                        controller.sendCustomCommand(SessionCommand(PlaybackService.ALARM_COMMAND, Bundle.EMPTY), args).await()
                        controller.release()
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }
}
