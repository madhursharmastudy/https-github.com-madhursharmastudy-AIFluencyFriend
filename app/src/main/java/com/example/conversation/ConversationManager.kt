package com.example.conversation

import android.content.Context
import android.util.Log
import com.example.audio.AudioRecordManager
import com.example.audio.AudioTrackPlayer
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
import com.example.providers.GeminiLiveClient
import com.example.providers.GeminiProvider
import com.example.providers.InworldLiveClient
import com.example.relationship.RelationshipManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class ConversationManager(
    private val context: Context,
    private val database: AppDatabase,
    private val aiProvider: AIProvider
) {
    private val tag = "ConversationManager"
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Sub-engine instantiations
    private val contextBuilder = ContextBuilder()
    private val memoryRanker = MemoryRanker()
    private val memoryExtractor = MemoryExtractor(aiProvider)
    private val faceEmotionAnalyzer = FaceEmotionAnalyzer()
    private val voiceEmotionAnalyzer = VoiceEmotionAnalyzer()
    private val emotionFusionEngine = EmotionFusionEngine()
    private val relationshipManager = RelationshipManager()
    private val englishEngine = EnglishEngine()

    // Real-time Audio Hardware Engines
    val audioRecordManager = AudioRecordManager(context)
    val audioTrackPlayer = AudioTrackPlayer(sampleRate = 24000)

    // State parameters
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _activeFusedEmotion = MutableStateFlow(EmotionFusionEngine.FusedEmotion("Neutral", 100))
    val activeFusedEmotion: StateFlow<EmotionFusionEngine.FusedEmotion> = _activeFusedEmotion.asStateFlow()

    private val _sessionMessages = MutableStateFlow<List<SessionMessage>>(emptyList())
    val sessionMessages: StateFlow<List<SessionMessage>> = _sessionMessages.asStateFlow()

    private val _liveAudioLevel = MutableStateFlow(0f)
    val liveAudioLevel: StateFlow<Float> = _liveAudioLevel.asStateFlow()

    private val _liveErrorMessage = MutableStateFlow<String?>(null)
    val liveErrorMessage: StateFlow<String?> = _liveErrorMessage.asStateFlow()

    private var activeGeminiClient: GeminiLiveClient? = null
    private var activeInworldClient: InworldLiveClient? = null
    private var sessionStartTime: Long = 0
    private var currentModelTurnTranscript = StringBuilder()
    private var isLiveStreamingActive = false
    private var responseWatchdogJob: Job? = null

    private fun startResponseWatchdog(onVoiceStateChanged: (String) -> Unit) {
        responseWatchdogJob?.cancel()
        responseWatchdogJob = coroutineScope.launch {
            delay(20_000L) // 20 seconds timeout
            if (isLiveStreamingActive) {
                Log.w(tag, "Gemini Live response timeout reached (20s). Resetting voice state to IDLE.")
                _liveErrorMessage.value = "No response received, please try again."
                stopLiveVoiceSession()
                withContext(Dispatchers.Main) {
                    onVoiceStateChanged("IDLE")
                }
            }
        }
    }

    private fun cancelResponseWatchdog() {
        responseWatchdogJob?.cancel()
        responseWatchdogJob = null
    }

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
        stopLiveVoiceSession()

        val sessionId = _currentSessionId.value ?: return@withContext
        val endTime = System.currentTimeMillis()
        val durationSec = ((endTime - sessionStartTime) / 1000).toInt()

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
            if (transcript.isNotBlank()) {
                memoryExtractor.extractAndSaveMemories(transcript) { extractedMemory ->
                    database.memoryFactDao().insertMemoryFact(extractedMemory)
                }
            }
        }

        // Nullify state
        _currentSessionId.value = null
        _isSessionActive.value = false
        Log.d(tag, "Session ended successfully: $sessionId in $durationSec seconds.")
    }

    /**
     * Starts Real-Time Gemini Multimodal Live Voice session via WebSockets,
     * AudioRecord (16kHz PCM), and AudioTrack (24kHz PCM).
     */
    suspend fun startLiveVoiceSession(
        userId: String,
        personality: String,
        isCameraModeEnabled: Boolean = false,
        onVoiceStateChanged: (String) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        _liveErrorMessage.value = null

        if (!_isSessionActive.value || _currentSessionId.value == null) {
            startSession(userId, personality)
        }

        val sessionId = _currentSessionId.value ?: UUID.randomUUID().toString()

        // 1. Fetch active memories & build system context
        val activeMemories = database.memoryFactDao().getAllActiveMemories().firstOrNull() ?: emptyList()
        val topMemories = memoryRanker.rankMemories(activeMemories, sessionId)
        val memoryStrings = topMemories.map { it.fact }

        val systemPrompt = contextBuilder.buildSystemInstruction(
            personality = personality,
            emotionState = _activeFusedEmotion.value.emotion,
            relationshipLevel = 5,
            goals = listOf("Improve speaking fluency", "Gain conversational confidence"),
            memories = memoryStrings,
            isEnglishCorrectionEnabled = true,
            isCameraModeEnabled = isCameraModeEnabled
        )

        // 2. Initialize AudioTrack player
        audioTrackPlayer.initialize(coroutineScope)
        audioTrackPlayer.onPlaybackStarted = {
            onVoiceStateChanged("SPEAKING")
        }
        audioTrackPlayer.onPlaybackCompleted = {
            if (isLiveStreamingActive) {
                onVoiceStateChanged("LISTENING")
            }
        }

        // 3. Connect Live Client based on active Voice Provider setting
        val settings = database.settingsDao().getSettings()
        val prefs = context.getSharedPreferences("ai_fluency_prefs", Context.MODE_PRIVATE)
        val voiceProvider = settings?.voiceProvider?.ifEmpty { null } 
            ?: prefs.getString("voice_provider", "Gemini") ?: "Gemini"

        onVoiceStateChanged("THINKING")
        isLiveStreamingActive = true
        currentModelTurnTranscript.clear()
        startResponseWatchdog(onVoiceStateChanged)

        if (voiceProvider.equals("Inworld", ignoreCase = true)) {
            // Inworld AI Realtime Connection
            val inworldApiKey = settings?.inworldApiKey?.ifEmpty { null }
                ?: prefs.getString("inworld_api_key", "") ?: ""

            if (inworldApiKey.isBlank()) {
                onError("Inworld API Key is not configured. Please add your key in Settings.")
                return@withContext
            }

            val inworldClient = InworldLiveClient(
                apiKey = inworldApiKey,
                voice = "alloy"
            )

            inworldClient.listener = object : InworldLiveClient.LiveEventListener {
                override fun onConnected() {
                    Log.i(tag, "Inworld Live WebSocket Connected")
                }

                override fun onSetupComplete() {
                    Log.i(tag, "Inworld Live Setup Complete -> starting microphone streaming")
                    cancelResponseWatchdog()
                    onVoiceStateChanged("LISTENING")

                    audioRecordManager.startRecording(
                        scope = coroutineScope,
                        onAudioChunk = { chunkBytes, rmsLevel ->
                            _liveAudioLevel.value = rmsLevel
                            if (isLiveStreamingActive) {
                                inworldClient.sendAudioChunk(chunkBytes)
                                submitEmotionFactors(
                                    isSmile = false,
                                    isFurrowed = false,
                                    isEyesClosed = false,
                                    volumeVariance = rmsLevel * 1000f,
                                    pitchHz = 160f + (rmsLevel * 80f),
                                    hesitations = 0
                                )
                            }
                        },
                        onError = { micError ->
                            Log.e(tag, "Mic error: $micError")
                            cancelResponseWatchdog()
                            onError(micError)
                            _liveErrorMessage.value = micError
                        }
                    )
                }

                override fun onAudioChunkReceived(audioData: ByteArray) {
                    cancelResponseWatchdog()
                    audioTrackPlayer.enqueueAudio(audioData)
                }

                override fun onTranscriptChunkReceived(text: String) {
                    cancelResponseWatchdog()
                    currentModelTurnTranscript.append(text)
                    updateLiveCompanionTranscript(sessionId, currentModelTurnTranscript.toString())
                }

                override fun onTurnComplete() {
                    Log.d(tag, "Inworld Companion turn complete")
                    cancelResponseWatchdog()
                    val fullTranscript = currentModelTurnTranscript.toString().trim()
                    if (fullTranscript.isNotEmpty()) {
                        commitCompletedCompanionMessage(sessionId, fullTranscript)
                    }
                    currentModelTurnTranscript.clear()
                }

                override fun onInterrupted() {
                    Log.d(tag, "Inworld Companion speech interrupted by user voice")
                    cancelResponseWatchdog()
                    audioTrackPlayer.stopAndFlush()
                    currentModelTurnTranscript.clear()
                    onVoiceStateChanged("LISTENING")
                }

                override fun onError(error: String) {
                    Log.e(tag, "Inworld Live Error: $error")
                    cancelResponseWatchdog()
                    _liveErrorMessage.value = error
                    onError(error)
                    stopLiveVoiceSession()
                    onVoiceStateChanged("IDLE")
                }

                override fun onDisconnected(reason: String) {
                    Log.i(tag, "Inworld Live Disconnected: $reason")
                    cancelResponseWatchdog()
                    if (isLiveStreamingActive) {
                        stopLiveVoiceSession()
                        onVoiceStateChanged("IDLE")
                    }
                }
            }

            activeInworldClient = inworldClient
            inworldClient.connect(systemPrompt)
        } else {
            // Google Gemini Multimodal Live Connection
            val geminiProv = aiProvider as? GeminiProvider
            val apiKey = geminiProv?.getActiveApiKey() ?: ""
            if (apiKey.isBlank()) {
                onError("Gemini API Key is not configured. Please add your key in Settings.")
                return@withContext
            }

            val liveClient = GeminiLiveClient(
                apiKey = apiKey,
                model = "gemini-2.5-flash-native-audio-preview-12-2025",
                voiceName = geminiProv?.voiceName ?: "Aoede"
            )

            liveClient.listener = object : GeminiLiveClient.LiveEventListener {
                override fun onConnected() {
                    Log.i(tag, "Gemini Live WebSocket Connected")
                }

                override fun onSetupComplete() {
                    Log.i(tag, "Gemini Live Setup Complete -> starting microphone streaming")
                    cancelResponseWatchdog()
                    onVoiceStateChanged("LISTENING")

                    audioRecordManager.startRecording(
                        scope = coroutineScope,
                        onAudioChunk = { chunkBytes, rmsLevel ->
                            _liveAudioLevel.value = rmsLevel
                            if (isLiveStreamingActive) {
                                liveClient.sendAudioChunk(chunkBytes)
                                submitEmotionFactors(
                                    isSmile = false,
                                    isFurrowed = false,
                                    isEyesClosed = false,
                                    volumeVariance = rmsLevel * 1000f,
                                    pitchHz = 160f + (rmsLevel * 80f),
                                    hesitations = 0
                                )
                            }
                        },
                        onError = { micError ->
                            Log.e(tag, "Mic error: $micError")
                            cancelResponseWatchdog()
                            onError(micError)
                            _liveErrorMessage.value = micError
                        }
                    )
                }

                override fun onAudioChunkReceived(audioData: ByteArray) {
                    cancelResponseWatchdog()
                    audioTrackPlayer.enqueueAudio(audioData)
                }

                override fun onTranscriptChunkReceived(text: String) {
                    cancelResponseWatchdog()
                    currentModelTurnTranscript.append(text)
                    updateLiveCompanionTranscript(sessionId, currentModelTurnTranscript.toString())
                }

                override fun onTurnComplete() {
                    Log.d(tag, "Companion turn complete")
                    cancelResponseWatchdog()
                    val fullTranscript = currentModelTurnTranscript.toString().trim()
                    if (fullTranscript.isNotEmpty()) {
                        commitCompletedCompanionMessage(sessionId, fullTranscript)
                    }
                    currentModelTurnTranscript.clear()
                }

                override fun onInterrupted() {
                    Log.d(tag, "Companion speech interrupted by user voice")
                    cancelResponseWatchdog()
                    audioTrackPlayer.stopAndFlush()
                    currentModelTurnTranscript.clear()
                    onVoiceStateChanged("LISTENING")
                }

                override fun onError(error: String) {
                    Log.e(tag, "Gemini Live Error: $error")
                    cancelResponseWatchdog()
                    _liveErrorMessage.value = error
                    onError(error)
                    stopLiveVoiceSession()
                    onVoiceStateChanged("IDLE")
                }

                override fun onDisconnected(reason: String) {
                    Log.i(tag, "Gemini Live Disconnected: $reason")
                    cancelResponseWatchdog()
                    if (isLiveStreamingActive) {
                        stopLiveVoiceSession()
                        onVoiceStateChanged("IDLE")
                    }
                }
            }

            activeGeminiClient = liveClient
            liveClient.connect(systemPrompt)
        }
    }

    private fun updateLiveCompanionTranscript(sessionId: String, transcript: String) {
        val currentList = _sessionMessages.value.toMutableList()
        val lastIndex = currentList.indexOfLast { it.sender == "companion" && it.messageId.startsWith("live_") }
        val liveMsg = SessionMessage(
            messageId = "live_current",
            sessionId = sessionId,
            sender = "companion",
            content = transcript,
            timestamp = System.currentTimeMillis()
        )
        if (lastIndex >= 0) {
            currentList[lastIndex] = liveMsg
        } else {
            currentList.add(liveMsg)
        }
        _sessionMessages.value = currentList
    }

    private fun commitCompletedCompanionMessage(sessionId: String, fullTranscript: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val finalMsg = SessionMessage(
                messageId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                sender = "companion",
                content = fullTranscript,
                timestamp = System.currentTimeMillis()
            )
            database.sessionMessageDao().insertMessage(finalMsg)

            // Replace ephemeral live message with persisted final message
            val cleaned = _sessionMessages.value.filterNot { it.messageId == "live_current" } + finalMsg
            _sessionMessages.value = cleaned
        }
    }

    fun stopLiveVoiceSession() {
        cancelResponseWatchdog()
        isLiveStreamingActive = false
        audioRecordManager.stopRecording()
        audioTrackPlayer.stopAndFlush()
        audioTrackPlayer.release()
        activeGeminiClient?.disconnect()
        activeGeminiClient = null
        activeInworldClient?.disconnect()
        activeInworldClient = null
        _liveAudioLevel.value = 0f
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
