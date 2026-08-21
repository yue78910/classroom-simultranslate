package com.classroom.simultranslate.engine

import android.content.res.AssetManager
import com.classroom.simultranslate.data.EngineStatus
import com.classroom.simultranslate.data.SessionConfig
import com.classroom.simultranslate.offline.ModelManager
import com.classroom.simultranslate.offline.NllbTranslator
import com.classroom.simultranslate.offline.SherpaAsr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OfflineEngine(
    private val modelManager: ModelManager,
    private val scope: CoroutineScope,
    private val assetManager: AssetManager?,
) : TranslationEngine {

    override var isRunning: Boolean = false
        private set

    private var listener: TranslationEngineListener? = null
    private var config: SessionConfig? = null
    private var asr: SherpaAsr? = null
    private var translator: NllbTranslator? = null
    private var audioChannel = Channel<FloatArray>(Channel.UNLIMITED)
    private var processingJob: Job? = null

    override fun start(config: SessionConfig, listener: TranslationEngineListener) {
        stop()
        val asrFiles = modelManager.asrFiles()
        val mtFiles = modelManager.mtFiles()
        if (asrFiles == null || mtFiles == null) {
            listener.onStatus(EngineStatus.Error("离线模型未安装，请先到“离线模型”页下载"))
            return
        }

        this.listener = listener
        this.config = config
        audioChannel = Channel(Channel.UNLIMITED)
        listener.onStatus(EngineStatus.Starting)

        val loadResult = runCatching {
            asr = SherpaAsr(asrFiles, assetManager)
            translator = NllbTranslator(mtFiles)
        }
        if (loadResult.isFailure) {
            closeResources()
            listener.onStatus(EngineStatus.Error(loadResult.exceptionOrNull()?.message ?: "离线模型加载失败"))
            return
        }

        isRunning = true
        listener.onStatus(EngineStatus.Offline)
        processingJob = scope.launch(Dispatchers.Default) { processAudio() }
    }

    override fun onAudioChunk(samples: FloatArray) {
        if (isRunning) audioChannel.trySend(samples)
    }

    override fun stop() {
        isRunning = false
        processingJob?.cancel()
        processingJob = null
        closeResources()
        audioChannel.close()
    }

    private suspend fun processAudio() {
        var stableText = ""
        var stableSince = 0L
        while (currentCoroutineContext().isActive) {
            val samples = audioChannel.receive()
            val recognizer = asr ?: return
            recognizer.accept(samples)
            val text = recognizer.text()
            val now = System.currentTimeMillis()
            if (text != stableText) {
                stableText = text
                stableSince = now
                if (text.isNotBlank()) listener?.onSourceDelta(text)
            } else if (text.isNotBlank() && now - stableSince >= FINALIZE_MS) {
                listener?.onSourceFinal(text)
                val sentence = text
                stableText = ""
                recognizer.reset()
                translate(sentence)
            }
        }
    }

    private suspend fun translate(sentence: String) {
        val result = withContext(Dispatchers.Default) {
            val cfg = config ?: return@withContext ""
            runCatching { translator?.translate(sentence, cfg.direction) }.getOrNull().orEmpty()
        }
        if (result.isBlank()) return
        listener?.onTargetDelta(result)
        listener?.onTargetFinal(result)
    }

    private fun closeResources() {
        runCatching { asr?.close() }
        runCatching { translator?.close() }
        asr = null
        translator = null
    }

    companion object {
        private const val FINALIZE_MS = 900L
    }
}
