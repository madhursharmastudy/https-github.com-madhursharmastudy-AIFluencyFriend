package com.example.providers

import android.util.Base64
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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

    fun isReady(): Boolean = isConnected && isSetupDone

    fun connect(systemInstructionText: String) {
        if (apiKey.isBlank()) {
            listener?.onError("Gemini API key is missing. Please set it in Settings.")
            return
        }

        disconnect()

        val normalizedModel = if (model.startsWith("models/")) model else "models/$model"
        val wsUrl = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$apiKey"

        Log.i(tag, "Connecting to Gemini Live WebSocket ($normalizedModel)...")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(tag, "WebSocket opened successfully")
                isConnected = true
                listener?.onConnected()

                // Send BidiGenerateContentSetup message
                sendSetupMessage(normalizedModel, systemInstructionText)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(tag, "WebSocket closing: code=$code, reason=$reason")
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(tag, "WebSocket closed: code=$code, reason=$reason")
                isConnected = false
                isSetupDone = false
                listener?.onDisconnected(reason)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.localizedMessage}", t)
                isConnected = false
                isSetupDone = false
                val errorMsg = if (response != null) {
                    val body = try { response.body?.string() } catch (_: Exception) { null }
                    "Gemini Live error (HTTP ${response.code}): ${body ?: t.localizedMessage}"
                } else {
                    t.localizedMessage ?: "Connection error to Gemini Live API"
                }
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
        webSocket?.send(json)
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
        webSocket?.send(json)
    }

    private fun handleIncomingMessage(jsonText: String) {
        try {
            val root = mapAdapter.fromJson(jsonText) ?: return

            // 1. Setup complete
            if (root.containsKey("setupComplete")) {
                Log.i(tag, "Gemini Live Setup Completed!")
                isSetupDone = true
                listener?.onSetupComplete()
                return
            }

            // 2. Error message
            if (root.containsKey("error")) {
                val errorObj = root["error"] as? Map<*, *>
                val msg = errorObj?.get("message") as? String ?: "Unknown Live API error"
                Log.e(tag, "Gemini Live returned error: $msg")
                listener?.onError(msg)
                return
            }

            // 3. Server content stream
            val serverContent = root["serverContent"] as? Map<*, *>
            if (serverContent != null) {
                val interrupted = serverContent["interrupted"] as? Boolean ?: false
                if (interrupted) {
                    Log.d(tag, "Server indicated interruption")
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
                            listener?.onTranscriptChunkReceived(text)
                        }

                        // Handle PCM audio chunk
                        val inlineData = partMap["inlineData"] as? Map<*, *>
                        if (inlineData != null) {
                            val base64Data = inlineData["data"] as? String
                            if (!base64Data.isNullOrEmpty()) {
                                val audioBytes = Base64.decode(base64Data, Base64.NO_WRAP)
                                listener?.onAudioChunkReceived(audioBytes)
                            }
                        }
                    }
                }

                val turnComplete = serverContent["turnComplete"] as? Boolean ?: false
                if (turnComplete) {
                    Log.d(tag, "Turn complete signal received")
                    listener?.onTurnComplete()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to parse incoming WebSocket message: $jsonText", e)
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "User ended session")
        } catch (e: Exception) {
            Log.e(tag, "Error closing WebSocket", e)
        } finally {
            webSocket = null
            isConnected = false
            isSetupDone = false
        }
    }
}
