package com.juscelino.sonivell.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** Auditoria REL-05: arquivo escolhido pela pessoa é lido com tamanho máximo. */
class BoundedReadTest {
    @Test
    fun smallFileIsReadWhole() {
        val data = ByteArray(200_000) { (it % 251).toByte() }
        assertArrayEquals(data, BoundedRead.bytes(ByteArrayInputStream(data), 200_000))
    }

    @Test
    fun fileOverTheLimitIsRefused() {
        assertNull(BoundedRead.bytes(ByteArrayInputStream(ByteArray(200_001)), 200_000))
    }

    @Test
    fun hugeStreamStopsAtTheLimitWithoutReadingItAll() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int = 0.also { served++ }
            override fun read(b: ByteArray, off: Int, len: Int): Int { served += len; return len }
        }
        assertNull(BoundedRead.bytes(endless, 1_000_000))
        assertTrue("leu $served bytes", served < 2_000_000)
    }
}
