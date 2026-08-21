package com.classroom.simultranslate.offline

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

class SherpaAsr(
    files: AsrFiles,
    assetManager: AssetManager?,
) : AutoCloseable {
    private val recognizer: OnlineRecognizer
    private val stream: OnlineStream

    init {
        val transducer = OnlineTransducerModelConfig().apply {
            encoder = files.encoder.absolutePath
            decoder = files.decoder.absolutePath
            joiner = files.joiner.absolutePath
        }
        val modelConfig = OnlineModelConfig().apply {
            this.transducer = transducer
            tokens = files.tokens.absolutePath
            numThreads = 2
            debug = false
            provider = "cpu"
        }
        val featureConfig = FeatureConfig().apply {
            sampleRate = SAMPLE_RATE
            featureDim = 80
            dither = 0.0f
        }
        val endpointConfig = EndpointConfig().apply {
            rule1 = EndpointRule(true, 2.4f, 0.0f)
            rule2 = EndpointRule(true, 1.2f, 0.0f)
            rule3 = EndpointRule(false, 0.0f, 20.0f)
        }
        val config = OnlineRecognizerConfig().apply {
            featConfig = featureConfig
            this.modelConfig = modelConfig
            decodingMethod = "greedy_search"
            enableEndpoint = true
            this.endpointConfig = endpointConfig
            blankPenalty = 0.0f
        }
        recognizer = OnlineRecognizer(assetManager, config)
        stream = recognizer.createStream("greedy_search")
    }

    fun accept(samples: FloatArray) {
        stream.acceptWaveform(samples, SAMPLE_RATE)
        while (recognizer.isReady(stream)) {
            recognizer.decode(stream)
        }
    }

    fun text(): String = recognizer.getResult(stream).text.trim()

    fun reset() {
        recognizer.reset(stream)
    }

    override fun close() {
        runCatching { stream.release() }
        runCatching { recognizer.release() }
    }

    companion object {
        const val SAMPLE_RATE = 16000
    }
}
