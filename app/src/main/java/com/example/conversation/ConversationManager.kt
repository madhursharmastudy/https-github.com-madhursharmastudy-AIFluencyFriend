package com.example.conversation

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.database.entity.Session
import com.example.data.database.entity.SessionMessage
import com.example.emotion.EmotionFusionEngine
import com.example.emotion.FaceEmotionAnalyzer
import com.example.emotion.VoiceEmotionAnalyzer
import com.example.english.EnglishEngine
import com.example.memory.MemoryExtractor
import com.example.memory.MemoryRanker
import com.example.providers.AIProvider
import com.example.relationship.RelationshipManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class ConversationManager(
    private val context: Context,
    private val database: AppDatabase,
    private val aiProvider: AIProvider
) {
    private val tag = "ConversationManager"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    // Sub-engine instantiations
    private val contextBuilder = ContextBuilder()
    private val memoryRanker = MemoryRanker()
    private val memoryExtractor = MemoryExtractor(aiProvider)
    private val faceEmotionAnalyzer = FaceEmotionAnalyzer()
    private val voiceEmotionAnalyzer = VoiceEmotionAnalyzer()
    private val emotionFusionEngine = EmotionFusionEngine()
    private val relationshipManager = RelationshipManager()
    private val englishEngine = EnglishEngine()

    // State parameters
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _activeFusedEmotion = MutableStateFlow(EmotionFusionEngine.FusedEmotion("Neutral", 100))
    val activeFusedEmotion: StateFlow<EmotionFusionEngine.FusedEmotion> = _activeFusedEmotion.asStateFlow()

    private val _sessionMessages = MutableStateFlow<List<SessionMessage>>(emptyList())
    val sessionMessages: StateFlow<List<SessionMessage>> = _sessionMessages.asStateFlow()

    private var sessionStartTime: Long = 0

    // Lifecycle methods
    suspend fun startSession(userId: String, initialPersonality: String) = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        _currentSessionId.value = sessionId
        _isSessionActive.value = true
        sessionStartTime = System.currentTimeMillis()

        // Load active relationship context
        val existingRel = database.relationshipStateDao().getRelationshipState().firstOrNull()
        if (existingRel == null) {
            val defaultRel = com.example.data.database.entity.RelationshipState(
                userId = userId,
                relationshipLevel = 1,
                trustScore = 50.0f,
                engagementScore = 50.0f,
                warmthScore = 50.0f,
                humorScore = 50.0f,
                companionshipScore = 50.0f,
                totalConversations = 0,
                updatedAt = System.currentTimeMillis()
            )
            database.relationshipStateDao().insertRelationshipState(defaultRel)
        }

        // Create standard session entry
        val defaultSession = Session(
            sessionId = sessionId,
            startedAt = sessionStartTime,
            endedAt = 0,
            durationSec = 0,
            dominantEmotion = "Neutral",
            averageConfidence = 80.0f,
            personalityUsed = initialPersonality,
            totalMessages = 0
        )
        database.sessionDao().insertSession(defaultSession)

        // Reset runtime values
        _activeFusedEmotion.value = EmotionFusionEngine.FusedEmotion("Neutral", 100)
        _sessionMessages.value = emptyList()

        Log.d(tag, "Started Conversation Session: $sessionId")
    }

    suspend fun endSession() = withContext(Dispatchers.IO) {
        val sessionId = _currentSessionId.value ?: return@withContext
        val endTime = System.currentTimeMillis()
        val durationSec = ((endTime - sessionStartTime) / 1000).toInt()

        // Save session stats
        val currentSessionVal = database.sessionDao().toString() // lookup details
        val totalMsgs = _sessionMessages.value.size
        
        val finishedSession = Session(
            sessionId = sessionId,
            startedAt = sessionStartTime,
            endedAt = endTime,
            durationSec = durationSec,
            dominantEmotion = _activeFusedEmotion.value.emotion,
            averageConfidence = 85.0f,
            personalityUsed = "Friendly",
            totalMessages = totalMsgs
        )
        database.sessionDao().insertSession(finishedSession)

        // Process Memory Extraction on final transcripts
        coroutineScope.launch {
            val transcript = _sessionMessages.value.joinToString("\n") { "${it.sender}: ${it.content}" }
            memoryExtractor.extractAndSaveMemories(transcript) { extractedMemory ->
                database.memoryFactDao().insertMemoryFact(extractedMemory)
            }
        }

        // Nullify state
        _currentSessionId.value = null
        _isSessionActive.value = false
        Log.d(tag, "Session ended successfully: $sessionId in $durationSec seconds.")
    }

    suspend fun sendMessage(
        userId: String,
        content: String,
        currentPersonality: String
    ): String = withContext(Dispatchers.IO) {
        val sessionId = _currentSessionId.value ?: return@withContext "No active conversation session exists."

        // 1. Save user's message
        val userMsg = SessionMessage(
            messageId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            sender = "user",
            content = content,
            timestamp = System.currentTimeMillis()
        )
        database.sessionMessageDao().insertMessage(userMsg)
        _sessionMessages.value = _sessionMessages.value + userMsg

        // 2. Fetch and score active memories
        val activeMemories = database.memoryFactDao().getAllActiveMemories().firstOrNull() ?: emptyList()
        val topMemories = memoryRanker.rankMemories(activeMemories, sessionId)
        val memoryStrings = topMemories.map { it.fact }

        // 3. Compose system parameters with ContextBuilder
        val contextPrompt = contextBuilder.buildSystemInstruction(
            personality = currentPersonality,
            emotionState = _activeFusedEmotion.value.emotion,
            relationshipLevel = 5,
            goals = listOf("Improve speaking fluency", "Gain general confidence"),
            memories = memoryStrings,
            isEnglishCorrectionEnabled = true
        )

        // 4. Send request to AI Provider
        val companionReply = aiProvider.sendMessage(content, contextPrompt)

        // 5. Save companion's reply
        val compMsg = SessionMessage(
            messageId = UUID.randomUUID().toString(),
            sessionId = sessionId,
            sender = "companion",
            content = companionReply,
            timestamp = System.currentTimeMillis()
        )
        database.sessionMessageDao().insertMessage(compMsg)
        _sessionMessages.value = _sessionMessages.value + compMsg

        return@withContext companionReply
    }

    fun submitEmotionFactors(
        isSmile: Boolean, isFurrowed: Boolean, isEyesClosed: Boolean,
        volumeVariance: Float, pitchHz: Float, hesitations: Int
    ) {
        val faceEst = faceEmotionAnalyzer.analyzeFaceState(isSmile, isFurrowed, isEyesClosed)
        val voiceEst = voiceEmotionAnalyzer.analyzeVoiceMetrics(volumeVariance, pitchHz, hesitations)
        val finalFusion = emotionFusionEngine.fuseEmotions(
            faceEst.emotion, faceEst.confidence,
            voiceEst.emotion, voiceEst.confidence,
            "neutral", 0.5f
        )
        _activeFusedEmotion.value = finalFusion
    }
}
