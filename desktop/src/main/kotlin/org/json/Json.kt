package org.json

/*
 * O pedaço do org.json (que vem embutido no Android) que os dados do app usam: playlists, playlists
 * inteligentes e correções de tags. Mesmos nomes e o mesmo formato de texto, para os arquivos do
 * celular e do PC serem iguais (e o backup servir nos dois).
 */

class JSONException(message: String) : RuntimeException(message)

class JSONObject() {
    private val map = LinkedHashMap<String, Any?>()

    constructor(text: String) : this() {
        val v = Parser(text).value()
        if (v !is JSONObject) throw JSONException("Não é um objeto JSON")
        map.putAll(v.map)
    }

    fun put(key: String, value: Any?): JSONObject = apply { map[key] = wrap(value) }
    fun has(key: String): Boolean = map.containsKey(key)
    fun keys(): Iterator<String> = map.keys.toList().iterator()
    fun length(): Int = map.size
    fun remove(key: String): Any? = map.remove(key)
    fun isNull(key: String): Boolean = map[key] == null

    private fun req(key: String): Any = map[key] ?: throw JSONException("Sem a chave $key")

    fun get(key: String): Any = req(key)
    fun getString(key: String): String = req(key).toString()
    fun getInt(key: String): Int = num(req(key))?.toInt() ?: throw JSONException("$key não é número")
    fun getLong(key: String): Long = num(req(key))?.toLong() ?: throw JSONException("$key não é número")
    fun getDouble(key: String): Double = num(req(key))?.toDouble() ?: throw JSONException("$key não é número")
    fun getBoolean(key: String): Boolean = bool(req(key)) ?: throw JSONException("$key não é booleano")
    fun getJSONArray(key: String): JSONArray = req(key) as? JSONArray ?: throw JSONException("$key não é lista")
    fun getJSONObject(key: String): JSONObject = req(key) as? JSONObject ?: throw JSONException("$key não é objeto")

    fun opt(key: String): Any? = map[key]
    fun optString(key: String, fallback: String = ""): String = map[key]?.toString() ?: fallback
    fun optInt(key: String, fallback: Int = 0): Int = num(map[key])?.toInt() ?: fallback
    fun optLong(key: String, fallback: Long = 0L): Long = num(map[key])?.toLong() ?: fallback
    fun optDouble(key: String, fallback: Double = Double.NaN): Double = num(map[key])?.toDouble() ?: fallback
    fun optBoolean(key: String, fallback: Boolean = false): Boolean = bool(map[key]) ?: fallback
    fun optJSONArray(key: String): JSONArray? = map[key] as? JSONArray
    fun optJSONObject(key: String): JSONObject? = map[key] as? JSONObject

    override fun toString(): String = buildString {
        append('{')
        map.entries.forEachIndexed { i, (k, v) ->
            if (i > 0) append(',')
            quote(k, this); append(':'); write(v, this)
        }
        append('}')
    }
}

class JSONArray() {
    private val list = ArrayList<Any?>()

    constructor(text: String) : this() {
        val v = Parser(text).value()
        if (v !is JSONArray) throw JSONException("Não é uma lista JSON")
        list.addAll(v.list)
    }

    constructor(items: Collection<*>) : this() {
        items.forEach { list.add(wrap(it)) }
    }

    fun put(value: Any?): JSONArray = apply { list.add(wrap(value)) }
    fun length(): Int = list.size

    private fun req(i: Int): Any = list.getOrNull(i) ?: throw JSONException("Sem o item $i")

    fun get(i: Int): Any = req(i)
    fun getString(i: Int): String = req(i).toString()
    fun getInt(i: Int): Int = num(req(i))?.toInt() ?: throw JSONException("item $i não é número")
    fun getLong(i: Int): Long = num(req(i))?.toLong() ?: throw JSONException("item $i não é número")
    fun getBoolean(i: Int): Boolean = bool(req(i)) ?: throw JSONException("item $i não é booleano")
    fun getJSONObject(i: Int): JSONObject = req(i) as? JSONObject ?: throw JSONException("item $i não é objeto")
    fun getJSONArray(i: Int): JSONArray = req(i) as? JSONArray ?: throw JSONException("item $i não é lista")
    fun opt(i: Int): Any? = list.getOrNull(i)
    fun optString(i: Int, fallback: String = ""): String = list.getOrNull(i)?.toString() ?: fallback
    fun optLong(i: Int, fallback: Long = 0L): Long = num(list.getOrNull(i))?.toLong() ?: fallback
    fun optInt(i: Int, fallback: Int = 0): Int = num(list.getOrNull(i))?.toInt() ?: fallback
    fun optJSONObject(i: Int): JSONObject? = list.getOrNull(i) as? JSONObject

