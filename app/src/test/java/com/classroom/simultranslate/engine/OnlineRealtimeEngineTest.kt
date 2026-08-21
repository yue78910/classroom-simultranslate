package com.classroom.simultranslate.engine

import com.classroom.simultranslate.data.SessionConfig
import com.classroom.simultranslate.data.TranslationDirection
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OnlineRealtimeEngineTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        runCatching { server.close() }
    }

    @Test
    fun `sends session update and audio append then emits transcript delta`() = runBlocking {
        val serverMessages = Collections.synchronizedList(mutableListOf<String>())
        val serverOpen = CountDownLatch(1)
        val receivedDelta = CountDownLatch(1)
        val appendReceived = CountDownLatch(1)
        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                serverOpen.countDown()
                webSocket.send(
                    """{"type":"session.output_transcript.delta","delta":"你好"}""",
                )
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                serverMessages += text
                if (text.contains("session.input_audio_buffer.append")) {
                    appendReceived.countDown()
                }
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))

        val wsUrl = server.url("/v1/realtime/translations").toString()
            .replaceFirst("http", "ws")
        val engine = OnlineRealtimeEngine(translationUrl = wsUrl)
        var targetText = ""
        val listener = object : TranslationEngineListener {
            override fun onSourceDelta(delta: String) = Unit
            override fun onSourceFinal(text: String) = Unit
            override fun onTargetDelta(delta: String) {
                targetText += delta
                receivedDelta.countDown()
            }
            override fun onTargetFinal(text: String) = Unit
            override fun onStatus(status: com.classroom.simultranslate.data.EngineStatus) = Unit
        }

        engine.start(
            SessionConfig(direction = TranslationDirection.EN_TO_ZH, apiKey = "test-key"),
            listener,
        )
        assertTrue(serverOpen.await(3, TimeUnit.SECONDS))
        engine.onAudioChunk(FloatArray(160) { 0.1f })

        assertTrue(appendReceived.await(3, TimeUnit.SECONDS))
        assertTrue(receivedDelta.await(3, TimeUnit.SECONDS))
        assertTrue(serverMessages.any { it.contains("session.update") })
        assertTrue(serverMessages.any { it.contains("session.input_audio_buffer.append") })
        assertTrue(targetText.contains("你好"))
        engine.stop()
        Thread.sleep(500)
    }
}
