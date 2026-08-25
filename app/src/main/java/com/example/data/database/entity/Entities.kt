package com.example.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val userId: String,
    val name: String,
    val age: Int?,
    val gender: String?,
    val nativeLanguage: String,
    val englishLevel: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "memory_facts")
data class MemoryFact(
    @PrimaryKey val memoryId: String,
    val fact: String,
    val category: String,
    val emotion: String,
    val importance: Int,
    val confidence: Float,
    val createdAt: Long,
    val lastReferencedAt: Long,
    val referenceCount: Int,
    val active: Boolean
)

@Entity(tableName = "memory_references")
data class MemoryReference(
    @PrimaryKey val id: String,
    val memoryId: String,
    val sessionId: String,
    val timestamp: Long
)

@Entity(tableName = "relationship_states")
data class RelationshipState(
    @PrimaryKey val userId: String,
    val relationshipLevel: Int,
    val trustScore: Float,
    val engagementScore: Float,
    val warmthScore: Float,
    val humorScore: Float,
    val companionshipScore: Float,
    val totalConversations: Int,
    val updatedAt: Long
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSec: Int,
    val dominantEmotion: String,
    val averageConfidence: Float,
    val personalityUsed: String,
    val totalMessages: Int
)

@Entity(tableName = "session_messages")
data class SessionMessage(
    @PrimaryKey val messageId: String,
    val sessionId: String,
    val sender: String,
    val content: String,
    val timestamp: Long
)

@Entity(tableName = "emotion_history")
data class EmotionHistory(
    @PrimaryKey val emotionId: String,
    val sessionId: String,
    val detectedEmotion: String,
    val confidence: Float,
    val source: String,
    val timestamp: Long
)

@Entity(tableName = "personality_profiles")
data class PersonalityProfile(
    @PrimaryKey val personalityId: String,
    val personalityName: String,
    val humorLevel: Int,
    val sarcasmLevel: Int,
    val affectionLevel: Int,
    val curiosityLevel: Int,
    val talkativeLevel: Int,
    val teasingLevel: Int
)

@Entity(tableName = "english_progress")
data class EnglishProgress(
    @PrimaryKey val progressId: String,
    val date: Long,
    val speakingDurationSec: Int,
    val englishUsagePercent: Float,
    val vocabularyScore: Float,
    val grammarScore: Float,
    val confidenceScore: Float,
    val fluencyScore: Float
)

@Entity(tableName = "vocabulary_growth")
data class VocabularyGrowth(
    @PrimaryKey val wordId: String,
    val word: String,
    val meaning: String,
    val learnedAt: Long,
    val usageCount: Int,
    val masteryLevel: Int
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey val goalId: String,
    val title: String,
    val description: String,
    val category: String,
    val progress: Float,
    val completed: Boolean,
    val createdAt: Long
)

@Entity(tableName = "achievements")
data class Achievement(
    @PrimaryKey val achievementId: String,
    val title: String,
    val description: String,
    val unlockedAt: Long
)

@Entity(tableName = "analytics")
data class Analytics(
    @PrimaryKey val analyticsId: String,
    val totalSessions: Int,
    val totalMinutes: Int,
    val averageSessionLength: Float,
    val averageConfidence: Float,
    val averageFluency: Float,
    val vocabularyGrowth: Int
)

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey val userId: String,
    val selectedPersonality: String,
    val cameraEnabled: Boolean,
    val voiceEmotionEnabled: Boolean,
    val faceEmotionEnabled: Boolean,
    val englishCorrectionEnabled: Boolean,
    val notificationsEnabled: Boolean,
    val geminiApiKey: String = "",
    val selectedModel: String = "gemini-2.5-flash",
    val inworldApiKey: String = "",
    val voiceProvider: String = "Gemini"
)
