package com.example.providers

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OpenAIProvider : AIProvider {
    override suspend fun connect() {}
    override suspend fun disconnect() {}
    override suspend fun sendMessage(prompt: String, systemInstruction: String): String {
        return "OpenAI Provider scaffolded for future support."
    }
    override suspend fun streamAudio(audioData: ByteArray): Flow<ByteArray> = flow {
        emit(ByteArray(0))
    }
}
