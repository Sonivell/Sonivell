// Pisos de segurança para bibliotecas que o AGP, o Kotlin e o lint trazem para o BUILD (nenhuma vai nos APKs).
// O OSV-Scanner do seguranca.yml aponta CVEs nas versões que eles pedem; aqui elas sobem para as corrigidas.
// Um constraint só sobe a versão, nunca desce: quando o AGP/Kotlin já pedir a corrigida, ele não faz nada.
// Ao mudar uma versão aqui, rode de novo "Atualizar a verificação das dependências".

// Classpath dos plugins (AGP, Kotlin, Compose), resolvido pelo bloco plugins logo abaixo. O buildscript
// não enxerga o resto do script, por isso a lista fica aqui dentro.
buildscript {
    dependencies {
        constraints {
            val netty = "4.1.137.Final"
            listOf(
                "netty-buffer", "netty-codec", "netty-codec-http", "netty-codec-http2", "netty-codec-socks",
                "netty-common", "netty-handler", "netty-handler-proxy", "netty-resolver", "netty-transport",
                "netty-transport-native-unix-common",
            ).forEach { classpath("io.netty:$it:$netty") }
            listOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on").forEach { classpath("org.bouncycastle:$it:1.85") }
            classpath("org.jdom:jdom2:2.0.6.1")
            classpath("org.bitbucket.b_c:jose4j:0.9.6")
            classpath("com.google.guava:guava:33.7.2-jre")
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.androidLibrary) apply false
}

// As mesmas bibliotecas nas configurações de cada módulo (lint, compilador Kotlin, testes…).
// A guava só na variante -jre: a -android do app já tem o piso em app/build.gradle.kts.
allprojects {
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            val v = requested.version ?: return@eachDependency
            val corrigida = when {
                requested.group == "io.netty" && v.startsWith("4.1.") -> "4.1.137.Final"
                requested.group == "org.bouncycastle" && requested.name.endsWith("-jdk18on") -> "1.85"
                requested.group == "org.jdom" && requested.name == "jdom2" -> "2.0.6.1"
                requested.group == "org.bitbucket.b_c" && requested.name == "jose4j" -> "0.9.6"
                requested.group == "com.google.guava" && requested.name == "guava" && v.endsWith("-jre") -> "33.7.2-jre"
                else -> return@eachDependency
            }
            if (versaoMenor(v, corrigida)) {
                useVersion(corrigida)
                because("CVE na versão $v apontada pelo OSV-Scanner (ferramental de build)")
            }
        }
    }
}

fun versaoMenor(a: String, b: String): Boolean {
    val pa = a.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toInt() }
    val pb = b.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toInt() }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x < y
    }
    return false
}
