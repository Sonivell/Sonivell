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

fun readAndroidStrings(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    val out = LinkedHashMap<String, String>()
    val nodes = doc.getElementsByTagName("string")
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        val attrs = node.attributes
        if (attrs.getNamedItem("translatable")?.nodeValue == "false") continue
        val name = attrs.getNamedItem("name").nodeValue
        var text = node.textContent
        val quoted = text.trim().length >= 2 && text.trim().startsWith("\"") && text.trim().endsWith("\"")
        text = if (quoted) text.trim().substring(1, text.trim().length - 1) else text.trim().replace(Regex("\\s+"), " ")
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
        out[name] = sb.toString()
    }
    return out
}

val generateStrings = tasks.register("generateStrings") {
    val appRes = rootProject.file("app/src/main/res")
    val deskRes = file("src/main/res")
    inputs.dir(appRes)
    inputs.dir(deskRes)
    outputs.dir(generatedStrings)
    doLast {
        val outDir = generatedStrings.get().asFile.also { it.mkdirs() }
        for ((suffix, folder) in listOf("en" to "values", "pt" to "values-pt-rBR")) {
            val merged = LinkedHashMap<String, String>()
            merged.putAll(readAndroidStrings(File(appRes, "$folder/strings.xml")))
            merged.putAll(readAndroidStrings(File(deskRes, "$folder/strings.xml")))
            val props = Properties()
            merged.forEach { (k, v) -> props.setProperty(k, v) }
            File(outDir, "strings_$suffix.properties").writer(Charsets.UTF_8).use { props.store(it, null) }
        }
    }
}
sourceSets["main"].resources.srcDir(generatedStrings)
tasks.named("processResources") { dependsOn(generateStrings) }

tasks.test { useJUnitPlatform() }

compose.desktop {
    application {
        mainClass = "com.juscelino.sonivell.desktop.MainKt"
        jvmArgs += listOf("-Xmx768m")
        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "sonivell"
            packageVersion = "0.1.0"
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
