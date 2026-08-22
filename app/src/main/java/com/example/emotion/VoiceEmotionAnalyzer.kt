package com.example.emotion

class VoiceEmotionAnalyzer {

    fun analyzeVoiceMetrics(
        volumeVariance: Float,
        pitchFrequencyHz: Float,
        hesitationCount: Int
    ): EmotionState {
        val emotion = when {
            volumeVariance > 500f && pitchFrequencyHz > 250f -> "excited"
            volumeVariance < 100f && hesitationCount > 3 -> "nervous"
            pitchFrequencyHz < 150f && volumeVariance < 50f -> "sad"
            hesitationCount > 5 -> "confusion"
            else -> "confident"
        }
        val confidence = (0.6f + (volumeVariance / 1000f)).coerceAtMost(0.95f)
        return EmotionState(emotion, confidence)
    }

    data class EmotionState(val emotion: String, val confidence: Float)
}
