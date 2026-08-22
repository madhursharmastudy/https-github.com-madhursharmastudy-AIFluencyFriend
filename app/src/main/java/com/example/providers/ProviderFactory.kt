package com.example.providers

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProviderFactory(private val context: Context) {

    private val _currentProviderType = MutableStateFlow("Gemini")
    val currentProviderType: StateFlow<String> = _currentProviderType.asStateFlow()

    private val geminiProvider = GeminiProvider()
    private val openAIProvider = OpenAIProvider()
    private val claudeProvider = ClaudeProvider()

    fun getActiveProvider(): AIProvider {
        return when (selectedProvider) {
            "OpenAI" -> openAIProvider
            "Claude" -> claudeProvider
            else -> geminiProvider
        }
    }

    fun getActiveProviderName(): String {
        return selectedProvider
    }

    fun setProvider(providerName: String) {
        if (providerName in listOf("Gemini", "OpenAI", "Claude")) {
            selectedProvider = providerName
            _currentProviderType.value = providerName
        }
    }

    companion object {
        private var selectedProvider = "Gemini"
        
        val geminiProvider = GeminiProvider()
        val openAIProvider = OpenAIProvider()
        val claudeProvider = ClaudeProvider()

        fun getProvider(type: String = "Gemini"): AIProvider {
            return when (type.lowercase()) {
                "openai" -> OpenAIProvider()
                "claude" -> ClaudeProvider()
                else -> GeminiProvider()
            }
        }
    }
}
