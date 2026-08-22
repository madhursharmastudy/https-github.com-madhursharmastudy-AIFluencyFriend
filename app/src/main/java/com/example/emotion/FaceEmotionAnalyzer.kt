package com.example.emotion

import android.util.Log

class FaceEmotionAnalyzer {

    fun analyzeFaceState(
        isSmileDetected: Boolean,
        isBrowFurrowed: Boolean,
        isEyeClosed: Boolean
    ): EmotionState {
        val emotion = when {
            isSmileDetected && !isBrowFurrowed -> "happy"
            isBrowFurrowed && !isSmileDetected -> "frustrated"
            isBrowFurrowed && isSmileDetected -> "excited"
            isEyeClosed && isBrowFurrowed -> "stressed"
            else -> "neutral"
        }
        val confidence = if (isSmileDetected || isBrowFurrowed) 0.85f else 0.5f
        return EmotionState(emotion, confidence)
    }

    data class EmotionState(val emotion: String, val confidence: Float)
}
