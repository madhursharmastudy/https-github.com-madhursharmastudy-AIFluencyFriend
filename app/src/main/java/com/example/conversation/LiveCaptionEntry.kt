package com.example.conversation

import java.util.UUID

/**
 * Represents a real-time live caption line (User or Companion) for on-screen overlay display.
 */
data class LiveCaptionEntry(
    val id: String = UUID.randomUUID().toString(),
    val sender: String, // "user" or "companion"
    val text: String,
    val isFinal: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
