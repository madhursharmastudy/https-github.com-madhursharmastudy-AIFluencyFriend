package com.example.safety

class SafetyGuidanceEngine {

    fun checkSafetyTriggers(userInput: String): SafetyAdvice? {
        val cleaned = userInput.trim().lowercase()
        return when {
            // Medical topic triggers
            cleaned.contains("leg pain") || cleaned.contains("symptom") || cleaned.contains("chest pain") || cleaned.contains("sick") -> {
                SafetyAdvice(
                    topic = "MEDICAL",
                    guidance = "Several things can cause physical discomfort, including general fatigue, muscle strain, or minor inflammation. I recommend resting first and consulting with a healthcare professional to identify the exact cause safely.",
                    riskLevel = "LOW_MODERATE",
                    redirectRequired = false
                )
            }
            // Legal topic triggers
            cleaned.contains("sue someone") || cleaned.contains("lawsuit") || cleaned.contains("legal battle") || cleaned.contains("contract details") -> {
                SafetyAdvice(
                    topic = "LEGAL",
                    guidance = "Legal procedures heavily depend on regional jurisdictions, facts, and standard documentation procedures. I recommend gathering all your documentation together and consulting with a certified legal adviser before proceeding with any action.",
                    riskLevel = "LOW",
                    redirectRequired = false
                )
            }
            // High-risk safety topics (De-escalation)
            cleaned.contains("hurt myself") || cleaned.contains("suicide") || cleaned.contains("end my life") -> {
                SafetyAdvice(
                    topic = "HIGH_RISK_HARMS",
                    guidance = "I hear how much pain you are holding right now, and I want you to know you don't have to carry this alone. Please reach out to someone who can help you safely, such as a specialized counselor or local helpline. Your life has immense value, and there is help available.",
                    riskLevel = "HIGH",
                    redirectRequired = true
                )
            }
            else -> null
        }
    }

    data class SafetyAdvice(
        val topic: String,
        val guidance: String,
        val riskLevel: String,
        val redirectRequired: Boolean
    )
}
