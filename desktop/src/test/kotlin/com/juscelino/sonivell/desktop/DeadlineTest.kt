package com.juscelino.sonivell.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Auditoria REL-03: um processo travado (como um ffprobe num arquivo ruim) não prende a leitura. */
class DeadlineTest {
    @Test
    fun hungProcessIsStoppedAtTheDeadline() {
        val p = ProcessBuilder("sleep", "30").start()
        val started = System.nanoTime()
        val out = withDeadline(p, 1) { p.inputStream.readBytes() }
        val seconds = (System.nanoTime() - started) / 1e9
        assertEquals(0, out.size)
        assertTrue(seconds < 10, "levou $seconds s")
        assertTrue(!p.isAlive)
    }

    @Test
    fun quickProcessIsNotDisturbed() {
        val p = ProcessBuilder("echo", "ok").start()
        val out = withDeadline(p, 10) { p.inputStream.bufferedReader().readText() }
        assertEquals("ok\n", out)
    }
}
