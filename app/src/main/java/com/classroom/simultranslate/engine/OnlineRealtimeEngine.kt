package com.classroom.simultranslate.engine

import com.classroom.simultranslate.audio.AudioResampler
import com.classroom.simultranslate.data.EngineStatus
import com.classroom.simultranslate.data.SessionConfig
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * OpenAI Realtime translation session.
 *
 * Protocol reference:
 * wss://api.openai.com/v1/realtime/translations?model=gpt-realtime-translate
 * 24 kHz PCM16 mono base64 input, streamed with session.input_audio_buffer.append.
 */
class OnlineRealtimeEngine(
    private val client: OkHttpClient = defaultClient(),
    private val translationUrl: String = TRANSLATION_URL,
) : TranslationEngine {

    override var isRunning: Boolean = false
        private set

    private var webSocket: WebSocket? = null
    private var listener: TranslationEngineListener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sendJob: Job? = null
    private val pendingChunks = ArrayDeque<FloatArray>()

    override fun start(config: SessionConfig, listener: TranslationEngineListener) {
        stop()
        if (config.apiKey.isBlank()) {
            listener.onStatus(EngineStatus.Error("请先在设置中填写 OpenAI API Key"))
            return
        }
        this.listener = listener
        listener.onStatus(EngineStatus.Starting)

        val request = Request.Builder()
            .url("$translationUrl?model=gpt-realtime-translate")
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isRunning = true
                listener.onStatus(EngineStatus.Online)
                sendSessionUpdate(webSocket, config)
                drainPendingChunks(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerEvent(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isRunning = false
                listener.onStatus(EngineStatus.Error(t.message ?: "在线连接失败"))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isRunning = false
            }
        })
    }

    override fun onAudioChunk(samples: FloatArray) {
        if (!isRunning) {
            if (pendingChunks.size < MAX_PENDING_CHUNKS) pendingChunks.addLast(samples)
            return
        }
        val ws = webSocket ?: return
        val resampled = AudioResampler.resample16kTo24k(samples)
        val pcm = AudioResampler.toPcm16LittleEndian(resampled)
        val event = buildJsonObject {
            put("type", "session.input_audio_buffer.append")
            put("audio", Base64.getEncoder().encodeToString(pcm))
        }
        ws.send(event.toString())
    }

    override fun stop() {
        sendJob?.cancel()
        val ws = webSocket
        webSocket = null
        if (ws != null) {
            runCatching {
                ws.send("""{"type":"session.close"}""")
                ws.close(1000, "client stop")
            }
        }
        pendingChunks.clear()
        isRunning = false
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun sendSessionUpdate(webSocket: WebSocket, config: SessionConfig) {
        val update = buildJsonObject {
            put("type", "session.update")
            put(
                "session",
                buildJsonObject {
                    put(
                        "audio",
                        buildJsonObject {
                            put("output", buildJsonObject {
                                put("language", config.direction.openAiTargetLanguage)
                            })
                            put(
                                "input",
                                buildJsonObject {
                                    put("transcription", buildJsonObject {
                                        put("model", "gpt-realtime-whisper")
                                    })
                                    put("noise_reduction", buildJsonObject {
                                        put("type", "near_field")
                                    })
                                },
                            )
                        },
                    )
                },
            )
        }
        webSocket.send(update.toString())
    }

    private fun drainPendingChunks(webSocket: WebSocket) {
        sendJob = scope.launch {
            while (pendingChunks.isNotEmpty()) {
                val chunk = pendingChunks.removeFirst()
                onAudioChunk(chunk)
            }
        }
    }

    private fun handleServerEvent(text: String) {
        val event = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (event["type"]?.jsonPrimitive?.content) {
            "session.input_transcript.delta" -> {
                event["delta"]?.jsonPrimitive?.content?.let { listener?.onSourceDelta(it) }
            }
            "session.output_transcript.delta" -> {
                event["delta"]?.jsonPrimitive?.content?.let { listener?.onTargetDelta(it) }
            }
            "session.input_transcript.done" -> {
                event["text"]?.jsonPrimitive?.content?.let { listener?.onSourceFinal(it) }
            }
            "session.output_transcript.done" -> {
                event["text"]?.jsonPrimitive?.content?.let { listener?.onTargetFinal(it) }
            }
            "error" -> {
                val message = (event["error"] as? JsonObject)
                    ?.get("message")?.jsonPrimitive?.content
                    ?: "OpenAI 返回错误"
                listener?.onStatus(EngineStatus.Error(message))
            }
        }
    }

    companion object {
        private const val TRANSLATION_URL = "wss://api.openai.com/v1/realtime/translations"
        private const val MAX_PENDING_CHUNKS = 50

        private fun defaultClient() = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
