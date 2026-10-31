package com.juscelino.sonivell.system

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Lê o XML de preferências do Android (o formato de `shared_prefs/<nome>.xml`) para um mapa:
 * String, Int, Long, Float, Boolean e Set<String>. Usado ao restaurar o backup, para aplicar os
 * valores pelo próprio SharedPreferences em vez de trocar o arquivo por baixo dele (auditoria REL-04).
 * Sem dependência do Android (testado no JVM).
 */
object PrefsXml {
    fun parse(bytes: ByteArray): Map<String, Any> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isExpandEntityReferences = false
            // Sem DTD/entidades externas (XXE): o arquivo vem de um .zip que a pessoa escolheu.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
        require(root.tagName == "map") { "Não é um arquivo de preferências" }
        val out = LinkedHashMap<String, Any>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val e = children.item(i) as? Element ?: continue
            val name = e.getAttribute("name").takeIf { it.isNotEmpty() } ?: continue
            val value: Any = when (e.tagName) {
                "string" -> e.textContent
                "int" -> e.getAttribute("value").toInt()
                "long" -> e.getAttribute("value").toLong()
                "float" -> e.getAttribute("value").toFloat()
                "boolean" -> e.getAttribute("value").toBooleanStrict()
                "set" -> {
                    val items = e.getElementsByTagName("string")
                    (0 until items.length).map { items.item(it).textContent }.toSet()
                }
                else -> continue
            }
            out[name] = value
        }
        return out
    }
}
