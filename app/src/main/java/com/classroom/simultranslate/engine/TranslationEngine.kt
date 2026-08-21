package com.classroom.simultranslate.engine

import com.classroom.simultranslate.data.EngineStatus
import com.classroom.simultranslate.data.SessionConfig

interface TranslationEngineListener {
    fun onSourceDelta(delta: String)
    fun onSourceFinal(text: String)
    fun onTargetDelta(delta: String)
    fun onTargetFinal(text: String)
    fun onStatus(status: EngineStatus)
}

interface TranslationEngine {
    val isRunning: Boolean

    fun start(config: SessionConfig, listener: TranslationEngineListener)

    fun onAudioChunk(samples: FloatArray)

    fun stop()
}

