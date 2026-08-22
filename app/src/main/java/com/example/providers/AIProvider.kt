package com.example.providers

import kotlinx.coroutines.flow.Flow

interface AIProvider {
    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendMessage(prompt: String, systemInstruction: String): String
    suspend fun streamAudio(audioData: ByteArray): Flow<ByteArray>
}
