package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.example.data.database.AppDatabase
import com.example.data.database.entity.*
import com.example.conversation.ConversationManager
import com.example.english.EnglishEngine
import com.example.providers.GeminiProvider
import com.example.providers.InworldLiveClient
import com.example.safety.SafetyGuidanceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "MainViewModel"
    private val prefs = application.getSharedPreferences("ai_fluency_prefs", Context.MODE_PRIVATE)

    // Initializing SQLite Room Database
    val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "ai_fluency_friend_db"
    ).fallbackToDestructiveMigration().build()

    // Providers & Managers
    private val geminiProvider = GeminiProvider()
    val conversationManager = ConversationManager(application, database, geminiProvider)
    private val englishEngine = EnglishEngine()
    private val safetyGuidanceEngine = SafetyGuidanceEngine()

    // Navigation Stack State
    private val _currentScreen = MutableStateFlow("splash")
    val currentScreen: StateFlow<String> = _currentScreen.asStateFlow()

    // Onboarding Form States
    val onboardingName = MutableStateFlow("")
    val onboardingLevel = MutableStateFlow("Intermediate")
    val selectedGoals = MutableStateFlow<Set<String>>(emptySet())
    val onboardingPersonality = MutableStateFlow("Friendly")
    val onboardingApiKey = MutableStateFlow("")
    val onboardingModel = MutableStateFlow("gemini-2.5-flash")

    // API Key Dialog and Testing State
    val showApiKeyDialog = MutableStateFlow(false)
    val apiKeyInput = MutableStateFlow("")
    val modelInput = MutableStateFlow("gemini-2.5-flash")
    val isTestingApiKey = MutableStateFlow(false)
    val apiKeyTestMessage = MutableStateFlow<String?>(null)
    val apiKeyTestSuccess = MutableStateFlow<Boolean?>(null)

    // Inworld Voice Provider & Key State
    val inworldApiKeyInput = MutableStateFlow("")
    val selectedVoiceProvider = MutableStateFlow("Gemini") // "Gemini" or "Inworld"
    val isTestingInworldApiKey = MutableStateFlow(false)
    val inworldApiKeyTestMessage = MutableStateFlow<String?>(null)
    val inworldApiKeyTestSuccess = MutableStateFlow<Boolean?>(null)
    private val _isInworldKeyConfigured = MutableStateFlow(false)
    val isInworldKeyConfigured: StateFlow<Boolean> = _isInworldKeyConfigured.asStateFlow()

    // Current Active Setup
    val userProfile = database.userProfileDao().getUserProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val relationshipState = database.relationshipStateDao().getRelationshipState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeMemories = database.memoryFactDao().getAllActiveMemories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pastSessions = database.sessionDao().getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val englishTrends = database.englishProgressDao().getProgressTrends()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val learnedVocabulary = database.vocabularyGrowthDao().getLearnedVocabulary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userGoals = database.goalDao().getAllGoals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unlockedAchievements = database.achievementDao().getUnlockedAchievements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appSettings = database.settingsDao().getSettingsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Chat UI variables
    val chatInputText = MutableStateFlow("")
    val chatMessages = conversationManager.sessionMessages
    val fusedEmotion = conversationManager.activeFusedEmotion
    val isSessionActive = conversationManager.isSessionActive
    val currentSessionId = conversationManager.currentSessionId

    // Voice Engine State
    // States: IDLE, LISTENING, THINKING, SPEAKING
    private val _voiceState = MutableStateFlow("IDLE")
    val voiceState: StateFlow<String> = _voiceState.asStateFlow()

    val liveAudioLevel: StateFlow<Float> = conversationManager.liveAudioLevel
    val liveErrorMessage: StateFlow<String?> = conversationManager.liveErrorMessage
    val requestAudioPermissionEvent = MutableStateFlow(false)

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()

    // Safety and grammar trigger states
    private val _safetyNotification = MutableStateFlow<String?>(null)
    val safetyNotification: StateFlow<String?> = _safetyNotification.asStateFlow()

    private val _lastInvisibleCorrection = MutableStateFlow<String?>(null)
    val lastInvisibleCorrection: StateFlow<String?> = _lastInvisibleCorrection.asStateFlow()

    private val _isApiKeyConfigured = MutableStateFlow(false)
    val isApiKeyConfigured: StateFlow<Boolean> = _isApiKeyConfigured.asStateFlow()

    init {
        // Load initial API keys and voice provider from SharedPreferences if available
        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        val savedModel = prefs.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        val savedInworldKey = prefs.getString("inworld_api_key", "") ?: ""
        val savedVoiceProvider = prefs.getString("voice_provider", "Gemini") ?: "Gemini"

        if (savedKey.isNotEmpty()) {
            geminiProvider.customApiKey = savedKey
            geminiProvider.customModelName = savedModel
            apiKeyInput.value = savedKey
            modelInput.value = savedModel
            _isApiKeyConfigured.value = true
        } else {
            val buildKey = geminiProvider.getActiveApiKey()
            _isApiKeyConfigured.value = buildKey.isNotEmpty()
        }

        if (savedInworldKey.isNotEmpty()) {
            inworldApiKeyInput.value = savedInworldKey
            _isInworldKeyConfigured.value = true
        }
        selectedVoiceProvider.value = savedVoiceProvider

        // Collect database settings and keep provider in sync
        viewModelScope.launch {
            appSettings.collect { settings ->
                if (settings != null) {
                    if (settings.geminiApiKey.isNotEmpty()) {
                        geminiProvider.customApiKey = settings.geminiApiKey
                        apiKeyInput.value = settings.geminiApiKey
                        prefs.edit().putString("gemini_api_key", settings.geminiApiKey).apply()
                    }
                    if (settings.selectedModel.isNotEmpty()) {
                        geminiProvider.customModelName = settings.selectedModel
                        modelInput.value = settings.selectedModel
                        prefs.edit().putString("gemini_model", settings.selectedModel).apply()
                    }
                    if (settings.inworldApiKey.isNotEmpty()) {
                        inworldApiKeyInput.value = settings.inworldApiKey
                        prefs.edit().putString("inworld_api_key", settings.inworldApiKey).apply()
                        _isInworldKeyConfigured.value = true
                    }
                    if (settings.voiceProvider.isNotEmpty()) {
                        selectedVoiceProvider.value = settings.voiceProvider
                        prefs.edit().putString("voice_provider", settings.voiceProvider).apply()
                    }
                    _isApiKeyConfigured.value = geminiProvider.isKeyConfigured()
                }
            }
        }

        // Run Splash countdown
        viewModelScope.launch {
            delay(2500)
            userProfile.collectLatest { profile ->
                if (profile == null) {
                    _currentScreen.value = "onboarding"
                } else {
                    _currentScreen.value = "home"
                }
            }
        }

        // Setup default mocks if store is blank
        viewModelScope.launch(Dispatchers.IO) {
            setupDefaultDataIfEmpty()
        }
    }

    fun navigateTo(screen: String) {
        _currentScreen.value = screen
    }

    fun openApiKeyDialog() {
        apiKeyTestMessage.value = null
        apiKeyTestSuccess.value = null
        inworldApiKeyTestMessage.value = null
        inworldApiKeyTestSuccess.value = null

        val currentKey = geminiProvider.customApiKey ?: prefs.getString("gemini_api_key", "") ?: ""
        apiKeyInput.value = currentKey
        val currentModel = geminiProvider.customModelName
        modelInput.value = currentModel

        val currentInworldKey = prefs.getString("inworld_api_key", "") ?: appSettings.value?.inworldApiKey ?: ""
        inworldApiKeyInput.value = currentInworldKey

        val currentVoiceProv = prefs.getString("voice_provider", "Gemini") ?: appSettings.value?.voiceProvider ?: "Gemini"
        selectedVoiceProvider.value = currentVoiceProv

        showApiKeyDialog.value = true
    }

    fun closeApiKeyDialog() {
        showApiKeyDialog.value = false
        apiKeyTestMessage.value = null
        apiKeyTestSuccess.value = null
        inworldApiKeyTestMessage.value = null
        inworldApiKeyTestSuccess.value = null
    }

    fun saveAllApiKeySettings(
        geminiKey: String,
        geminiModel: String,
        inworldKey: String,
        voiceProvider: String
    ) {
        val cleanGeminiKey = geminiKey.trim()
        val cleanGeminiModel = geminiModel.trim().ifEmpty { "gemini-2.5-flash" }
        val cleanInworldKey = inworldKey.trim()
        val cleanVoiceProvider = voiceProvider.trim().ifEmpty { "Gemini" }

        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit()
                .putString("gemini_api_key", cleanGeminiKey)
                .putString("gemini_model", cleanGeminiModel)
                .putString("inworld_api_key", cleanInworldKey)
                .putString("voice_provider", cleanVoiceProvider)
                .apply()

            geminiProvider.customApiKey = cleanGeminiKey
            geminiProvider.customModelName = cleanGeminiModel
            _isApiKeyConfigured.value = geminiProvider.isKeyConfigured()
            _isInworldKeyConfigured.value = cleanInworldKey.isNotEmpty()
            selectedVoiceProvider.value = cleanVoiceProvider

            // Save to Room Settings Table
            val currentPh = appSettings.value?.userId ?: "user_default"
            val existing = database.settingsDao().getSettings()
            if (existing != null) {
                val updated = existing.copy(
                    geminiApiKey = cleanGeminiKey,
                    selectedModel = cleanGeminiModel,
                    inworldApiKey = cleanInworldKey,
                    voiceProvider = cleanVoiceProvider
                )
                database.settingsDao().insertSettings(updated)
            } else {
                val newSettings = Settings(
                    userId = currentPh,
                    selectedPersonality = "Friendly",
                    cameraEnabled = true,
                    voiceEmotionEnabled = true,
                    faceEmotionEnabled = true,
                    englishCorrectionEnabled = true,
                    notificationsEnabled = true,
                    geminiApiKey = cleanGeminiKey,
                    selectedModel = cleanGeminiModel,
                    inworldApiKey = cleanInworldKey,
                    voiceProvider = cleanVoiceProvider
                )
                database.settingsDao().insertSettings(newSettings)
            }
            showApiKeyDialog.value = false
        }
    }

    fun saveGeminiApiKey(key: String, model: String) {
        val currentInworld = inworldApiKeyInput.value
        val currentVoiceProv = selectedVoiceProvider.value
        saveAllApiKeySettings(key, model, currentInworld, currentVoiceProv)
    }

    fun updateVoiceProvider(provider: String) {
        val cleanProvider = provider.trim().ifEmpty { "Gemini" }
        selectedVoiceProvider.value = cleanProvider
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString("voice_provider", cleanProvider).apply()
            val existing = database.settingsDao().getSettings()
            if (existing != null) {
                database.settingsDao().insertSettings(existing.copy(voiceProvider = cleanProvider))
            }
        }
    }

    fun testGeminiApiKey(key: String, model: String) {
        val cleanKey = key.trim()
        val cleanModel = model.trim().ifEmpty { "gemini-2.5-flash" }
        if (cleanKey.isEmpty()) {
            apiKeyTestSuccess.value = false
            apiKeyTestMessage.value = "Please enter a Gemini API Key first."
            return
        }

        viewModelScope.launch {
            isTestingApiKey.value = true
            apiKeyTestMessage.value = "Testing connection with Google Gemini..."
            apiKeyTestSuccess.value = null

            val result = geminiProvider.testConnection(cleanKey, cleanModel)
            isTestingApiKey.value = false
            result.onSuccess { msg ->
                apiKeyTestSuccess.value = true
                apiKeyTestMessage.value = msg
            }.onFailure { err ->
                apiKeyTestSuccess.value = false
                apiKeyTestMessage.value = "Verification failed: ${err.message}"
            }
        }
    }

    fun testInworldApiKey(key: String) {
        val cleanKey = key.trim()
        if (cleanKey.isEmpty()) {
            inworldApiKeyTestSuccess.value = false
            inworldApiKeyTestMessage.value = "Please enter an Inworld API Key first."
            return
        }

        viewModelScope.launch {
            isTestingInworldApiKey.value = true
            inworldApiKeyTestMessage.value = "Testing connection with Inworld AI..."
            inworldApiKeyTestSuccess.value = null

            val result = InworldLiveClient.testConnection(cleanKey)
            isTestingInworldApiKey.value = false
            result.onSuccess { msg ->
                inworldApiKeyTestSuccess.value = true
                inworldApiKeyTestMessage.value = msg
            }.onFailure { err ->
                inworldApiKeyTestSuccess.value = false
                inworldApiKeyTestMessage.value = "Verification failed: ${err.message}"
            }
        }
    }

    // Onboarding Actions
    fun completeOnboarding() {
        val name = onboardingName.value.trim().ifEmpty { "Learner" }
        val enteredApiKey = onboardingApiKey.value.trim()
        val enteredModel = onboardingModel.value.trim().ifEmpty { "gemini-2.5-flash" }

        viewModelScope.launch(Dispatchers.IO) {
            val userId = "user_default"

            if (enteredApiKey.isNotEmpty()) {
                prefs.edit()
                    .putString("gemini_api_key", enteredApiKey)
                    .putString("gemini_model", enteredModel)
                    .apply()
                geminiProvider.customApiKey = enteredApiKey
                geminiProvider.customModelName = enteredModel
                _isApiKeyConfigured.value = true
            }

            // Save Profile
            val profile = UserProfile(
                userId = userId,
                name = name,
                age = 24,
                gender = "Other",
                nativeLanguage = "Spanish",
                englishLevel = onboardingLevel.value,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.userProfileDao().insertUserProfile(profile)

            // Save relationship state
            val rel = RelationshipState(
                userId = userId,
                relationshipLevel = 12,
                trustScore = 65.0f,
                engagementScore = 70.0f,
                warmthScore = 80.0f,
                humorScore = 75.0f,
                companionshipScore = 72.0f,
                totalConversations = 3,
                updatedAt = System.currentTimeMillis()
            )
            database.relationshipStateDao().insertRelationshipState(rel)

            // Save default Settings
            val settings = Settings(
                userId = userId,
                selectedPersonality = onboardingPersonality.value,
                cameraEnabled = true,
                voiceEmotionEnabled = true,
                faceEmotionEnabled = true,
                englishCorrectionEnabled = true,
                notificationsEnabled = true,
                geminiApiKey = enteredApiKey,
                selectedModel = enteredModel
            )
            database.settingsDao().insertSettings(settings)

            // Auto-populate goals
            selectedGoals.value.forEach { goalName ->
                val goal = Goal(
                    goalId = UUID.randomUUID().toString(),
                    title = goalName,
                    description = "Improve ability in $goalName through companion chats.",
                    category = "ENGLISH",
                    progress = 20.0f,
                    completed = false,
                    createdAt = System.currentTimeMillis()
                )
                database.goalDao().insertGoal(goal)
            }

            // Move to Home screen
            _currentScreen.value = "home"
        }
    }

    // Conversation Actions
    fun handleStartSession() {
        if (!geminiProvider.isKeyConfigured()) {
            openApiKeyDialog()
        }
        viewModelScope.launch {
            val personality = appSettings.value?.selectedPersonality ?: "Friendly"
            conversationManager.startSession("user_default", personality)
            _voiceState.value = "IDLE"
        }
    }

    fun handleEndSession() {
        _voiceState.value = "IDLE"
        conversationManager.stopLiveVoiceSession()
        viewModelScope.launch {
            conversationManager.endSession()
        }
    }

    fun toggleVoiceState() {
        if (!geminiProvider.isKeyConfigured()) {
            openApiKeyDialog()
            return
        }

        if (!conversationManager.audioRecordManager.hasPermission()) {
            requestAudioPermissionEvent.value = true
            return
        }

        viewModelScope.launch {
            if (_voiceState.value == "IDLE") {
                startRealVoiceSession()
            } else {
                stopRealVoiceSession()
            }
        }
    }

    fun startRealVoiceSession() {
        if (!geminiProvider.isKeyConfigured()) {
            openApiKeyDialog()
            return
        }
        if (!conversationManager.audioRecordManager.hasPermission()) {
            requestAudioPermissionEvent.value = true
            return
        }

        viewModelScope.launch {
            _lastInvisibleCorrection.value = null
            _safetyNotification.value = null
            val personality = appSettings.value?.selectedPersonality ?: "Friendly"
            conversationManager.startLiveVoiceSession(
                userId = "user_default",
                personality = personality,
                onVoiceStateChanged = { newState ->
                    _voiceState.value = newState
                },
                onError = { error ->
                    _voiceState.value = "IDLE"
                }
            )
        }
    }

    fun stopRealVoiceSession() {
        _voiceState.value = "IDLE"
        conversationManager.stopLiveVoiceSession()
    }

    fun onAudioPermissionResult(granted: Boolean) {
        requestAudioPermissionEvent.value = false
        if (granted) {
            startRealVoiceSession()
        }
    }

    fun sendMessageDirectly(text: String) {
        if (text.trim().isEmpty()) return
        if (!geminiProvider.isKeyConfigured()) {
            openApiKeyDialog()
            return
        }
        chatInputText.value = ""
        viewModelScope.launch {
            _voiceState.value = "THINKING"
            
            // Check Safety triggers first
            val safetyResult = safetyGuidanceEngine.checkSafetyTriggers(text)
            if (safetyResult != null) {
                _safetyNotification.value = "[Safety System Warning on topic ${safetyResult.topic}]\n${safetyResult.guidance}"
            }

            // Check English corrections context
            val evaluation = englishEngine.evaluateGrammarAndSuggestCorrection(text)
            if (evaluation.hasCorrection) {
                _lastInvisibleCorrection.value = "Invisible Correction Applied: modeled grammar correction \"${evaluation.correctedPhrase}\""
            }

            // Suggest Vocabulary Expansion naturally
            val vocabSuggestion = englishEngine.suggestVocabularyExpansion(text)
            if (vocabSuggestion.isNotEmpty()) {
                val vocabNode = VocabularyGrowth(
                    wordId = UUID.randomUUID().toString(),
                    word = vocabSuggestion,
                    meaning = "More advanced synonym for user expression.",
                    learnedAt = System.currentTimeMillis(),
                    usageCount = 1,
                    masteryLevel = 25
                )
                database.vocabularyGrowthDao().insertVocabulary(vocabNode)
            }

            // Save text message to session log
            val personality = appSettings.value?.selectedPersonality ?: "Friendly"
            val reply = conversationManager.sendMessage("user_default", text, personality)
            
            _voiceState.value = "SPEAKING"
            // Feed simulated facial expression data to adjust active fusion
            conversationManager.submitEmotionFactors(
                isSmile = true,
                isFurrowed = false,
                isEyesClosed = false,
                volumeVariance = 312.0f,
                pitchHz = 195.0f,
                hesitations = 1
            )
            delay(3000)
            if (_voiceState.value == "SPEAKING") {
                _voiceState.value = "IDLE"
            }
        }
    }

    // Active Quick Commands
    fun applyQuickAction(actionType: String) {
        when (actionType) {
            "correct" -> {
                sendMessageDirectly("Can you correct my grammar in my last message?")
            }
            "grammar" -> {
                sendMessageDirectly("Explain when to use past tense versus past perfect.")
            }
            "interview" -> {
                sendMessageDirectly("Let's practice an English job interview. Ask me the first question.")
            }
            "topic" -> {
                sendMessageDirectly("Let's change the topic. Tell me an interesting space story.")
            }
            "question" -> {
                sendMessageDirectly("Ask me a deep friendly question about my passions.")
            }
        }
    }

    // Memory Actions
    fun pinMemory(memoryId: String, currentStatus: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val memory = database.memoryFactDao().getMemoryById(memoryId)
            if (memory != null) {
                val updated = memory.copy(
                    importance = if (currentStatus) 1 else 5,
                    referenceCount = memory.referenceCount + 1,
                    lastReferencedAt = System.currentTimeMillis()
                )
                database.memoryFactDao().insertMemoryFact(updated)
            }
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.memoryFactDao().archiveMemoryFact(memoryId)
        }
    }

    fun toggleCamera() {
        _isCameraOn.value = !_isCameraOn.value
    }

    fun resetCompanionHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentPh = appSettings.value?.userId ?: "user_default"
            val resetRel = RelationshipState(
                userId = currentPh,
                relationshipLevel = 1,
                trustScore = 50.0f,
                engagementScore = 50.0f,
                warmthScore = 50.0f,
                humorScore = 50.0f,
                companionshipScore = 50.0f,
                totalConversations = 0,
                updatedAt = System.currentTimeMillis()
            )
            database.relationshipStateDao().insertRelationshipState(resetRel)
            Log.d(tag, "Companion relationship reset done.")
        }
    }

    fun updateSelectedPersonality(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentSettings = appSettings.value
            if (currentSettings != null) {
                val updated = currentSettings.copy(selectedPersonality = name)
                database.settingsDao().insertSettings(updated)
            }
        }
    }

    private suspend fun setupDefaultDataIfEmpty() {
        val existingProfile = database.userProfileDao().getUserProfile().firstOrNull()
        if (existingProfile == null) {
            return
        }

        val existingActiveMemories = database.memoryFactDao().getAllActiveMemories().first()
        if (existingActiveMemories.isEmpty()) {
            val memories = listOf(
                MemoryFact(
                    memoryId = "mem_1",
                    fact = "User enjoys morning runs and is training for a local 5k race.",
                    category = "HOBBY",
                    emotion = "happy",
                    importance = 4,
                    confidence = 0.95f,
                    createdAt = System.currentTimeMillis() - 86400000 * 3,
                    lastReferencedAt = System.currentTimeMillis() - 86400000 * 1,
                    referenceCount = 4,
                    active = true
                ),
                MemoryFact(
                    memoryId = "mem_2",
                    fact = "User's mother was a teacher, inspiring their focus on education.",
                    category = "FAMILY",
                    emotion = "love",
                    importance = 5,
                    confidence = 0.92f,
                    createdAt = System.currentTimeMillis() - 86400000 * 5,
                    lastReferencedAt = System.currentTimeMillis() - 86400000 * 2,
                    referenceCount = 2,
                    active = true
                ),
                MemoryFact(
                    memoryId = "mem_3",
                    fact = "User gets a bit anxious when presenting slides to large teams at work.",
                    category = "FEAR",
                    emotion = "anxious",
                    importance = 3,
                    confidence = 0.88f,
                    createdAt = System.currentTimeMillis() - 86400000 * 4,
                    lastReferencedAt = System.currentTimeMillis() - 86400000 * 3,
                    referenceCount = 3,
                    active = true
                )
            )
            memories.forEach { database.memoryFactDao().insertMemoryFact(it) }
        }

        val progress = database.englishProgressDao().getProgressTrends().first()
        if (progress.isEmpty()) {
            val daysHistory = listOf(
                EnglishProgress("p_1", System.currentTimeMillis() - 86400000 * 4, 180, 80.0f, 65.0f, 70.0f, 74.0f, 68.0f),
                EnglishProgress("p_2", System.currentTimeMillis() - 86400000 * 3, 340, 84.0f, 70.0f, 75.0f, 76.0f, 73.0f),
                EnglishProgress("p_3", System.currentTimeMillis() - 86400000 * 2, 450, 88.0f, 72.0f, 78.0f, 80.0f, 79.0f),
                EnglishProgress("p_4", System.currentTimeMillis() - 86400000 * 1, 620, 92.0f, 78.0f, 82.0f, 85.0f, 88.0f)
            )
            daysHistory.forEach { database.englishProgressDao().insertProgress(it) }
        }

        val achievements = database.achievementDao().getUnlockedAchievements().first()
        if (achievements.isEmpty()) {
            val unlocked = listOf(
                Achievement("FIRST_CONVERSATION", "First Spoken Words", "Unlocked during your initial dialogue.", System.currentTimeMillis() - 86400000 * 4),
                Achievement("FIRST_10_MIN_SESSION", "Fluent Flow", "Successfully stayed active in conversational chat for over 10 minutes.", System.currentTimeMillis() - 86400000 * 2),
                Achievement("CONFIDENCE_IMPROVER", "Bold Speaker", "Formulated 10 consecutive elaborate long-form complete English answers.", System.currentTimeMillis() - 86400000 * 1)
            )
            unlocked.forEach { database.achievementDao().insertAchievement(it) }
        }

        val sessionsCount = database.sessionDao().getAllSessions().first()
        if (sessionsCount.isEmpty()) {
            val past = listOf(
                Session(
                    sessionId = "past_s1",
                    startedAt = System.currentTimeMillis() - 86400000 * 3,
                    endedAt = System.currentTimeMillis() - 86400000 * 3 + 120000,
                    durationSec = 120,
                    dominantEmotion = "Excited",
                    averageConfidence = 78.0f,
                    personalityUsed = "Witty",
                    totalMessages = 6
                ),
                Session(
                    sessionId = "past_s2",
                    startedAt = System.currentTimeMillis() - 86400000 * 1,
                    endedAt = System.currentTimeMillis() - 86400000 * 1 + 340000,
                    durationSec = 340,
                    dominantEmotion = "Confident",
                    averageConfidence = 85.0f,
                    personalityUsed = "Friendly",
                    totalMessages = 15
                )
            )
            past.forEach { database.sessionDao().insertSession(it) }
        }

        val words = database.vocabularyGrowthDao().getLearnedVocabulary().first()
        if (words.isEmpty()) {
            val vocabList = listOf(
                VocabularyGrowth(UUID.randomUUID().toString(), "Delighted", "Genuinely or extremely pleased.", System.currentTimeMillis() - 86400000 * 3, 2, 75),
                VocabularyGrowth(UUID.randomUUID().toString(), "Vibrant", "Full of energy, enthusiasm, and activity.", System.currentTimeMillis() - 86400000 * 2, 1, 50),
                VocabularyGrowth(UUID.randomUUID().toString(), "Articulate", "Having or showing the ability to speak fluently and coherently.", System.currentTimeMillis() - 86400000 * 1, 3, 100)
            )
            vocabList.forEach { database.vocabularyGrowthDao().insertVocabulary(it) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        conversationManager.stopLiveVoiceSession()
    }
}
