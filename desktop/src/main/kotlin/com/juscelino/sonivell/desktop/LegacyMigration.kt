package com.juscelino.sonivell.desktop

import com.juscelino.sonivell.compat.AppContext
import com.juscelino.sonivell.compat.SharedPreferences
import com.juscelino.sonivell.data.ListenEvent
import com.juscelino.sonivell.data.SettingsStore
import com.juscelino.sonivell.ui.theme.AccentChoice
import com.juscelino.sonivell.ui.theme.DarkBackground
import com.juscelino.sonivell.ui.theme.ThemeMode
import java.io.File
import java.util.Properties

/**
 * Até a 1.3.0 o Sonivell para PC guardava ajustes, curtidas e histórico no formato dele
 * (settings.properties, favorites.txt, history.tsv). Agora usa os mesmos arquivos do app Android:
 * na primeira abertura, o que existia passa para o formato novo (os arquivos antigos ficam).
 */
object LegacyMigration {
    private const val KEY_DONE = "desktop_migrated_v2"

    fun run() {
        val prefs = SharedPreferences.named(SettingsStore.PREFS)
        if (prefs.getBoolean(KEY_DONE, false)) return
        runCatching { migrateSettings(prefs) }
        runCatching { migrateFavorites(prefs) }
        runCatching { migrateHistory() }
        prefs.edit().putBoolean(KEY_DONE, true).commit()
    }

    private fun migrateSettings(prefs: SharedPreferences) {
        val file = File(Dirs.config, "settings.properties")
        if (!file.exists()) return
        val p = Properties().apply { file.reader(Charsets.UTF_8).use { load(it) } }
        val e = prefs.edit()
        p.getProperty("theme")?.let { name -> ThemeMode.entries.firstOrNull { it.name == name }?.let { e.putInt(SettingsStore.KEY_THEME, it.ordinal) } }
        p.getProperty("accent")?.let { name -> AccentChoice.entries.firstOrNull { it.name == name }?.let { e.putInt(SettingsStore.KEY_ACCENT, it.ordinal) } }
        p.getProperty("darkBg")?.let { name -> DarkBackground.entries.firstOrNull { it.name == name }?.let { e.putString(SettingsStore.KEY_DARK_BACKGROUND, it.name) } }
        when (p.getProperty("layout")) {
            "ESSENCIAL" -> e.putString(SettingsStore.KEY_UI_STYLE, "ESSENCIAL")
            "ONDAS" -> e.putString(SettingsStore.KEY_UI_STYLE, "ONDAS")
        }
        if (p.getProperty("tinted") == "true") e.putBoolean(SettingsStore.KEY_TINTED_BACKGROUND, true)
        if (p.getProperty("smart") == "true") e.putBoolean(SettingsStore.KEY_SMART_MODE, true)
        p.getProperty("artistVariety")?.toIntOrNull()?.let { e.putInt(SettingsStore.KEY_ARTIST_VARIETY, it.coerceIn(0, 4)) }
        p.getProperty("volume")?.toFloatOrNull()?.let { e.putFloat("volume", it.coerceIn(0f, 1f)) }
        p.getProperty("musicDirs")?.split('\u0001')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.let {
            e.putStringSet(com.juscelino.sonivell.MainViewModel.KEY_MUSIC_DIRS, it.toSet())
        }
        e.commit()
        if (p.getProperty("compact") == "true") SharedPreferences.named("desktop").edit().putBoolean("compact", true).commit()
    }

    private fun migrateFavorites(prefs: SharedPreferences) {
        val file = File(Dirs.data, "favorites.txt")
        if (!file.exists() || prefs.contains("favorites")) return
        val ids = file.readLines().mapNotNull { it.trim().toLongOrNull()?.toString() }.toSet()
        if (ids.isNotEmpty()) prefs.edit().putStringSet("favorites", ids).commit()
    }

    /** history.tsv (faixa, início, ouvido, completa) → o histórico do app (mesmas regras de pulo). */
    private fun migrateHistory() {
        val old = File(Dirs.data, "history.tsv")
        val target = File(AppContext.filesDir, "historico.tsv")
        if (!old.exists() || target.exists()) return
        val lines = old.readLines().mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 4) return@mapNotNull null
            val listened = f[2].toLongOrNull() ?: return@mapNotNull null
            val completed = f[3] == "1"
            ListenEvent(
                trackId = f[0].toLongOrNull() ?: return@mapNotNull null,
                startedAtMs = f[1].toLongOrNull() ?: return@mapNotNull null,
                playedMs = listened,
                durationMs = 0,
                completed = completed,
                skipped = !completed && listened < ListenEvent.SKIP_THRESHOLD_MS,
            ).toLine()
        }
        if (lines.isNotEmpty()) target.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
