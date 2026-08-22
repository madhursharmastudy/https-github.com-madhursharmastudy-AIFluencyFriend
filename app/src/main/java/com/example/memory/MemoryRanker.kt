package com.example.memory

import com.example.data.database.entity.MemoryFact

class MemoryRanker {

    fun rankMemories(
        memories: List<MemoryFact>,
        currentSessionId: String,
        currentTime: Long = System.currentTimeMillis()
    ): List<MemoryFact> {
        return memories.map { memory ->
            // Recency score (Normalized between 0 and 1, based on days)
            val timeDiffDays = (currentTime - memory.lastReferencedAt).coerceAtLeast(0) / (1000 * 60 * 60 * 24.0)
            val recencyScore = (1.0 / (1.0 + timeDiffDays)).toFloat()

            // Emotion weight (High-emotion memories get minor boost, normalized)
            val emotionWeight = when (memory.emotion.lowercase()) {
                "sad", "fear", "anxious", "happy", "excitement" -> 1.0f
                else -> 0.5f
            }

            // Reference Count component (Boost frequently reference topics, capped at 10)
            val normalizedReferenceCount = (memory.referenceCount / 10.0f).coerceAtMost(1.0f)

            // Importance score from the database (Usually 1-5, normalized to 0-1)
            val normalizedImportance = (memory.importance / 5.0f).coerceAtMost(1.0f)

            // Final score formula: Importance * 0.4 + Recency * 0.3 + Emotion Weight * 0.2 + Reference Count * 0.1
            val finalScore = (normalizedImportance * 0.4f) +
                    (recencyScore * 0.3f) +
                    (emotionWeight * 0.2f) +
                    (normalizedReferenceCount * 0.1f)

            RankedMemory(memory, finalScore)
        }
        .sortedByDescending { it.score }
        .map { it.memory }
        .take(5)
    }

    private data class RankedMemory(val memory: MemoryFact, val score: Float)
}
