import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory

// Sonivell para PC (Linux Debian/Ubuntu): Kotlin + Compose Desktop, no mesmo repositório do app.
plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    // Ícones das telas trazidas do app Android (Icons.Rounded.*), navegação e lifecycle multiplataforma.
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.1")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.9.6")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.6")
    implementation(project(":desktop-smart"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    // MPRIS (teclas de mídia, controle de música do painel) pelo D-Bus da sessão.
    implementation("com.github.hypfvieh:dbus-java-core:5.1.1")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
}

// ---------------------------------------------------------------------------------------------
// Textos: a fonte única são os strings.xml (inglês em values/, português em values-pt-rBR/).
// Os do app Android são lidos daqui; os exclusivos do PC ficam em desktop/src/main/res.
// Este passo converte tudo em .properties para o Java ler em tempo de execução.
// ---------------------------------------------------------------------------------------------
val generatedStrings = layout.buildDirectory.dir("generated/strings")

/** Converte o texto de um <string>/<item> do Android (aspas, \n, \t, \', \uXXXX) no texto final. */
fun unescapeAndroid(raw: String): String {
    val quoted = raw.trim().length >= 2 && raw.trim().startsWith("\"") && raw.trim().endsWith("\"")
    val text = if (quoted) raw.trim().substring(1, raw.trim().length - 1) else raw.trim().replace(Regex("\\s+"), " ")
    val sb = StringBuilder()
    var k = 0
    while (k < text.length) {
        val c = text[k]
        if (c == '\\' && k + 1 < text.length) {
            when (val n = text[k + 1]) {
                'n' -> { sb.append('\n'); k += 2 }
                't' -> { sb.append('\t'); k += 2 }
                'u' -> if (k + 5 < text.length) {
                    sb.append(text.substring(k + 2, k + 6).toInt(16).toChar()); k += 6
                } else { sb.append(n); k += 2 }
                else -> { sb.append(n); k += 2 }
            }
        } else { sb.append(c); k++ }
    }
    return sb.toString()
}

/** Textos de um strings.xml: os <string> pelo nome e os <plurals> como "nome#quantidade". */
fun readAndroidStrings(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val out = LinkedHashMap<String, String>()
    val nodes = doc.getElementsByTagName("string")
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        val attrs = node.attributes
        if (attrs.getNamedItem("translatable")?.nodeValue == "false" && file.parentFile.name != "values") continue
        out[attrs.getNamedItem("name").nodeValue] = unescapeAndroid(node.textContent)
    }
    val plurals = doc.getElementsByTagName("plurals")
    for (i in 0 until plurals.length) {
        val node = plurals.item(i)
        val name = node.attributes.getNamedItem("name").nodeValue
        val items = node.childNodes
        for (j in 0 until items.length) {
            val item = items.item(j)
            if (item.nodeName != "item") continue
            val quantity = item.attributes.getNamedItem("quantity").nodeValue
            out["$name#$quantity"] = unescapeAndroid(item.textContent)
        }
    }
    return out
}

val generatedR = layout.buildDirectory.dir("generated/r")

val generateStrings = tasks.register("generateStrings") {
    val appRes = rootProject.file("app/src/main/res")
    val deskRes = file("src/main/res")
    inputs.dir(appRes)
    inputs.dir(deskRes)
    inputs.file(rootProject.file("version.properties"))
    outputs.dir(generatedStrings)
    outputs.dir(generatedR)
    doLast {
        val outDir = generatedStrings.get().asFile.also { it.mkdirs() }
        val names = sortedSetOf<String>()
        for ((suffix, folder) in listOf("en" to "values", "pt" to "values-pt-rBR")) {
            val merged = LinkedHashMap<String, String>()
            merged.putAll(readAndroidStrings(File(appRes, "$folder/strings.xml")))
            merged.putAll(readAndroidStrings(File(deskRes, "$folder/strings.xml")))
            names.addAll(merged.keys)
            val props = Properties()
            merged.forEach { (k, v) -> props.setProperty(k, v) }
            File(outDir, "strings_$suffix.properties").writer(Charsets.UTF_8).use { props.store(it, null) }
        }
        // R.string e R.plurals como no Android: cada nome vira um Int, e o Int volta ao nome em tempo
        // de execução (R.names). Assim as telas trazidas do app usam stringResource(R.string.x) sem mudança.
        val strings = names.filter { '#' !in it }
        val plurals = names.filter { '#' in it }.map { it.substringBefore('#') }.distinct()
        val sb = StringBuilder()
        sb.append("// Gerado pelo desktop/build.gradle.kts (generateStrings). Não editar.\n")
        sb.append("package com.juscelino.sonivell\n\n")
        sb.append("@Suppress(\"ObjectPropertyName\", \"ConstPropertyName\", \"ClassName\")\n")
        sb.append("object R {\n")
        sb.append("    object string {\n")
        strings.forEachIndexed { i, n -> sb.append("        const val `").append(n).append("`: Int = ").append(i + 1).append('\n') }
        sb.append("    }\n    object plurals {\n")
        plurals.forEachIndexed { i, n -> sb.append("        const val `").append(n).append("`: Int = ").append(100_000 + i + 1).append('\n') }
        sb.append("    }\n    object font {\n        const val manrope: Int = 900_001\n    }\n")
        sb.append("    /** Nome de cada id (strings a partir de 1, plurais a partir de 100.001). */\n")
        sb.append("    val names: Map<Int, String> = buildMap {\n")
        sb.append("        STRINGS.split('\\n').forEachIndexed { i, n -> put(i + 1, n) }\n")
        sb.append("        PLURALS.split('\\n').forEachIndexed { i, n -> put(100_000 + i + 1, n) }\n")
        sb.append("    }\n")
        sb.append("    private const val STRINGS = \"").append(strings.joinToString("\\n")).append("\"\n")
        sb.append("    private const val PLURALS = \"").append(plurals.joinToString("\\n")).append("\"\n")
        sb.append("}\n")
        // Versão única (version.properties) para a tela Sobre.
        val version = Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }.getProperty("versionName")
        File(outDir, "version.txt").writeText(version)
        val rDir = generatedR.get().asFile.resolve("com/juscelino/sonivell").also { it.mkdirs() }
        File(rDir, "R.kt").writeText(sb.toString())
    }
}
sourceSets["main"].resources.srcDir(generatedStrings)
kotlin.sourceSets["main"].kotlin.srcDir(generatedR)
tasks.named("processResources") { dependsOn(generateStrings) }
tasks.named("compileKotlin") { dependsOn(generateStrings) }

tasks.test { useJUnitPlatform(); jvmArgs("-Duser.language=pt", "-Duser.country=BR") }

// Versão única do Sonivell (version.properties na raiz), a mesma do app Android.
val sonivellVersionName: String = Properties()
    .apply { rootProject.file("version.properties").inputStream().use { load(it) } }
    .getProperty("versionName")

compose.desktop {
    application {
        mainClass = "com.juscelino.sonivell.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m")
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "sonivell"
            packageVersion = sonivellVersionName
            description = "Sonivell: player de música moderno, leve e com identidade própria"
            vendor = "Sonivell"
            includeAllModules = true
            linux {
                iconFile.set(project.file("packaging/sonivell.png"))
                debMaintainer = "sonivell@users.noreply.github.com"
                menuGroup = "Sound & Video"
                appCategory = "AudioVideo"
                shortcut = true
            }
        }
    }
}
