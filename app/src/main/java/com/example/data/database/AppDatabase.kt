package com.example.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.database.entity.*
import com.example.data.database.dao.*

@Database(
    entities = [
        UserProfile::class,
        MemoryFact::class,
        MemoryReference::class,
        RelationshipState::class,
        Session::class,
        SessionMessage::class,
        EmotionHistory::class,
        PersonalityProfile::class,
        EnglishProgress::class,
        VocabularyGrowth::class,
        Goal::class,
        Achievement::class,
        Analytics::class,
        Settings::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun memoryReferenceDao(): MemoryReferenceDao
    abstract fun relationshipStateDao(): RelationshipStateDao
    abstract fun sessionDao(): SessionDao
    abstract fun sessionMessageDao(): SessionMessageDao
    abstract fun emotionHistoryDao(): EmotionHistoryDao
    abstract fun personalityProfileDao(): PersonalityProfileDao
    abstract fun englishProgressDao(): EnglishProgressDao
    abstract fun vocabularyGrowthDao(): VocabularyGrowthDao
    abstract fun goalDao(): GoalDao
    abstract fun achievementDao(): AchievementDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun settingsDao(): SettingsDao
}
