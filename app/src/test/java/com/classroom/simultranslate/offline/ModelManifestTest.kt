package com.classroom.simultranslate.offline

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestTest {
    @Test
    fun `manifest parses with expected packs`() {
        val json = Json { ignoreUnknownKeys = true }
        val manifest = json.decodeFromString<ModelManifest>(
            """
            {
              "version": 1,
              "packs": [
                {
                  "id": "asr-zh-en",
                  "label": "ASR",
                  "kind": "asr",
                  "type": "tar.bz2",
                  "url": "https://example.com/model.tar.bz2",
                  "extractNames": ["encoder.onnx", "tokens.txt"]
                },
                {
                  "id": "nllb-600m-zh-en",
                  "label": "MT",
                  "kind": "mt",
                  "type": "files",
                  "files": [
                    {"name": "encoder.onnx", "url": "https://example.com/encoder.onnx"},
                    {"name": "decoder.onnx", "url": "https://example.com/decoder.onnx"}
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(2, manifest.packs.size)
        assertTrue(manifest.packs.any { it.type == "tar.bz2" })
        assertEquals("nllb-600m-zh-en", manifest.packs.last().id)
    }
}
