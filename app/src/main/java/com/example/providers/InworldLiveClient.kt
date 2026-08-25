package com.example.providers

import android.util.Base64
import android.util.Log
import com.example.debug.LiveDebugLogger
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * Manages WebSocket connection to the Inworld AI Realtime API
 * for low-latency bidirectional speech-to-speech companion conversation.
 */
class InworldLiveClient(
    private val apiKey: String,
    private val voice: String = "Sarah",
    private val model: String = "openai/gpt-4o-mini"
) {
    private val tag = "InworldLiveClient"
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var setupTimeoutJob: Job? = null

    interface LiveEventListener {
        fun onConnected()
        fun onSetupComplete()
        fun onAudioChunkReceived(audioData: ByteArray)
        fun onTranscriptChunkReceived(text: String)
        fun onTurnComplete()
        fun onInterrupted()
        fun onError(error: String)
        fun onDisconnected(reason: String)
    }

    var listener: LiveEventListener? = null

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for WebSockets
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val mapAdapter = moshi.adapter(Map::class.java)

    private var isConnected = false
    private var isSetupDone = false
    private var sentAudioChunkCount = 0
    private var receivedAudioChunkCount = 0

    fun isReady(): Boolean = isConnected && isSetupDone

    fun connect(systemInstructionText: String) {
        if (apiKey.isBlank()) {
            LiveDebugLogger.log("Connect failed: Inworld API key is missing", LiveDebugLogger.LogLevel.ERROR)
            listener?.onError("Inworld API key is missing. Please set it in Settings.")
            return
        }

        disconnect()
        sentAudioChunkCount = 0
        receivedAudioChunkCount = 0

        val wsUrl = "wss://api.inworld.ai/api/v1/realtime/session"
        val authHeader = buildAuthHeader(apiKey)

        Log.i(tag, "Connecting to Inworld Realtime WebSocket at $wsUrl...")
        LiveDebugLogger.setWsStatus("Connecting")
        LiveDebugLogger.log("WebSocket connecting to Inworld AI ($wsUrl)...", LiveDebugLogger.LogLevel.INFO)

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", authHeader)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(tag, "Inworld WebSocket opened successfully")
                isConnected = true
                LiveDebugLogger.setWsStatus("Connected")
                LiveDebugLogger.log("Inworld WebSocket opened (HTTP ${response.code}, msg='${response.message}')", LiveDebugLogger.LogLevel.SUCCESS)
                listener?.onConnected()

                // Send session.update message
                sendSessionUpdate(systemInstructionText)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(tag, "Inworld WebSocket closing: code=$code, reason=$reason")
                val reasonText = if (reason.isBlank()) "<empty>" else reason
                LiveDebugLogger.log("Inworld WebSocket closing: code=$code, reason='$reasonText'", LiveDebugLogger.LogLevel.WARN)
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(tag, "Inworld WebSocket closed: code=$code, reason=$reason")
                isConnected = false
                isSetupDone = false
                LiveDebugLogger.setWsStatus("Disconnected")
                val reasonText = if (reason.isBlank()) "<empty>" else reason
                LiveDebugLogger.log("Inworld WebSocket closed: code=$code, reason='$reasonText'", LiveDebugLogger.LogLevel.WARN)
                listener?.onDisconnected("code=$code, reason='$reasonText'")
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "Inworld WebSocket failure: ${t.localizedMessage}", t)
                setupTimeoutJob?.cancel()
                isConnected = false
                isSetupDone = false
                LiveDebugLogger.setWsStatus("Disconnected")
                val respBody = if (response != null) {
                    try { response.body?.string() } catch (_: Exception) { null }
                } else null
                val errorMsg = if (response != null) {
                    "Inworld WebSocket failure (HTTP ${response.code} ${response.message}): ${respBody ?: t.localizedMessage ?: t.javaClass.simpleName}"
                } else {
                    "Inworld WebSocket failure: ${t.javaClass.simpleName} - ${t.localizedMessage ?: "Connection error"}"
                }
                LiveDebugLogger.log(errorMsg, LiveDebugLogger.LogLevel.ERROR)
                listener?.onError(errorMsg)
            }
        })
    }

    private fun sendSessionUpdate(systemInstructionText: String) {
        val sessionConfig = mutableMapOf<String, Any>(
            "type" to "realtime",
            "instructions" to systemInstructionText,
            "model" to model,
            "audio" to mapOf(
                "input" to mapOf(
                    "transcription" to mapOf(
                        "model" to "inworld/inworld-stt-1",
                        "language" to "en"
                    ),
                    "turn_detection" to mapOf(
                        "type" to "semantic_vad",
                        "eagerness" to "medium",
                        "interrupt_response" to true
                    )
                ),
                "output" to mapOf(
                    "voice" to voice,
                    "model" to "inworld-tts-2"
                )
            )
        )

        val updateMessage = mapOf(
            "type" to "session.update",
            "session" to sessionConfig
        )

        val json = mapAdapter.toJson(updateMessage)
        Log.d(tag, "Sending Inworld session.update: $json")
        LiveDebugLogger.log("Session configuration sent to Inworld AI (Voice: $voice, Model: $model)", LiveDebugLogger.LogLevel.INFO)
        LiveDebugLogger.log("Inworld setup payload:\n$json", LiveDebugLogger.LogLevel.DATA)

        // Start 12s setup watchdog to catch timeouts
        setupTimeoutJob?.cancel()
        setupTimeoutJob = clientScope.launch {
            delay(12_000L)
            if (!isSetupDone && isConnected) {
                Log.e(tag, "Inworld setup timeout: session.updated not received within 12s")
                val errorMsg = "Setup timeout (12s): No session confirmation received from Inworld AI"
                LiveDebugLogger.log(errorMsg, LiveDebugLogger.LogLevel.ERROR)
                listener?.onError(errorMsg)
                disconnect()
            }
        }

        webSocket?.send(json)
    }

    fun sendAudioChunk(pcm16kChunk: ByteArray) {
        if (!isReady() || webSocket == null || pcm16kChunk.isEmpty()) return

        val base64Data = Base64.encodeToString(pcm16kChunk, Base64.NO_WRAP)
        val audioAppend = mapOf(
            "type" to "input_audio_buffer.append",
            "audio" to base64Data
        )

        val json = mapAdapter.toJson(audioAppend)
        val sent = webSocket?.send(json) ?: false
        if (sent) {
            sentAudioChunkCount++
            if (sentAudioChunkCount % 20 == 1) {
                LiveDebugLogger.log("Audio chunk sent to Inworld (#$sentAudioChunkCount): ${pcm16kChunk.size} bytes", LiveDebugLogger.LogLevel.DATA)
            }
        }
    }

    fun sendTextMessage(userPrompt: String) {
        if (!isReady() || webSocket == null || userPrompt.isBlank()) return

        val itemCreate = mapOf(
            "type" to "conversation.item.create",
            "item" to mapOf(
                "type" to "message",
                "role" to "user",
                "content" to listOf(
                    mapOf(
                        "type" to "input_text",
                        "text" to userPrompt
                    )
                )
            )
        )

        val json = mapAdapter.toJson(itemCreate)
        LiveDebugLogger.log("Text message sent to Inworld: \"$userPrompt\"", LiveDebugLogger.LogLevel.INFO)
        webSocket?.send(json)
        webSocket?.send(mapAdapter.toJson(mapOf("type" to "response.create")))
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val root = mapAdapter.fromJson(jsonText) ?: return
            val type = root["type"] as? String ?: ""

            when (type) {
                "session.created", "session.updated" -> {
                    Log.i(tag, "Inworld session established: $type")
                    setupTimeoutJob?.cancel()
                    setupTimeoutJob = null
                    isSetupDone = true
                    LiveDebugLogger.log("Session confirmation ($type) received from Inworld AI", LiveDebugLogger.LogLevel.SUCCESS)
                    listener?.onSetupComplete()
                }

                "conversation.item.input_audio_transcription.completed",
                "input_audio_transcription.completed" -> {
                    val userTranscript = root["transcript"] as? String
                        ?: root["text"] as? String
                        ?: root["delta"] as? String
                        ?: ""
                    if (userTranscript.isNotBlank()) {
                        Log.i(tag, "User speech transcribed: $userTranscript")
                        LiveDebugLogger.log("[USER SAID]: $userTranscript", LiveDebugLogger.LogLevel.SUCCESS)
                    }
                }

                "conversation.item.input_audio_transcription.delta",
                "input_audio_transcription.delta" -> {
                    val delta = root["delta"] as? String ?: root["transcript"] as? String ?: ""
                    if (delta.isNotBlank()) {
                        Log.v(tag, "User speech transcript delta: $delta")
                        LiveDebugLogger.log("[USER SAID (delta)]: $delta", LiveDebugLogger.LogLevel.DATA)
                    }
                }

                "conversation.item.created" -> {
                    val item = root["item"] as? Map<*, *>
                    val role = item?.get("role") as? String
                    if (role == "user") {
                        val contentList = item["content"] as? List<*>
                        contentList?.forEach { contentItem ->
                            if (contentItem is Map<*, *>) {
                                val transcript = contentItem["transcript"] as? String ?: contentItem["text"] as? String
                                if (!transcript.isNullOrBlank()) {
                                    Log.i(tag, "User speech item created: $transcript")
                                    LiveDebugLogger.log("[USER SAID]: $transcript", LiveDebugLogger.LogLevel.SUCCESS)
                                }
                            }
                        }
                    }
                }

                "response.output_audio.delta", "response.audio.delta" -> {
                    val base64Delta = root["delta"] as? String ?: root["audio"] as? String
                    if (!base64Delta.isNullOrEmpty()) {
                        val audioBytes = Base64.decode(base64Delta, Base64.NO_WRAP)
                        receivedAudioChunkCount++
                        if (receivedAudioChunkCount % 15 == 1) {
                            LiveDebugLogger.log("Audio chunk received from Inworld (#$receivedAudioChunkCount): ${audioBytes.size} bytes", LiveDebugLogger.LogLevel.DATA)
                        }
                        listener?.onAudioChunkReceived(audioBytes)
                    }
                }

                "response.output_audio_transcript.delta", "response.audio_transcript.delta", "response.text.delta" -> {
                    val deltaText = root["delta"] as? String ?: root["text"] as? String ?: root["transcript"] as? String
                    if (!deltaText.isNullOrEmpty()) {
                        LiveDebugLogger.log("Inworld transcript: \"$deltaText\"", LiveDebugLogger.LogLevel.INFO)
                        listener?.onTranscriptChunkReceived(deltaText)
                    }
                }

                "input_audio_buffer.speech_started" -> {
                    Log.d(tag, "Inworld VAD speech started (user interruption)")
                    LiveDebugLogger.log("Model turn interrupted by user voice (VAD speech started)", LiveDebugLogger.LogLevel.WARN)
                    listener?.onInterrupted()
                }

                "input_audio_buffer.speech_stopped" -> {
                    Log.d(tag, "Inworld VAD speech stopped")
                    LiveDebugLogger.log("User voice input ended (VAD speech stopped)", LiveDebugLogger.LogLevel.INFO)
                }

                "response.done", "response.output_item.done" -> {
                    Log.d(tag, "Inworld turn completed: $type")
                    LiveDebugLogger.log("Inworld turn complete ($type)", LiveDebugLogger.LogLevel.SUCCESS)
                    listener?.onTurnComplete()
                }

                "error" -> {
                    setupTimeoutJob?.cancel()
                    setupTimeoutJob = null
                    val errorObj = root["error"] as? Map<*, *>
                    val msg = errorObj?.get("message") as? String ?: root["message"] as? String ?: "Inworld Realtime API error"
                    Log.e(tag, "Inworld returned error: $msg ($jsonText)")
                    LiveDebugLogger.log("Inworld error: $jsonText", LiveDebugLogger.LogLevel.ERROR)
                    listener?.onError(msg)
                }

                else -> {
                    // Log other telemetry events for debug transparency
                    if (type.isNotEmpty()) {
                        Log.v(tag, "Inworld event received: $type")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse Inworld WebSocket message: $jsonText", e)
            LiveDebugLogger.log("Parse error: ${e.localizedMessage}", LiveDebugLogger.LogLevel.ERROR)
        }
    }

    fun disconnect() {
        try {
            setupTimeoutJob?.cancel()
            setupTimeoutJob = null
            webSocket?.cancel()
            webSocket?.close(1000, "User ended session")
        } catch (e: Exception) {
            Log.e(tag, "Error closing Inworld WebSocket", e)
        } finally {
            webSocket = null
            isConnected = false
            isSetupDone = false
            LiveDebugLogger.setWsStatus("Disconnected")
            LiveDebugLogger.log("Disconnected: Inworld session closed", LiveDebugLogger.LogLevel.INFO)
        }
    }

    companion object {
        fun buildAuthHeader(apiKey: String): String {
            val trimmed = apiKey.trim()
            return when {
                trimmed.startsWith("Basic ", ignoreCase = true) || trimmed.startsWith("Bearer ", ignoreCase = true) -> trimmed
                trimmed.contains(":") -> "Basic " + Base64.encodeToString(trimmed.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                else -> {
                    try {
                        val decoded = Base64.decode(trimmed, Base64.NO_WRAP)
                        if (decoded.isNotEmpty() && trimmed.length % 4 == 0 && !trimmed.contains(" ")) {
                            "Basic $trimmed"
                        } else {
                            "Basic " + Base64.encodeToString("$trimmed:".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                        }
                    } catch (_: Exception) {
                        "Basic " + Base64.encodeToString("$trimmed:".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    }
                }
            }
        }

        /**
         * Real network verification of Inworld credentials against Inworld AI servers.
         */
        suspend fun testConnection(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
            val cleanKey = apiKey.trim()
            if (cleanKey.isEmpty()) {
                return@withContext Result.failure(Exception("Inworld API key cannot be empty."))
            }

            val authHeader = buildAuthHeader(cleanKey)
            val testClient = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            // 1. Try testing via Inworld API endpoint
            val testEndpoints = listOf(
                "https://api.inworld.ai/v1/voices",
                "https://api.inworld.ai/llm/v1alpha/models"
            )

            for (endpoint in testEndpoints) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .addHeader("Authorization", authHeader)
                        .get()
                        .build()

                    val response = testClient.newCall(request).execute()
                    val code = response.code
                    val body = response.body?.string() ?: ""

                    if (response.isSuccessful) {
                        return@withContext Result.success("Inworld AI connection verified! Status: HTTP $code OK.")
                    } else if (code == 401 || code == 403) {
                        return@withContext Result.failure(Exception("Inworld authentication failed (HTTP $code): Invalid or unauthorized API key."))
                    }
                } catch (e: Exception) {
                    Log.w("InworldLiveClient", "Endpoint $endpoint test attempt failed: ${e.message}")
                }
            }

            // 2. Direct Realtime WebSocket test handshake
            try {
                var receivedSession = false
                var errorMsg: String? = null
                val lock = java.util.concurrent.CountDownLatch(1)

                val wsRequest = Request.Builder()
                    .url("wss://api.inworld.ai/api/v1/realtime/session")
                    .addHeader("Authorization", authHeader)
                    .build()

                val ws = testClient.newWebSocket(wsRequest, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        receivedSession = true
                        webSocket.close(1000, "Test done")
                        lock.countDown()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (response != null) {
                            errorMsg = "HTTP ${response.code} ${response.message}"
                        } else {
                            errorMsg = t.localizedMessage ?: "Connection refused"
                        }
                        lock.countDown()
                    }
                })

                lock.await(6, TimeUnit.SECONDS)
                ws.cancel()

                if (receivedSession) {
                    return@withContext Result.success("Inworld Realtime API connection verified successfully!")
                } else if (errorMsg != null) {
                    return@withContext Result.failure(Exception("Inworld connection verification failed: $errorMsg"))
                } else {
                    return@withContext Result.failure(Exception("Connection timed out waiting for Inworld server response."))
                }
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("Could not connect to Inworld AI: ${e.localizedMessage}"))
            }
        }
    }
}
