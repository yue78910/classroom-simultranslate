package com.classroom.simultranslate.engine

import com.classroom.simultranslate.audio.AudioCapture
import com.classroom.simultranslate.audio.AudioChunkListener
import com.classroom.simultranslate.data.EngineMode
import com.classroom.simultranslate.data.EngineStatus
import com.classroom.simultranslate.data.SessionConfig
import com.classroom.simultranslate.data.SubtitleReducer
import com.classroom.simultranslate.data.SubtitleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class EngineCoordinator(
    private val audioCapture: AudioCapture,
    private val onlineEngine: TranslationEngine,
    private val offlineEngineProvider: () -> TranslationEngine?,
    private val onUiState: (SubtitleSnapshot, EngineStatus, Float) -> Unit,
    private val onBanner: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reducer = SubtitleReducer()
    private var activeEngine: TranslationEngine? = null
    private var config: SessionConfig? = null
    private var startedFromAuto = false
    private var activeIsOffline = false
    private var paused = false
    private var silenceCommitJob: Job? = null
    private var lastRms = 0f

    val isRunning: Boolean
        get() = activeEngine?.isRunning == true

    fun start(config: SessionConfig) {
        stopInternal()
        this.config = config
        startedFromAuto = config.mode == EngineMode.AUTO
        reducer.clear()
        emit()

        audioCapture.listener = AudioChunkListener { samples, rms -> onAudioChunk(samples, rms) }
        if (!audioCapture.start()) {
            onBanner("无法启动麦克风，请检查录音权限")
            emit(EngineStatus.Error("无法启动麦克风"))
            return
        }

        if (config.mode == EngineMode.OFFLINE) {
            startOffline()
        } else {
            startOnline(config)
        }
    }

    fun stop() {
        stopInternal()
        emit()
    }

    fun pause() {
        if (activeEngine == null || paused) return
        paused = true
        audioCapture.stop()
    }

    fun resume() {
        if (activeEngine == null || !paused) return
        paused = false
        audioCapture.listener = AudioChunkListener { samples, rms -> onAudioChunk(samples, rms) }
        if (!audioCapture.start()) {
            onBanner("无法恢复麦克风，请检查录音权限")
            emit(EngineStatus.Error("无法恢复麦克风"))
            return
        }
        emit()
    }

    fun clearSubtitles() {
        reducer.clear()
        emit()
    }

    fun release() {
        stopInternal()
        audioCapture.release()
        scope.cancel()
    }

    private fun stopInternal() {
        silenceCommitJob?.cancel()
        silenceCommitJob = null
        activeEngine?.stop()
        activeEngine = null
        activeIsOffline = false
        paused = false
        audioCapture.stop()
    }

    private fun startOnline(config: SessionConfig) {
        val engine = onlineEngine
        activeEngine = engine
        activeIsOffline = false
        engine.start(config, object : TranslationEngineListener {
            override fun onSourceDelta(delta: String) {
                reducer.onSourceDelta(delta)
                emit()
            }

            override fun onSourceFinal(text: String) {
                reducer.onSourceFinal(text)
                emit()
            }

            override fun onTargetDelta(delta: String) {
                reducer.onTargetDelta(delta)
                emit()
            }

            override fun onTargetFinal(text: String) {
                reducer.onTargetFinal(text)
                emit()
            }

            override fun onStatus(status: EngineStatus) {
                when (status) {
                    is EngineStatus.Error -> {
                        activeEngine?.stop()
                        activeEngine = null
                        if (startedFromAuto) {
                            onBanner("在线连接失败，已切换离线模式：${status.message}")
                            startOffline()
                        } else {
                            emit(status)
                        }
                    }
                    else -> emit(status)
                }
            }
        })
    }

    private fun startOffline() {
        val engine = offlineEngineProvider()
        if (engine == null) {
            onBanner("离线模型尚未下载完成，请先在“离线模型”页下载")
            emit(EngineStatus.Error("离线模型未安装"))
            return
        }
        activeIsOffline = true
        activeEngine = engine
        val cfg = config ?: return
        engine.start(cfg, object : TranslationEngineListener {
            override fun onSourceDelta(delta: String) {
                reducer.onSourceDelta(delta)
                emit()
            }

            override fun onSourceFinal(text: String) {
                reducer.onSourceFinal(text)
                reducer.commitCurrent()
                emit()
            }

            override fun onTargetDelta(delta: String) {
                reducer.onTargetDelta(delta)
                emit()
            }

            override fun onTargetFinal(text: String) {
                reducer.onTargetFinal(text)
                emit()
            }

            override fun onStatus(status: EngineStatus) = emit(status)
        })
    }

    private fun onAudioChunk(samples: FloatArray, rms: Float) {
        if (paused) return
        lastRms = rms
        activeEngine?.onAudioChunk(samples)
        scheduleSilenceCommit(rms)
        emit()
    }

    private fun scheduleSilenceCommit(rms: Float) {
        silenceCommitJob?.cancel()
        if (rms > SILENCE_THRESHOLD) return
        silenceCommitJob = scope.launch {
            delay(SILENCE_COMMIT_MS)
            if (isActive) {
                reducer.commitCurrent()
                emit()
            }
        }
    }

    private fun emit(status: EngineStatus? = null) {
        val resolved = status ?: when {
            activeIsOffline -> EngineStatus.Offline
            activeEngine?.isRunning == true -> EngineStatus.Online
            else -> EngineStatus.Idle
        }
        onUiState(reducer.snapshot(), resolved, lastRms)
    }

    companion object {
        private const val SILENCE_THRESHOLD = 0.015f
        private const val SILENCE_COMMIT_MS = 1200L
    }
}
