package com.example.providers

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiProvider : AIProvider {

    private val tag = "GeminiProvider"
    var customApiKey: String? = null
    var customModelName: String = "gemini-2.5-flash"
    var voiceName: String = "Aoede"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private var activeLiveClient: GeminiLiveClient? = null

    fun getActiveApiKey(): String {
        val custom = customApiKey?.trim()
        if (!custom.isNullOrEmpty()) {
            return custom
        }
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotEmpty() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun isKeyConfigured(): Boolean {
        return getActiveApiKey().isNotEmpty()
    }

    override suspend fun connect() {
        Log.d(tag, "Connected to Gemini Provider")
    }

    override suspend fun disconnect() {
        Log.d(tag, "Disconnected from Gemini Provider")
        activeLiveClient?.disconnect()
        activeLiveClient = null
    }

    fun createLiveClient(
        systemInstruction: String,
        listener: GeminiLiveClient.LiveEventListener
    ): GeminiLiveClient {
        val apiKey = getActiveApiKey()
        val liveModel = "gemini-2.5-flash-native-audio-preview-12-2025"
        val liveClient = GeminiLiveClient(apiKey = apiKey, model = liveModel, voiceName = voiceName)
        liveClient.listener = listener
        liveClient.connect(systemInstruction)
        activeLiveClient = liveClient
        return liveClient
    }

    private fun normalizeModel(model: String): String {
        val clean = model.trim()
        return when {
            clean.isEmpty() -> "gemini-2.5-flash"
            clean.startsWith("models/") -> clean.removePrefix("models/")
            clean == "gemini-1.5-flash" -> "gemini-2.5-flash"
            clean == "gemini-1.5-pro" -> "gemini-2.5-flash"
            else -> clean
        }
    }

    suspend fun testConnection(apiKeyToTest: String, modelToTest: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKeyToTest.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be empty."))
        }
        val model = normalizeModel(modelToTest)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$cleanKey"

        val requestPayload = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to "Hello! Reply with 'OK' if you can hear me.")
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.1,
                "maxOutputTokens" to 20
            )
        )

        val jsonAdapter = moshi.adapter(Map::class.java)
        val jsonString = jsonAdapter.toJson(requestPayload)
        val requestBody = jsonString.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(tag, "Gemini Test Connection Error (${response.code}): $bodyString")
                    val msg = parseErrorMessage(bodyString) ?: "HTTP ${response.code}"
                    return@withContext Result.failure(Exception("Gemini error ($msg)"))
                }
                return@withContext Result.success("Connection successful! Gemini ($model) is active and responsive.")
            }
        } catch (e: Exception) {
            Log.e(tag, "Gemini Test Connection Failed", e)
            return@withContext Result.failure(e)
        }
    }

    override suspend fun sendMessage(prompt: String, systemInstruction: String): String = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey()
        if (apiKey.isEmpty()) {
            Log.e(tag, "Gemini API Key is missing!")
            return@withContext "Hi there! Please enter your Gemini API Key in Settings to start our conversation."
        }

        val model = normalizeModel(customModelName)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestPayload = mutableMapOf<String, Any>(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to prompt)
                    )
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.7,
                "maxOutputTokens" to 500
            )
        )

        if (systemInstruction.isNotBlank()) {
            requestPayload["systemInstruction"] = mapOf(
                "parts" to listOf(
                    mapOf("text" to systemInstruction)
                )
            )
        }

        val jsonAdapter = moshi.adapter(Map::class.java)
        val jsonString = jsonAdapter.toJson(requestPayload)
        val requestBody = jsonString.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(tag, "Gemini API Error (${response.code}): $bodyString")
                    val parsedError = parseErrorMessage(bodyString)
                    return@withContext if (parsedError != null) {
                        "Gemini API issue (${response.code}): $parsedError. Please verify your API key in Settings."
                    } else {
                        "I had trouble connecting (${response.code}). Please check your API key or model in Settings."
                    }
                }

                val responseMap = moshi.adapter(Map::class.java).fromJson(bodyString)
                val candidates = responseMap?.get("candidates") as? List<*>
                val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
                val content = firstCandidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val firstPart = parts?.firstOrNull() as? Map<*, *>
                val text = firstPart?.get("text") as? String

                return@withContext text?.trim() ?: "I heard you clearly, let's keep practicing!"
            }
        } catch (e: Exception) {
            Log.e(tag, "Gemini Network Call Failed", e)
            return@withContext "Network connection failed: ${e.localizedMessage ?: "Unable to reach Gemini"}. Please check your internet connection."
        }
    }

    private fun parseErrorMessage(json: String): String? {
        return try {
            val map = moshi.adapter(Map::class.java).fromJson(json)
            val errorObj = map?.get("error") as? Map<*, *>
            errorObj?.get("message") as? String
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun streamAudio(audioData: ByteArray): Flow<ByteArray> = callbackFlow {
        val apiKey = getActiveApiKey()
        if (apiKey.isBlank()) {
            close(IllegalStateException("Gemini API key missing"))
            return@callbackFlow
        }

        val client = GeminiLiveClient(
            apiKey = apiKey,
            model = "gemini-2.5-flash-native-audio-preview-12-2025",
            voiceName = voiceName
        )

        client.listener = object : GeminiLiveClient.LiveEventListener {
            override fun onConnected() {}
            override fun onSetupComplete() {
                client.sendAudioChunk(audioData)
            }

            override fun onAudioChunkReceived(audioData: ByteArray) {
                trySend(audioData)
            }

            override fun onTranscriptChunkReceived(text: String) {}

            override fun onTurnComplete() {
                close()
            }

            override fun onInterrupted() {
                close()
            }

            override fun onError(error: String) {
                close(Exception(error))
            }

            override fun onDisconnected(reason: String) {
                close()
            }
        }

        client.connect("")

        awaitClose {
            client.disconnect()
        }
    }
}
