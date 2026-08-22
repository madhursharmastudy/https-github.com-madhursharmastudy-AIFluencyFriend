package com.example.providers

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiProvider : AIProvider {

    private val tag = "GeminiProvider"
    var customApiKey: String? = null
    var customModelName: String = "gemini-2.5-flash"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

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
    }

    suspend fun testConnection(apiKeyToTest: String, modelToTest: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKeyToTest.trim()
        if (cleanKey.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("API Key cannot be empty."))
        }
        val model = modelToTest.trim().ifEmpty { "gemini-2.5-flash" }
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
                    Log.e(tag, "Gemini Test Connection Error ($response): $bodyString")
                    return@withContext Result.failure(Exception("Gemini API returned error code ${response.code}: $bodyString"))
                }
                return@withContext Result.success("Connection successful! Gemini ($model) is active and ready.")
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
            return@withContext "Hi there! To start speaking with me, please enter your Gemini API Key in the Settings screen or tap the API setup prompt above."
        }

        val model = customModelName.trim().ifEmpty { "gemini-2.5-flash" }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestPayload = mapOf(
            "contents" to listOf(
                mapOf(
                    "role" to "user",
                    "parts" to listOf(
                        mapOf("text" to prompt)
                    )
                )
            ),
            "systemInstruction" to mapOf(
                "parts" to listOf(
                    mapOf("text" to systemInstruction)
                )
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.7,
                "responseMimeType" to "text/plain"
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
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e(tag, "Gemini API Error ($response): $errorBody")
                    return@withContext "I encountered a connection issue (${response.code}). Please verify your Gemini API key in Settings."
                }

                val bodyString = response.body?.string() ?: ""
                val responseMap = moshi.adapter(Map::class.java).fromJson(bodyString)
                
                val candidates = responseMap?.get("candidates") as? List<*>
                val firstCandidate = candidates?.firstOrNull() as? Map<*, *>
                val content = firstCandidate?.get("content") as? Map<*, *>
                val parts = content?.get("parts") as? List<*>
                val firstPart = parts?.firstOrNull() as? Map<*, *>
                val text = firstPart?.get("text") as? String

                return@withContext text ?: "I am listening closely, tell me more!"
            }
        } catch (e: IOException) {
            Log.e(tag, "Network call failed", e)
            return@withContext "Sorry, I had trouble reaching the Gemini service. Please check your internet connection and API key."
        }
    }

    override suspend fun streamAudio(audioData: ByteArray): Flow<ByteArray> = flow {
        emit(ByteArray(0))
    }
}
