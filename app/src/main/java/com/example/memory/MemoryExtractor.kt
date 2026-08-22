package com.example.memory

import android.util.Log
import com.example.data.database.entity.MemoryFact
import com.example.providers.AIProvider
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.UUID

class MemoryExtractor(private val provider: AIProvider) {

    private val tag = "MemoryExtractor"
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()

    suspend fun extractAndSaveMemories(
        conversationLogString: String,
        onMemoryExtracted: suspend (MemoryFact) -> Unit
    ) {
        val prompt = """
            Analyze the following conversation transcript between a user and an AI companion.
            Identify and extract any important facts, goals, preferences, interests, or major life events mentioned about the user.
            
            Return the results strictly as a valid JSON array of objects representing facts, matching this exact schema:
            [
              {
                "fact": "A complete sentence describing the fact, e.g. 'User has an upcoming job interview next Thursday.'",
                "category": "One of: PERSONAL, WORK, STUDY, GOALS, HOBBY, FAMILY, HEALTH, RELATIONSHIP, FEAR, DREAM, ACHIEVEMENT",
                "emotion": "The emotion associated with this fact, e.g. 'anxious', 'happy', 'neutral'",
                "importance": an integer from 1 to 5 representing importance
              }
            ]
            
            Return NOTHING else except the JSON array. Do not enclose it in markdown blocks or write any intro/outro text.
            TRANSCRIPT:
            $conversationLogString
        """.trimIndent()

        try {
            val responseText = provider.sendMessage(prompt, "You are a database parser helper. Return valid JSON only.")
            
            // Strip potential markdown envelopes if they leak
            var cleanedJson = responseText.trim()
            if (cleanedJson.startsWith("```json")) {
                cleanedJson = cleanedJson.substringAfter("```json").substringBeforeLast("```").trim()
            } else if (cleanedJson.startsWith("```")) {
                cleanedJson = cleanedJson.substringAfter("```").substringBeforeLast("```").trim()
            }

            if (cleanedJson.isEmpty() || !cleanedJson.startsWith("[")) {
                Log.d(tag, "No memories parsed or invalid JSON received.")
                return
            }

            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, Map::class.java)
            val adapter = moshi.adapter<List<Map<String, Any>>>(listType)
            val items = adapter.fromJson(cleanedJson)

            items?.forEach { item ->
                val factText = item["fact"] as? String ?: return@forEach
                val categoryText = item["category"] as? String ?: "PERSONAL"
                val emotionText = item["emotion"] as? String ?: "neutral"
                val importanceValue = (item["importance"] as? Double)?.toInt() ?: 3

                val memory = MemoryFact(
                    memoryId = UUID.randomUUID().toString(),
                    fact = factText,
                    category = categoryText.uppercase(),
                    emotion = emotionText,
                    importance = importanceValue,
                    confidence = 0.9f,
                    createdAt = System.currentTimeMillis(),
                    lastReferencedAt = System.currentTimeMillis(),
                    referenceCount = 1,
                    active = true
                )
                onMemoryExtracted(memory)
                Log.d(tag, "Extracted new memory fact: $factText")
            }

        } catch (e: Exception) {
            Log.e(tag, "Failed to extract memories", e)
        }
    }
}
