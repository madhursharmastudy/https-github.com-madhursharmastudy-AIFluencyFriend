package com.example.english

import android.util.Log

class EnglishEngine {

    // Evaluate user speech or text and suggest invisible or active corrections
    fun evaluateGrammarAndSuggestCorrection(userInput: String): GrammarCorrection {
        val inputCleaned = userInput.trim().lowercase()
        return when {
            inputCleaned.contains("i go market yesterday") -> 
                GrammarCorrection("I went to the market yesterday.", "Corrected tense of 'go' to 'went' and added missing preposition 'to'.", "went to the market")
            inputCleaned.contains("she don't like") -> 
                GrammarCorrection("She doesn't like.", "Subject-verb agreement: 'she' takes singular 'doesn't'.", "doesn't like")
            inputCleaned.contains("more better") -> 
                GrammarCorrection("much better", "Double comparative error. Avoid using 'more' with comparative adjectives.", "much better")
            else -> 
                GrammarCorrection(userInput, "", "")
        }
    }

    // Naturally suggest stronger, more expressive synonyms
    fun suggestVocabularyExpansion(userInput: String): String {
        val inputCleaned = userInput.trim().lowercase()
        return when {
            inputCleaned.contains("very happy") || inputCleaned.contains("very glad") -> "delighted"
            inputCleaned.contains("very angry") || inputCleaned.contains("so angry") -> "furious"
            inputCleaned.contains("very tired") || inputCleaned.contains("so tired") -> "exhausted"
            inputCleaned.contains("very bad") -> "terrible"
            inputCleaned.contains("good choice") -> "excellent decision"
            else -> ""
        }
    }

    // Assess speaking fluency score on a scale of 0-100
    fun calculateFluencyScore(
        durationSec: Int,
        totalWordCount: Int,
        hesitationCount: Int
    ): Float {
        if (durationSec <= 0 || totalWordCount <= 0) return 50.0f
        
        // Words per minute (WPM)
        val wpm = (totalWordCount.toFloat() / durationSec) * 60f
        
        // Target WPM for natural conversational English is around 110-150 words
        val wpmScore = when {
            wpm in 110.0f..150.0f -> 100.0f
            wpm > 150.0f -> 85.0f // talking too fast
            else -> (wpm / 110.0f) * 100.0f // slow / developing fluency
        }

        // Penalty for excessive hesitations (pauses/stumbles)
        val hesitationScore = (100.0f - (hesitationCount * 5.0f)).coerceAtLeast(20.0f)

        // Weighted balance of WPM stability and pacing continuity
        return (wpmScore * 0.6f + hesitationScore * 0.4f).coerceIn(0f, 100f)
    }

    // Assess confidence score based on willingness to formulate long responses
    fun calculateConfidenceScore(
        totalTurns: Int,
        averageResponseLength: Float
    ): Float {
        if (totalTurns <= 0) return 50.0f
        
        // Long sentences reflect high articulation confidence
        val lengthScore = (averageResponseLength / 15f) * 100f
        val turnsScore = (totalTurns * 8.0f) // rewards engaging dialogues
        
        return (lengthScore * 0.7f + turnsScore * 0.3f).coerceIn(0f, 100f)
    }

    data class GrammarCorrection(
        val correctedPhrase: String,
        val explanation: String,
        val correctionSegment: String
    ) {
        val hasCorrection: Boolean get() = correctedPhrase != "" && explanation != ""
    }
}
