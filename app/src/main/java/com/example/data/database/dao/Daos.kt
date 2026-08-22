package com.example.data.database.dao

import androidx.room.*
import com.example.data.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Update
    suspend fun updateUserProfile(profile: UserProfile)
}

@Dao
interface MemoryFactDao {
    @Query("SELECT * FROM memory_facts WHERE active = 1")
    fun getAllActiveMemories(): Flow<List<MemoryFact>>

    @Query("SELECT * FROM memory_facts WHERE memoryId = :id LIMIT 1")
    suspend fun getMemoryById(id: String): MemoryFact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryFact(memory: MemoryFact)

    @Update
    suspend fun updateMemoryFact(memory: MemoryFact)

    @Query("UPDATE memory_facts SET active = 0 WHERE memoryId = :id")
    suspend fun archiveMemoryFact(id: String)

    @Delete
    suspend fun deleteMemoryFact(memory: MemoryFact)
}

@Dao
interface MemoryReferenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryReference(reference: MemoryReference)

    @Query("SELECT * FROM memory_references WHERE memoryId = :memoryId")
    suspend fun getReferencesByMemoryId(memoryId: String): List<MemoryReference>
}

@Dao
interface RelationshipStateDao {
    @Query("SELECT * FROM relationship_states LIMIT 1")
    fun getRelationshipState(): Flow<RelationshipState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRelationshipState(state: RelationshipState)

    @Update
    suspend fun updateRelationshipState(state: RelationshipState)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): Session?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session)
}

@Dao
interface SessionMessageDao {
    @Query("SELECT * FROM session_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<SessionMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: SessionMessage)
}

@Dao
interface EmotionHistoryDao {
    @Query("SELECT * FROM emotion_history ORDER BY timestamp DESC")
    fun getFullEmotionHistory(): Flow<List<EmotionHistory>>

    @Query("SELECT * FROM emotion_history WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun getEmotionHistoryForSession(sessionId: String): Flow<List<EmotionHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmotionHistory(history: EmotionHistory)
}

@Dao
interface PersonalityProfileDao {
    @Query("SELECT * FROM personality_profiles WHERE personalityId = :id LIMIT 1")
    suspend fun getPersonalityById(id: String): PersonalityProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersonality(personality: PersonalityProfile)
}

@Dao
interface EnglishProgressDao {
    @Query("SELECT * FROM english_progress ORDER BY date DESC")
    fun getProgressTrends(): Flow<List<EnglishProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: EnglishProgress)
}

@Dao
interface VocabularyGrowthDao {
    @Query("SELECT * FROM vocabulary_growth ORDER BY learnedAt DESC")
    fun getLearnedVocabulary(): Flow<List<VocabularyGrowth>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(word: VocabularyGrowth)

    @Update
    suspend fun updateVocabulary(word: VocabularyGrowth)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt DESC")
    fun getAllGoals(): Flow<List<Goal>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal)

    @Update
    suspend fun updateGoal(goal: Goal)
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY unlockedAt DESC")
    fun getUnlockedAchievements(): Flow<List<Achievement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: Achievement)
}

@Dao
interface AnalyticsDao {
    @Query("SELECT * FROM analytics LIMIT 1")
    fun getAnalytics(): Flow<Analytics?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalytics(analytics: Analytics)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings LIMIT 1")
    fun getSettingsFlow(): Flow<Settings?>

    @Query("SELECT * FROM settings LIMIT 1")
    suspend fun getSettings(): Settings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: Settings)
}
