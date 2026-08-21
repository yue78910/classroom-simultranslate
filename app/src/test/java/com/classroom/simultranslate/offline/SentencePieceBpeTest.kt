package com.classroom.simultranslate.offline

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SentencePieceBpeTest {
    @Test
    fun `parses protobuf model and maps pieces to ids`() {
        val model = File.createTempFile("sentencepiece", ".model")
        model.writeBytes(buildModelBytes())
        val sp = SentencePieceBpe.load(model)
        model.delete()

        assertNotNull(sp.idOf("▁"))
        assertNotNull(sp.idOf("</s>"))
        assertEquals(0, sp.idOf("<unk>"))
    }

    @Test
    fun `encode adds language token and eos`() {
        val model = File.createTempFile("sentencepiece", ".model")
        model.writeBytes(buildModelBytes())
        val sp = SentencePieceBpe.load(model)
        val ids = sp.encode("a", languageToken = "eng_Latn", addEos = true)
        assertEquals(4, ids.size)
        assertEquals(sp.idOf("eng_Latn"), ids[0])
        assertEquals(sp.idOf("</s>"), ids.last())
        model.delete()
    }

    private fun buildModelBytes(): ByteArray {
        fun piece(piece: String, type: Int, score: Float): ByteArray {
            val body = ByteArrayOutputStream()
            writeTag(body, 1, 2)
            val pieceBytes = piece.toByteArray(Charsets.UTF_8)
            writeVarint(body, pieceBytes.size.toLong())
            body.write(pieceBytes)

            writeTag(body, 2, 5)
            body.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(score).array())

            writeTag(body, 3, 0)
            writeVarint(body, type.toLong())
            return body.toByteArray()
        }

        val out = ByteArrayOutputStream()
        listOf(
            "<unk>" to 2,
            "</s>" to 3,
            "▁" to 1,
            "a" to 1,
            "b" to 1,
            "ab" to 1,
            "eng_Latn" to 3,
            "zho_Hans" to 3,
        ).forEachIndexed { index, (text, type) ->
            writeTag(out, 1, 2)
            val bytes = piece(text, type, -(index + 1).toFloat())
            writeVarint(out, bytes.size.toLong())
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun writeTag(stream: ByteArrayOutputStream, field: Int, wire: Int) {
        writeVarint(stream, ((field shl 3) or wire).toLong())
    }

    private fun writeVarint(stream: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            if (v and 0x7FL.inv() == 0L) {
                stream.write(v.toInt())
                return
            }
            stream.write((v and 0x7F).toInt() or 0x80)
            v = v ushr 7
        }
    }
}
