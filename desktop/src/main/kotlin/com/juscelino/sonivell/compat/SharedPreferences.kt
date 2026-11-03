package com.juscelino.sonivell.compat

import com.juscelino.sonivell.desktop.Dirs
import com.juscelino.sonivell.desktop.writeAtomically
import com.juscelino.sonivell.system.PrefsXml
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Preferências do PC com a mesma interface do SharedPreferences do Android, gravadas no mesmo
 * formato (`shared_prefs/<nome>.xml`). Assim o código do app funciona igual e um backup feito no
 * celular pode ser restaurado no computador (e vice-versa).
 */
class SharedPreferences private constructor(private val file: File) {

    fun interface OnSharedPreferenceChangeListener {
        fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?)
    }

    private val values = ConcurrentHashMap<String, Any>()
    private val listeners = java.util.concurrent.CopyOnWriteArrayList<OnSharedPreferenceChangeListener>()

    init {
        reload()
    }

    /** Relê o arquivo (depois de restaurar um backup). */
    fun reload() {
        values.clear()
        if (file.exists()) runCatching { values.putAll(PrefsXml.parse(file.readBytes())) }
    }

    val all: Map<String, *> get() = HashMap(values)

    fun contains(key: String): Boolean = values.containsKey(key)

    fun getString(key: String, def: String?): String? = values[key] as? String ?: def
    fun getInt(key: String, def: Int): Int = (values[key] as? Number)?.toInt() ?: def
    fun getLong(key: String, def: Long): Long = (values[key] as? Number)?.toLong() ?: def
    fun getFloat(key: String, def: Float): Float = (values[key] as? Number)?.toFloat() ?: def
    fun getBoolean(key: String, def: Boolean): Boolean = values[key] as? Boolean ?: def

    @Suppress("UNCHECKED_CAST")
    fun getStringSet(key: String, def: Set<String>?): Set<String>? = (values[key] as? Set<String>)?.toSet() ?: def

    fun registerOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener) { listeners.add(l) }
    fun unregisterOnSharedPreferenceChangeListener(l: OnSharedPreferenceChangeListener) { listeners.remove(l) }

    fun edit(): Editor = Editor()

    inner class Editor {
        private val changes = LinkedHashMap<String, Any?>()
        private var clear = false

        fun putString(key: String, value: String?) = apply { changes[key] = value }
        fun putInt(key: String, value: Int) = apply { changes[key] = value }
        fun putLong(key: String, value: Long) = apply { changes[key] = value }
        fun putFloat(key: String, value: Float) = apply { changes[key] = value }
        fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        fun putStringSet(key: String, value: Set<String>?) = apply { changes[key] = value?.toSet() }
        fun remove(key: String) = apply { changes[key] = null }
        fun clear() = apply { clear = true }

        fun apply() { commitChanges(sync = false) }
        fun commit(): Boolean { commitChanges(sync = true); return true }

        private fun commitChanges(sync: Boolean) {
            if (clear) values.clear()
            for ((k, v) in changes) if (v == null) values.remove(k) else values[k] = v
            if (sync) write() else writer.execute(::write)
            val keys = if (clear) listOf<String?>(null) else changes.keys.toList()
            for (k in keys) for (l in listeners) runCatching { l.onSharedPreferenceChanged(this@SharedPreferences, k) }
        }
    }

    @Synchronized
    private fun write() {
        val sb = StringBuilder("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n")
        for ((k, v) in values.toSortedMap()) {
            val name = esc(k)
            when (v) {
                is String -> sb.append("    <string name=\"").append(name).append("\">").append(esc(v)).append("</string>\n")
                is Boolean -> sb.append("    <boolean name=\"").append(name).append("\" value=\"").append(v).append("\" />\n")
                is Int -> sb.append("    <int name=\"").append(name).append("\" value=\"").append(v).append("\" />\n")
                is Long -> sb.append("    <long name=\"").append(name).append("\" value=\"").append(v).append("\" />\n")
                is Float -> sb.append("    <float name=\"").append(name).append("\" value=\"").append(v).append("\" />\n")
                is Set<*> -> {
                    sb.append("    <set name=\"").append(name).append("\">\n")
                    v.forEach { sb.append("        <string>").append(esc(it.toString())).append("</string>\n") }
                    sb.append("    </set>\n")
                }
            }
        }
        sb.append("</map>\n")
        runCatching { writeAtomically(file, sb.toString()) }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "prefs").apply { isDaemon = true } }
        private val opened = ConcurrentHashMap<String, SharedPreferences>()

        val dir: File get() = File(Dirs.data, "shared_prefs").also { it.mkdirs() }

        fun named(name: String): SharedPreferences = opened.getOrPut(name) { SharedPreferences(File(dir, "$name.xml")) }

        /** Relê todas as preferências abertas (depois de restaurar um backup). */
        fun reloadAll() = opened.values.forEach { it.reload() }

        /** Garante que tudo foi gravado (antes de fechar o app ou fazer backup). */
        fun flush() {
            val done = java.util.concurrent.CountDownLatch(1)
            writer.execute { done.countDown() }
            done.await(3, java.util.concurrent.TimeUnit.SECONDS)
        }
    }
}
