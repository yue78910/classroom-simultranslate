package com.classroom.simultranslate.offline

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadMirrorTest {
    @Test
    fun huggingFaceUrlUsesMirror() {
        assertEquals(
            "https://hf-mirror.com/tfchan-lab/nllb-200-distilled-600M-onnx/resolve/main/encoder_model_int8.onnx",
            DownloadMirror.mirror(
                "https://huggingface.co/tfchan-lab/nllb-200-distilled-600M-onnx/resolve/main/encoder_model_int8.onnx",
            ),
        )
    }

    @Test
    fun githubUrlUsesProxy() {
        assertEquals(
            "https://ghfast.top/https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/model.tar.bz2",
            DownloadMirror.mirror(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/model.tar.bz2",
            ),
        )
    }

    @Test
    fun unknownUrlStaysUnchanged() {
        assertEquals(
            "https://example.com/model.onnx",
            DownloadMirror.mirror("https://example.com/model.onnx"),
        )
    }
}
