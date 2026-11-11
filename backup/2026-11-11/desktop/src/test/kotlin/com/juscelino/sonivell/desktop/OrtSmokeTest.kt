package com.juscelino.sonivell.desktop

import io.github.nikitasud.latentjam.smart.smartSmokeRun
import kotlin.test.Test
import kotlin.test.assertTrue

class OrtSmokeTest {
    @Test
    fun modelRunsOnce() {
        val result = smartSmokeRun()
        println("SMOKE: $result")
        assertTrue(result.startsWith("dim=960"), result)
    }
}
