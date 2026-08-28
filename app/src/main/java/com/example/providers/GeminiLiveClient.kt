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
 * Manages WebSocket connection to the Gemini Multimodal Live API
 * (BidiGenerateContent) for low-latency bidirectional real-time audio conversation.
 */
class GeminiLiveClient(
    private val apiKey: String,
    private val model: String = "gemini-2.5-flash-native-audio-preview-12-2025",
    private val voiceName: String = "Aoede"
) {
    private val tag = "GeminiLiveClient"
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var setupTimeoutJob: Job? = null

    interface LiveEventListener {
        fun onConnected()
        fun onSetupComplete()
        fun onAudioChunkReceived(audioData: ByteArray)
        fun onTranscriptChunkReceived(text: String)
        fun onUserTranscriptDelta(delta: String) {}
        fun onUserTranscriptCompleted(fullTranscript: String) {}
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
    private var isIntentionallyClosed = false
    private var sentAudioChunkCount = 0
    private var receivedAudioChunkCount = 0

    fun isReady(): Boolean = isConnected && isSetupDone

    fun connect(systemInstructionText: String) {
        if (apiKey.isBlank()) {
            LiveDebugLogger.log("Connect failed: Gemini API key is missing", LiveDebugLogger.LogLevel.ERROR)
            listener?.onError("Gemini API key is missing. Please set it in Settings.")
            return
        }

        isIntentionallyClosed = false
        sentAudioChunkCount = 0
        receivedAudioChunkCount = 0

        val normalizedModel = if (model.startsWith("models/")) model else "models/$model"
        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

        Log.i(tag, "Connecting to Gemini Live WebSocket ($normalizedModel)...")
        LiveDebugLogger.setWsStatus("Connecting")
        LiveDebugLogger.log("WebSocket connecting to $normalizedModel...", LiveDebugLogger.LogLevel.INFO)

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(tag, "WebSocket opened successfully")
                isConnected = true
                LiveDebugLogger.setWsStatus("Connected")
                LiveDebugLogger.log("WebSocket opened (HTTP ${response.code}, msg='${response.message}')", LiveDebugLogger.LogLevel.SUCCESS)
                listener?.onConnected()

                // Send BidiGenerateContentSetup message
                sendSetupMessage(normalizedModel, systemInstructionText)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(tag, "WebSocket closing: code=$code, reason=$reason")
                val reasonText = if (reason.isBlank()) "<empty>" else reason
                LiveDebugLogger.log("WebSocket closing: code=$code, reason='$reasonText'", LiveDebugLogger.LogLevel.WARN)
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(tag, "WebSocket closed: code=$code, reason=$reason")
                isConnected = false
                isSetupDone = false
                LiveDebugLogger.setWsStatus("Disconnected")
                val reasonText = if (reason.isBlank()) "<empty>" else reason
                LiveDebugLogger.log("WebSocket closed: code=$code, reason='$reasonText'", LiveDebugLogger.LogLevel.WARN)
                if (!isIntentionallyClosed) {
                    listener?.onDisconnected("code=$code, reason='$reasonText'")
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                setupTimeoutJob?.cancel()
                isConnected = false
                isSetupDone = false
                LiveDebugLogger.setWsStatus("Disconnected")
                if (isIntentionallyClosed) {
                    Log.i(tag, "Ignoring expected closure exception during intentional disconnect: ${t.localizedMessage}")
                    return
                }
                Log.e(tag, "WebSocket failure: ${t.localizedMessage}", t)
                val respBody = if (response != null) {
                    try { response.body?.string() } catch (_: Exception) { null }
                } else null
                val errorMsg = if (response != null) {
                    "WebSocket failure (HTTP ${response.code} ${response.message}): ${respBody ?: t.localizedMessage ?: t.javaClass.simpleName}"
                } else {
                    "WebSocket failure: ${t.javaClass.simpleName} - ${t.localizedMessage ?: "Connection error"}"
                }
                LiveDebugLogger.log(errorMsg, LiveDebugLogger.LogLevel.ERROR)
                listener?.onError(errorMsg)
            }
        })
    }

    private fun sendSetupMessage(modelName: String, systemInstructionText: String) {
        val setupPayload = mutableMapOf<String, Any>(
            "model" to modelName,
            "generationConfig" to mapOf(
                "responseModalities" to listOf("AUDIO"),
                "speechConfig" to mapOf(
                    "voiceConfig" to mapOf(
                        "prebuiltVoiceConfig" to mapOf(
                            "voiceName" to voiceName
                        )
                    )
                )
            )
        )

        if (systemInstructionText.isNotBlank()) {
            setupPayload["systemInstruction"] = mapOf(
                "parts" to listOf(
                    mapOf("text" to systemInstructionText)
                )
            )
        }

        val fullMessage = mapOf("setup" to setupPayload)
        val json = mapAdapter.toJson(fullMessage)
        Log.d(tag, "Sending setup message: $json")
        LiveDebugLogger.log("Setup message sent for $modelName (Voice: $voiceName)", LiveDebugLogger.LogLevel.INFO)
        LiveDebugLogger.log("Setup JSON payload:\n$json", LiveDebugLogger.LogLevel.DATA)
        
        // Start 12s setup watchdog to detect unavailable/denied models
        setupTimeoutJob?.cancel()
        setupTimeoutJob = clientScope.launch {
            delay(12_000L)
            if (!isSetupDone && isConnected) {
                Log.e(tag, "Setup timeout: setupComplete not received within 12s")
                val errorMsg = "Setup timeout (12s): No setupComplete received from server"
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
        val realtimeInput = mapOf(
            "realtimeInput" to mapOf(
                "mediaChunks" to listOf(
                    mapOf(
                        "mimeType" to "audio/pcm;rate=16000",
                        "data" to base64Data
                    )
                )
            )
        )

        val json = mapAdapter.toJson(realtimeInput)
        val sent = webSocket?.send(json) ?: false
        if (sent) {
            sentAudioChunkCount++
            if (sentAudioChunkCount % 20 == 1) { // Log periodically to avoid flooding
                LiveDebugLogger.log("Audio chunk sent (#$sentAudioChunkCount): ${pcm16kChunk.size} bytes", LiveDebugLogger.LogLevel.DATA)
            }
        }
    }

    fun sendTextMessage(userPrompt: String) {
        if (!isReady() || webSocket == null || userPrompt.isBlank()) return

        val contentMessage = mapOf(
            "clientContent" to mapOf(
                "turns" to listOf(
                    mapOf(
                        "role" to "user",
                        "parts" to listOf(
                            mapOf("text" to userPrompt)
                        )
                    )
                ),
                "turnComplete" to true
            )
        )

        val json = mapAdapter.toJson(contentMessage)
        LiveDebugLogger.log("Text message sent: \"$userPrompt\"", LiveDebugLogger.LogLevel.INFO)
        webSocket?.send(json)
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val root = mapAdapter.fromJson(jsonText) ?: return

            // 1. Setup complete
            if (root.containsKey("setupComplete")) {
                Log.i(tag, "Gemini Live Setup Completed!")
                setupTimeoutJob?.cancel()
                setupTimeoutJob = null
                isSetupDone = true
                LiveDebugLogger.log("Setup complete received from Gemini Live", LiveDebugLogger.LogLevel.SUCCESS)
                listener?.onSetupComplete()
                return
            }

            // 2. Error message
            if (root.containsKey("error")) {
                setupTimeoutJob?.cancel()
                setupTimeoutJob = null
                val errorObj = root["error"] as? Map<*, *>
                val msg = errorObj?.get("message") as? String ?: "Unknown Live API error"
                Log.e(tag, "Gemini Live returned error: $msg ($jsonText)")
                LiveDebugLogger.log("Error from server payload: $jsonText", LiveDebugLogger.LogLevel.ERROR)
                listener?.onError(msg)
                return
            }

            // 3. Server content stream
            val serverContent = root["serverContent"] as? Map<*, *>
            if (serverContent != null) {
                val interrupted = serverContent["interrupted"] as? Boolean ?: false
                if (interrupted) {
                    Log.d(tag, "Server indicated interruption")
                    LiveDebugLogger.log("Model turn interrupted by user voice", LiveDebugLogger.LogLevel.WARN)
                    listener?.onInterrupted()
                }

                val modelTurn = serverContent["modelTurn"] as? Map<*, *>
                if (modelTurn != null) {
                    val parts = modelTurn["parts"] as? List<*>
                    parts?.forEach { partObj ->
                        val partMap = partObj as? Map<*, *> ?: return@forEach

                        // Handle text transcript
                        val text = partMap["text"] as? String
                        if (!text.isNullOrEmpty()) {
                            LiveDebugLogger.log("Transcript received: \"$text\"", LiveDebugLogger.LogLevel.INFO)
                            listener?.onTranscriptChunkReceived(text)
                        }

                        // Handle PCM audio chunk
                        val inlineData = partMap["inlineData"] as? Map<*, *>
                        if (inlineData != null) {
                            val base64Data = inlineData["data"] as? String
                            if (!base64Data.isNullOrEmpty()) {
                                val audioBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                                receivedAudioChunkCount++
                                if (receivedAudioChunkCount % 15 == 1) {
                                    LiveDebugLogger.log("Audio chunk received (#$receivedAudioChunkCount): ${audioBytes.size} bytes", LiveDebugLogger.LogLevel.DATA)
                                }
                                listener?.onAudioChunkReceived(audioBytes)
                            }
                        }
                    }
                }

                val turnComplete = serverContent["turnComplete"] as? Boolean ?: false
                if (turnComplete) {
                    Log.d(tag, "Turn complete signal received")
                    LiveDebugLogger.log("Turn complete received", LiveDebugLogger.LogLevel.SUCCESS)
                    listener?.onTurnComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse incoming WebSocket message: $jsonText", e)
            LiveDebugLogger.log("Parse error: ${e.localizedMessage}", LiveDebugLogger.LogLevel.ERROR)
        }
    }

    fun disconnect() {
        isIntentionallyClosed = true
        listener = null
        try {
            setupTimeoutJob?.cancel()
            setupTimeoutJob = null
            webSocket?.cancel()
            webSocket?.close(1000, "User ended session")
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebSocket", e)
        } finally {
            webSocket = null
            isConnected = false
            isSetupDone = false
            LiveDebugLogger.setWsStatus("Disconnected")
            LiveDebugLogger.log("Disconnected: WebSocket session closed", LiveDebugLogger.LogLevel.INFO)
        }
    }
}
