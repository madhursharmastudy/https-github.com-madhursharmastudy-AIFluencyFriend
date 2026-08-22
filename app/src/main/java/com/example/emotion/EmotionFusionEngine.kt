package com.example.emotion

class EmotionFusionEngine {

    fun fuseEmotions(
        faceEmotion: String, faceConfidence: Float,
        voiceEmotion: String, voiceConfidence: Float,
        contextEmotion: String, contextConfidence: Float
    ): FusedEmotion {
        // Source weights: Voice = 40%, Context = 40%, Face = 20%
        val weights = mapOf(
            "face" to 0.20f,
            "voice" to 0.40f,
            "context" to 0.40f
        )

        val emotionScores = mutableMapOf<String, Float>()

        // Helper to distribute score to map
        fun addScore(emotion: String, source: String, confidence: Float) {
            val weight = weights[source] ?: 0.33f
            val calculatedScore = confidence * weight
            val existing = emotionScores[emotion] ?: 0.0f
            emotionScores[emotion] = existing + calculatedScore
        }

        addScore(faceEmotion.lowercase(), "face", faceConfidence)
        addScore(voiceEmotion.lowercase(), "voice", voiceConfidence)
        addScore(contextEmotion.lowercase(), "context", contextConfidence)

        // Find the absolute highest score among all merged candidates
        val winningEntry = emotionScores.maxByOrNull { it.value }
        val winningEmotion = winningEntry?.key ?: "neutral"
        // Fused metric is normalized out of peak weights
        val fusedConfidence = ((winningEntry?.value ?: 0.5f) * 100f).coerceAtMost(100f)

        return FusedEmotion(
            emotion = winningEmotion.capitalize(),
            confidence = fusedConfidence.toInt()
        )
    }

    data class FusedEmotion(val emotion: String, val confidence: Int)

    private fun String.capitalize(): String {
        return replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
