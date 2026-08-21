package com.classroom.simultranslate.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioResamplerTest {
    @Test
    fun `16k to 24k output has 3 to 2 length ratio`() {
        val input = FloatArray(1600) { index -> index.toFloat() / 1600f }
        val output = AudioResampler.resample16kTo24k(input)
        assertEquals(input.size * 3 / 2, output.size)
    }

    @Test
    fun `constant input stays constant`() {
        val input = FloatArray(320) { 0.5f }
        val output = AudioResampler.resample16kTo24k(input)
        assertTrue(output.all { kotlin.math.abs(it - 0.5f) < 1e-5f })
    }

    @Test
    fun `pcm encoding is 16 bit little endian`() {
        val bytes = AudioResampler.toPcm16LittleEndian(floatArrayOf(0f, 1f, -1f))
        assertEquals(6, bytes.size)
        assertEquals(0, bytes[0].toInt())
        assertEquals(0, bytes[1].toInt())
        assertEquals((-128).toByte(), bytes[5])
    }
}