    override fun toString(): String = buildString {
        append('[')
        list.forEachIndexed { i, v -> if (i > 0) append(','); write(v, this) }
        append(']')
    }
}

private fun wrap(v: Any?): Any? = when (v) {
    null, is String, is Number, is Boolean, is JSONObject, is JSONArray -> v
    is Collection<*> -> JSONArray(v)
    is LongArray -> JSONArray(v.toList())
    is IntArray -> JSONArray(v.toList())
    else -> v.toString()
}

private fun num(v: Any?): Number? = when (v) {
    is Number -> v
    is String -> v.toLongOrNull() ?: v.toDoubleOrNull()
    else -> null
}

private fun bool(v: Any?): Boolean? = when (v) {
    is Boolean -> v
    is String -> v.toBooleanStrictOrNull()
    else -> null
}

private fun quote(s: String, out: StringBuilder) {
    out.append('"')
    for (c in s) when (c) {
        '"' -> out.append("\\\"")
        '\\' -> out.append("\\\\")
        '\n' -> out.append("\\n")
        '\r' -> out.append("\\r")
        '\t' -> out.append("\\t")
        '/' -> out.append("\\/")
        else -> if (c < ' ') out.append(String.format("\\u%04x", c.code)) else out.append(c)
    }
    out.append('"')
}

private fun write(v: Any?, out: StringBuilder) {
    when (v) {
        null -> out.append("null")
        is String -> quote(v, out)
        is Double -> out.append(if (v == Math.floor(v) && !v.isInfinite() && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString())
        is Float -> write(v.toDouble(), out)
        is Number, is Boolean -> out.append(v.toString())
        else -> out.append(v.toString())
    }
}

private class Parser(private val s: String) {
    private var i = 0

    fun value(): Any? {
        ws()
        if (i >= s.length) throw JSONException("Fim inesperado")
        return when (val c = s[i]) {
            '{' -> obj()
            '[' -> arr()
            '"' -> str()
            't' -> lit("true", true)
            'f' -> lit("false", false)
            'n' -> lit("null", null)
            else -> if (c == '-' || c.isDigit()) number() else throw JSONException("Caractere inesperado '$c' em $i")
        }
    }

    private fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

    private fun lit(word: String, v: Any?): Any? {
        if (!s.startsWith(word, i)) throw JSONException("Esperava $word em $i")
        i += word.length
        return v
    }

    private fun number(): Number {
        val start = i
        if (s[i] == '-') i++
        while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
        val t = s.substring(start, i)
        return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw JSONException("Número inválido $t")
    }

    private fun str(): String {
        i++ // "
        val sb = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (i >= s.length) break
                    when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                }
                else -> sb.append(c)
            }
        }
        throw JSONException("Texto sem fim")
    }

    private fun obj(): JSONObject {
        i++ // {
        val o = JSONObject()
        ws()
        if (i < s.length && s[i] == '}') { i++; return o }
        while (true) {
            ws()
            val k = str()
            ws()
            if (s.getOrNull(i) != ':') throw JSONException("Esperava ':' em $i")
            i++
            o.put(k, value())
            ws()
            when (s.getOrNull(i)) {
                ',' -> i++
                '}' -> { i++; return o }
                else -> throw JSONException("Esperava ',' ou '}' em $i")
            }
        }
    }

    private fun arr(): JSONArray {
        i++ // [
        val a = JSONArray()
        ws()
        if (i < s.length && s[i] == ']') { i++; return a }
        while (true) {
            a.put(value())
            ws()
            when (s.getOrNull(i)) {
                ',' -> i++
                ']' -> { i++; return a }
                else -> throw JSONException("Esperava ',' ou ']' em $i")
            }
        }
    }
}
