package com.classroom.simultranslate.audio

/**
 * Linear resampler used to convert the 16 kHz microphone stream to the
 * 24 kHz PCM expected by the OpenAI Realtime API (ratio 3:2).
 */
object AudioResampler {
    fun resample16kTo24k(input: FloatArray): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        val outSize = (input.size * 3) / 2
        val out = FloatArray(outSize)
        for (i in out.indices) {
            val pos = i * 2f / 3f
            val index = pos.toInt()
            val frac = pos - index
            val left = input[index.coerceAtMost(input.lastIndex)]
            val right = input[(index + 1).coerceAtMost(input.lastIndex)]
            out[i] = left + (right - left) * frac
        }
        return out
    }

    fun toPcm16LittleEndian(samples: FloatArray): ByteArray {
        val bytes = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val sample = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            bytes[i * 2] = (sample.toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}

