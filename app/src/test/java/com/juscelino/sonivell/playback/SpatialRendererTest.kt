package com.juscelino.sonivell.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class SpatialRendererTest {
    private val rate = 48_000

    private fun run(r: SpatialRenderer, frames: Int, enabled: Boolean, input: (Int) -> Pair<Float, Float>): Pair<FloatArray, FloatArray> {
        val l = FloatArray(frames)
        val rr = FloatArray(frames)
        for (i in 0 until frames) {
            val (a, b) = input(i)
            r.render(a, b, enabled)
            l[i] = r.outLeft
            rr[i] = r.outRight
        }
        return l to rr
    }

    private fun rms(x: FloatArray, from: Int) = sqrt(x.drop(from).map { it * it }.average()).toFloat()

    @Test
    fun desligadoDevolveOOriginal() {
        val r = SpatialRenderer(rate)
        val (l, rr) = run(r, 2_000, enabled = false) { i -> (i % 7) / 10f to -(i % 5) / 10f }
        for (i in l.indices) {
            assertEquals((i % 7) / 10f, l[i], 0f)
            assertEquals(-(i % 5) / 10f, rr[i], 0f)
        }
    }

    @Test
    fun centroFicaNoCentro() {
        val r = SpatialRenderer(rate)
        val tone = { i: Int -> (0.5 * sin(2 * PI * 1_000 * i / rate)).toFloat().let { it to it } }
        val (l, rr) = run(r, 24_000, enabled = true, input = tone)
        // Voz no meio do disco: os dois ouvidos quase iguais (só as reflexões da sala diferem um pouco).
        val left = rms(l, 12_000)
        val right = rms(rr, 12_000)
        assertTrue("esquerdo $left, direito $right", abs(left - right) / maxOf(left, right) < 0.15f)
        // E o volume parecido com o original (0,5 de pico = 0,354 RMS).
        assertTrue("volume $left", left in 0.25f..0.5f)
    }

    @Test
    fun ladoEsquerdoChegaMaisForteNoOuvidoEsquerdo() {
        val r = SpatialRenderer(rate)
        // Agudo só no canal esquerdo: o ouvido direito recebe, mas abafado pela cabeça.
        val (l, rr) = run(r, 24_000, enabled = true) { i -> (0.5 * sin(2 * PI * 6_000 * i / rate)).toFloat() to 0f }
        val left = rms(l, 12_000)
        val right = rms(rr, 12_000)
        assertTrue("esquerdo $left, direito $right", left > right * 1.5f)
        assertTrue("o direito também ouve (não é só panorâmica)", right > 0.02f)
    }

    @Test
    fun naoEstouraNoVolumeMaximo() {
        val r = SpatialRenderer(rate)
        val (l, rr) = run(r, 24_000, enabled = true) { i -> (if ((i / 24) % 2 == 0) 1f else -1f).let { it to it } }
        assertTrue(l.all { abs(it) <= 1f } && rr.all { abs(it) <= 1f })
    }

    @Test
    fun ligarEDesligarSemSalto() {
        val r = SpatialRenderer(rate)
        var prev = 0f
        var maxJump = 0f
        for (i in 0 until 48_000) {
            val x = (0.3 * sin(2 * PI * 220 * i / rate)).toFloat()
            r.render(x, x, enabled = (i / 12_000) % 2 == 1)
            maxJump = maxOf(maxJump, abs(r.outLeft - prev))
            prev = r.outLeft
        }
        // Um seno de 220 Hz a 0,3 anda no máximo ~0,009 por amostra; a troca não pode dar um degrau grande.
        assertTrue("salto $maxJump", maxJump < 0.05f)
    }
}
