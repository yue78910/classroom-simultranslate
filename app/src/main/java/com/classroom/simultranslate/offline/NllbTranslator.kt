package com.classroom.simultranslate.offline

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.classroom.simultranslate.data.TranslationDirection
import java.nio.LongBuffer

/**
 * Greedy encoder-decoder inference for NLLB-200-distilled-600M INT8 ONNX.
 * The encoder runs once per sentence; the decoder runs one step per token
 * without a KV cache, which keeps the implementation simple and portable.
 */
class NllbTranslator(files: MtFiles) : AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val sessionOptions = OrtSession.SessionOptions().apply {
        setIntraOpNumThreads(2)
    }
    private val encoderSession = environment.createSession(files.encoder.absolutePath, sessionOptions)
    private val decoderSession = environment.createSession(files.decoder.absolutePath, sessionOptions)
    private val tokenizer = SentencePieceBpe.load(files.tokenizer)
    private val eosId = tokenizer.idOf("</s>") ?: 2
    private val padId = tokenizer.idOf("<pad>") ?: 1
    private val unkId = tokenizer.idOf("<unk>") ?: 0

    fun translate(text: String, direction: TranslationDirection): String {
        val (sourceLang, targetLang) = when (direction) {
            TranslationDirection.EN_TO_ZH -> "eng_Latn" to "zho_Hans"
            TranslationDirection.ZH_TO_EN -> "zho_Hans" to "eng_Latn"
        }
        val sourceIds = tokenizer.encode(text, sourceLang, addEos = true)
        val sourceLength = sourceIds.size.coerceAtMost(MAX_SEQ)
        val encIds = LongArray(MAX_SEQ) { padId.toLong() }
        val encMask = LongArray(MAX_SEQ)
        for (i in 0 until sourceLength) {
            encIds[i] = sourceIds[i].toLong()
            encMask[i] = 1L
        }

        val encoderInputs = mutableMapOf<String, OnnxTensor>()
        encoderSession.inputNames.forEach { name ->
            val lower = name.lowercase()
            encoderInputs[name] = when {
                lower == "input_ids" -> tensor(encIds)
                lower == "attention_mask" -> tensor(encMask)
                else -> tensor(encMask)
            }
        }

        val hidden: FloatArray
        val hiddenDim: Int
        val encoderResult = encoderSession.run(encoderInputs)
        try {
            val hiddenTensor = encoderResult.get(0) as OnnxTensor
            hiddenDim = (hiddenTensor.info.shape.getOrNull(2) ?: -1L).toInt().coerceAtLeast(1)
            hidden = FloatArray(MAX_SEQ * hiddenDim)
            hiddenTensor.floatBuffer.get(hidden)
        } finally {
            runCatching { encoderResult.close() }
            encoderInputs.values.forEach { runCatching { it.close() } }
        }

        val decoderInputIds = mutableListOf(tokenizer.idOf(targetLang) ?: unkId)
        val outputIds = mutableListOf<Int>()
        for (step in 1..MAX_DECODE_STEPS) {
            val currentLength = decoderInputIds.size
            val decIds = LongArray(currentLength) { decoderInputIds[it].toLong() }
            val decMask = LongArray(currentLength) { 1L }

            val decoderInputs = mutableMapOf<String, OnnxTensor>()
            decoderSession.inputNames.forEach { name ->
                val lower = name.lowercase()
                decoderInputs[name] = when {
                    lower.contains("decoder_input_ids") || lower == "input_ids" -> tensor(decIds)
                    lower.contains("decoder_attention_mask") || lower == "attention_mask" -> tensor(decMask)
                    lower.contains("encoder_hidden_states") || lower.contains("encoder_hidden") ->
                        tensor(hidden, longArrayOf(1, MAX_SEQ.toLong(), hiddenDim.toLong()))
                    lower.contains("encoder_attention_mask") -> tensor(encMask)
                    else -> tensor(decMask)
                }
            }

            val result = decoderSession.run(decoderInputs)
            try {
                val logits = result.get(0) as OnnxTensor
                val shape = logits.info.shape
                val vocabSize = shape.getOrNull(2)?.toInt() ?: error("无法读取词表大小")
                val offset = (currentLength - 1) * vocabSize
                val buffer = logits.floatBuffer
                var bestToken = unkId
                var bestScore = Float.NEGATIVE_INFINITY
                for (v in 0 until vocabSize) {
                    val score = buffer.get(offset + v)
                    if (score > bestScore) {
                        bestScore = score
                        bestToken = v
                    }
                }
                decoderInputIds += bestToken
                outputIds += bestToken
                if (bestToken == eosId) break
            } finally {
                runCatching { result.close() }
                decoderInputs.values.forEach { runCatching { it.close() } }
            }
        }

        return tokenizer.decode(outputIds.toIntArray())
    }

    private fun tensor(data: LongArray) = OnnxTensor.createTensor(
        environment,
        LongBuffer.wrap(data),
        longArrayOf(1, data.size.toLong()),
    )

    private fun tensor(data: FloatArray, shape: LongArray) = OnnxTensor.createTensor(
        environment,
        java.nio.FloatBuffer.wrap(data),
        shape,
    )

    override fun close() {
        runCatching { encoderSession.close() }
        runCatching { decoderSession.close() }
    }

    companion object {
        private const val MAX_SEQ = 128
        private const val MAX_DECODE_STEPS = 128
    }
}
