package com.juscelino.sonivell.compat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.juscelino.sonivell.R
import com.juscelino.sonivell.desktop.Dirs
import com.juscelino.sonivell.desktop.Str
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/*
 * Peças do Android que as telas e os dados do app usam, reescritas para o PC. Com elas, o código
 * trazido do app (ui/, data/) compila no desktop quase sem mudança: stringResource(R.string.x),
 * Context.filesDir, SharedPreferences, Uri…
 */

/** Marca de id de texto, como no androidx.annotation. */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.TYPE)
annotation class StringRes

@Retention(AnnotationRetention.SOURCE)
annotation class PluralsRes

/** Textos pelo id do R gerado (os mesmos strings.xml do app Android). */
object Texts {
    private val portuguese: Boolean get() = Locale.getDefault().language == "pt"

    fun name(id: Int): String = R.names[id] ?: "?$id"

    fun get(id: Int): String = Str[name(id)]

    fun format(id: Int, args: Array<out Any?>): String {
        val pattern = get(id)
        if (args.isEmpty()) return pattern
        return runCatching { String.format(Locale.getDefault(), pattern, *args) }.getOrDefault(pattern)
    }

    /** Regra de plural do Android (CLDR): em português 0 e 1 são "one"; em inglês só o 1. */
    fun quantity(id: Int, count: Int, args: Array<out Any?>): String {
        val base = name(id)
        val one = if (portuguese) count == 0 || count == 1 else count == 1
        val key = if (one) "$base#one" else "$base#other"
        val pattern = Str.raw(key) ?: Str.raw("$base#other") ?: Str.raw("$base#one") ?: base
        return runCatching { String.format(Locale.getDefault(), pattern, *args) }.getOrDefault(pattern)
    }
}

fun stringResource(id: Int): String = Texts.get(id)
fun stringResource(id: Int, vararg formatArgs: Any?): String = Texts.format(id, formatArgs)
fun pluralStringResource(id: Int, count: Int): String = Texts.quantity(id, count, emptyArray())
fun pluralStringResource(id: Int, count: Int, vararg formatArgs: Any?): String = Texts.quantity(id, count, formatArgs)

class Resources {
    fun getString(id: Int): String = Texts.get(id)
    fun getString(id: Int, vararg formatArgs: Any?): String = Texts.format(id, formatArgs)
    fun getQuantityString(id: Int, quantity: Int): String = Texts.quantity(id, quantity, emptyArray())
    fun getQuantityString(id: Int, quantity: Int, vararg formatArgs: Any?): String = Texts.quantity(id, quantity, formatArgs)
}

/** O "Context" do PC: pastas do usuário (XDG), textos e preferências. */
open class Context {
    val resources: Resources = Resources()
    open val filesDir: File get() = File(Dirs.data, "files").also { it.mkdirs() }
    open val cacheDir: File get() = Dirs.cache
    val applicationContext: Context get() = this
    val packageName: String get() = "com.juscelino.sonivell"

    fun getString(id: Int): String = Texts.get(id)
    fun getString(id: Int, vararg formatArgs: Any?): String = Texts.format(id, formatArgs)

    @Suppress("UNCHECKED_CAST")
    fun <T> getSystemService(type: Class<T>): T = when (type) {
        AudioManager::class.java -> AudioManager() as T
        else -> error("Serviço indisponível no PC: $type")
    }

    fun getSharedPreferences(name: String, @Suppress("UNUSED_PARAMETER") mode: Int): SharedPreferences = SharedPreferences.named(name)

    companion object {
        const val MODE_PRIVATE = 0
    }
}

/** O app inteiro usa um Context só. */
object AppContext : Context()

val LocalContext = staticCompositionLocalOf<Context> { AppContext }

/** No PC não há versões do Android: tudo que depende de uma versão mínima fica desligado. */
object Build {
    object VERSION {
        const val SDK_INT: Int = 0
    }

    object VERSION_CODES {
        const val O = 26
        const val P = 28
        const val Q = 29
        const val R = 30
        const val S = 31
        const val TIRAMISU = 33
    }
}

/** Endereço de um arquivo (no PC, sempre um caminho). */
class Uri private constructor(val path: String) {
    override fun toString(): String = path
    override fun equals(other: Any?): Boolean = other is Uri && other.path == path
    override fun hashCode(): Int = path.hashCode()
    val lastPathSegment: String? get() = path.trimEnd('/').substringAfterLast('/').ifEmpty { null }

    companion object {
        val EMPTY = Uri("")
        fun parse(text: String): Uri = Uri(text.removePrefix("file://"))
        fun fromFile(file: File): Uri = Uri(file.path)
        fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")
        fun decode(text: String): String = URLDecoder.decode(text, Charsets.UTF_8)
    }
}

fun String.toUri(): Uri = Uri.parse(this)

/** Voltar (Esc ou o botão de voltar do mouse): a tela mais recente que pediu tem a vez. */
object BackDispatcher {
    private val handlers = ArrayList<Pair<Any, () -> Boolean>>()

    fun register(owner: Any, handler: () -> Boolean) {
        synchronized(handlers) { handlers.removeAll { it.first === owner }; handlers.add(owner to handler) }
    }

    fun unregister(owner: Any) {
        synchronized(handlers) { handlers.removeAll { it.first === owner } }
    }

    /** true se alguma tela tratou o voltar. */
    fun dispatch(): Boolean {
        val list = synchronized(handlers) { handlers.toList() }
        for ((_, h) in list.asReversed()) if (h()) return true
        return false
    }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val owner = androidx.compose.runtime.remember { Any() }
    val current = androidx.compose.runtime.rememberUpdatedState(onBack)
    val isEnabled = androidx.compose.runtime.rememberUpdatedState(enabled)
    androidx.compose.runtime.DisposableEffect(owner) {
        BackDispatcher.register(owner) { if (isEnabled.value) { current.value(); true } else false }
        onDispose { BackDispatcher.unregister(owner) }
    }
}

/** Volume do player do PC (0–100), com a interface do AudioManager do Android. */
class AudioManager {
    fun getStreamMaxVolume(@Suppress("UNUSED_PARAMETER") stream: Int): Int = 100
    fun getStreamVolume(@Suppress("UNUSED_PARAMETER") stream: Int): Int = (com.juscelino.sonivell.playback.PlayerVolume.value * 100).toInt()
    fun setStreamVolume(@Suppress("UNUSED_PARAMETER") stream: Int, volume: Int, @Suppress("UNUSED_PARAMETER") flags: Int) {
        com.juscelino.sonivell.playback.PlayerVolume.set(volume / 100f)
    }

    companion object {
        const val STREAM_MUSIC = 3
    }
}
