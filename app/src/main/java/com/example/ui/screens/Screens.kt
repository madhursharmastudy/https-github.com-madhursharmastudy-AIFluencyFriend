package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.entity.Goal
import com.example.data.database.entity.MemoryFact
import com.example.data.database.entity.Session
import com.example.debug.LiveDebugLogger
import com.example.ui.viewmodel.MainViewModel

// ==========================================
// 1. SPLASH SCREEN
// ==========================================
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(
                colors = listOf(Color(0xFFEADDFF), Color(0xFFFEF7FF)),
                radius = 1200f
            )),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // Elegant pulsing glow representation
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .background(Color(0xFF6750A4), shape = CircleShape)
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubble,
                    contentDescription = "App Logo",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "AI Fluency Friend",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1D1B20),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your English Companion",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF49454F),
                letterSpacing = 1.sp
            )
        }
    }
}

// ==========================================
// 1.5 API KEY DIALOG & BANNER
// ==========================================
@Composable
fun ApiKeyStatusBanner(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isConfigured by viewModel.isApiKeyConfigured.collectAsState()
    if (!isConfigured) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .clickable { viewModel.openApiKeyDialog() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFFFFB74D))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFFF9800), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gemini API Key Required",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = "Tap here to enter your API key to activate voice conversations.",
                        fontSize = 12.sp,
                        color = Color(0xFF5D4037)
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = "Configure",
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ApiKeySetupDialog(viewModel: MainViewModel) {
    val inputKey by viewModel.apiKeyInput.collectAsState()
    val inputModel by viewModel.modelInput.collectAsState()
    val isTestingGemini by viewModel.isTestingApiKey.collectAsState()
    val geminiTestMessage by viewModel.apiKeyTestMessage.collectAsState()
    val geminiTestSuccess by viewModel.apiKeyTestSuccess.collectAsState()

    val inworldKey by viewModel.inworldApiKeyInput.collectAsState()
    val selectedVoiceProvider by viewModel.selectedVoiceProvider.collectAsState()
    val isTestingInworld by viewModel.isTestingInworldApiKey.collectAsState()
    val inworldTestMessage by viewModel.inworldApiKeyTestMessage.collectAsState()
    val inworldTestSuccess by viewModel.inworldApiKeyTestSuccess.collectAsState()

    var geminiPasswordVisible by remember { mutableStateOf(false) }
    var inworldPasswordVisible by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = { viewModel.closeApiKeyDialog() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "AI & Voice Configuration",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF1D1B20)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. VOICE PROVIDER SELECTOR
                Text(
                    text = "VOICE PROVIDER (LIVE AUDIO)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose the engine that powers real-time bidirectional voice conversation. Text chat always uses Gemini API.",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Gemini", "Inworld").forEach { provider ->
                        val isSelected = selectedVoiceProvider.equals(provider, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectedVoiceProvider.value = provider },
                            label = {
                                Text(
                                    text = if (provider == "Gemini") "Google Gemini Live" else "Inworld AI Realtime",
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEADDFF),
                                selectedLabelColor = Color(0xFF21005D)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // 2. GEMINI API KEY SECTION
                Text(
                    text = "GEMINI API KEY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Powers text conversations, companion intelligence, and Gemini Live voice.",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inputKey,
                    onValueChange = { viewModel.apiKeyInput.value = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    singleLine = true,
                    visualTransformation = if (geminiPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inputKey.isNotEmpty()) {
                                IconButton(onClick = { geminiPasswordVisible = !geminiPasswordVisible }) {
                                    Icon(
                                        imageVector = if (geminiPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (geminiPasswordVisible) "Hide key" else "Show key",
                                        tint = Color(0xFF6750A4)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                val clipText = clipboardManager.getText()?.text ?: ""
                                if (clipText.isNotEmpty()) {
                                    viewModel.apiKeyInput.value = clipText.trim()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste from clipboard",
                                    tint = Color(0xFF6750A4)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "GEMINI TEXT MODEL",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(4.dp))

                val availableModels = listOf(
                    "gemini-2.5-flash" to "Fast & Recommended",
                    "gemini-3.5-flash" to "Advanced Flash",
                    "gemini-3.1-pro-preview" to "Pro Reasoning"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableModels.forEach { (mCode, _) ->
                        val isSelected = inputModel == mCode
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.modelInput.value = mCode },
                            label = { Text(mCode, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEADDFF),
                                selectedLabelColor = Color(0xFF21005D)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Gemini Test Connection Button
                OutlinedButton(
                    onClick = { viewModel.testGeminiApiKey(inputKey, inputModel) },
                    enabled = !isTestingGemini && inputKey.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestingGemini) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF6750A4)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Gemini...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Gemini Connection", fontSize = 13.sp)
                    }
                }

                if (geminiTestMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (geminiTestSuccess == true) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = geminiTestMessage ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (geminiTestSuccess == true) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // 3. INWORLD AI KEY SECTION
                Text(
                    text = "INWORLD API KEY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Powers Inworld AI ultra low-latency speech-to-speech companion conversation.",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = inworldKey,
                    onValueChange = { viewModel.inworldApiKeyInput.value = it },
                    label = { Text("Inworld API Key") },
                    placeholder = { Text("Base64 API Key or key:secret") },
                    singleLine = true,
                    visualTransformation = if (inworldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (inworldKey.isNotEmpty()) {
                                IconButton(onClick = { inworldPasswordVisible = !inworldPasswordVisible }) {
                                    Icon(
                                        imageVector = if (inworldPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (inworldPasswordVisible) "Hide key" else "Show key",
                                        tint = Color(0xFF6750A4)
                                    )
                                }
                            }
                            IconButton(onClick = {
                                val clipText = clipboardManager.getText()?.text ?: ""
                                if (clipText.isNotEmpty()) {
                                    viewModel.inworldApiKeyInput.value = clipText.trim()
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = "Paste from clipboard",
                                    tint = Color(0xFF6750A4)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Inworld Test Connection Button
                OutlinedButton(
                    onClick = { viewModel.testInworldApiKey(inworldKey) },
                    enabled = !isTestingInworld && inworldKey.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTestingInworld) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF6750A4)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Testing Inworld...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Inworld Connection", fontSize = 13.sp)
                    }
                }

                if (inworldTestMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (inworldTestSuccess == true) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp)
                    ) {
                        Text(
                            text = inworldTestMessage ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (inworldTestSuccess == true) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Get your Inworld key from studio.inworld.ai",
                    fontSize = 11.sp,
                    color = Color(0xFF79747E)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveAllApiKeySettings(
                        inputKey,
                        inputModel,
                        inworldKey,
                        selectedVoiceProvider
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Settings", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.closeApiKeyDialog() }) {
                Text("Cancel", color = Color(0xFF6750A4))
            }
        }
    )
}

// ==========================================
// 2. ONBOARDING SCREEN
// ==========================================
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    var step by remember { mutableStateOf(1) }
    val name by viewModel.onboardingName.collectAsState()
    val level by viewModel.onboardingLevel.collectAsState()
    val selectedGoals by viewModel.selectedGoals.collectAsState()
    val personality by viewModel.onboardingPersonality.collectAsState()
    val onboardingKey by viewModel.onboardingApiKey.collectAsState()
    val onboardingModel by viewModel.onboardingModel.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val isTesting by viewModel.isTestingApiKey.collectAsState()
    val testMessage by viewModel.apiKeyTestMessage.collectAsState()
    val testSuccess by viewModel.apiKeyTestSuccess.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .navigationBarsPadding()
                .statusBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Setup Header Progress
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Welcome",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4)
                )
                Text(
                    text = "Step $step of 6",
                    fontSize = 12.sp,
                    color = Color(0xFF49454F)
                )
            }

            // Central setup panel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (step) {
                    1 -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "What is your name?",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "I'll use this to customize our companion chat sessions.",
                                fontSize = 14.sp,
                                color = Color(0xFF49454F),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = { viewModel.onboardingName.value = it },
                                label = { Text("Enter your name") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF6750A4),
                                    unfocusedBorderColor = Color(0xFFCAC4D0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    2 -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "What is your current English level?",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            val levels = listOf("Beginner", "Intermediate", "Advanced")
                            levels.forEach { item ->
                                val isSelected = level == item
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable { viewModel.onboardingLevel.value = item },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFFEADDFF) else Color.White
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isSelected) Color(0xFF6750A4) else Color(0xFFCAC4D0)
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(18.dp)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = item,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) Color(0xFF21005D) else Color(0xFF1D1B20)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "Choose your conversation goals",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Multi-select list",
                                fontSize = 12.sp,
                                color = Color(0xFF49454F),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            val goals = listOf(
                                "Improve Speaking",
                                "Improve Confidence",
                                "Interview Practice",
                                "Daily Conversation",
                                "Vocabulary Building",
                                "Grammar Improvement"
                            )
                            Column {
                                goals.forEach { item ->
                                    val isSelected = selectedGoals.contains(item)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(
                                                color = if (isSelected) Color(0xFFF3EDF7) else Color.Transparent,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                val newSet = selectedGoals.toMutableSet()
                                                if (isSelected) newSet.remove(item) else newSet.add(item)
                                                viewModel.selectedGoals.value = newSet
                                             }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null,
                                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6750A4))
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = item, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                    4 -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Select Companion Personality",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            val personalities = listOf(
                                "Friendly" to "Balanced and Supportive config",
                                "Witty" to "Smart humor and playful banter",
                                "Talkative" to "Energetic dialogue starter",
                                "Sarcastic" to "Loveable friendly roasts",
                                "Lovable" to "Warm, affectionate, caring",
                                "Flirty" to "Sweet charm and happy teasings",
                                "Naughty" to "Mischievous, silly and fun"
                            )
                            Box(modifier = Modifier.height(350.dp).verticalScroll(rememberScrollState())) {
                                Column {
                                    personalities.forEach { (pName, pDesc) ->
                                        val isSelected = personality == pName
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                                .clickable { viewModel.onboardingPersonality.value = pName },
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) Color(0xFFEADDFF) else Color.White
                                            ),
                                            border = BorderStroke(
                                                width = 1.dp,
                                                color = if (isSelected) Color(0xFF6750A4) else Color(0xFFCAC4D0)
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                RadioButton(
                                                    selected = isSelected,
                                                    onClick = null,
                                                    colors = RadioButtonDefaults.colors(
                                                        selectedColor = Color(
                                                            0xFF6750A4
                                                        )
                                                    )
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(
                                                        pName,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF1D1B20)
                                                    )
                                                    Text(pDesc, fontSize = 12.sp, color = Color(0xFF49454F))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    5 -> {
                        // GEMINI API KEY STEP
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Color(0xFFEADDFF), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VpnKey,
                                    contentDescription = "Gemini Key",
                                    tint = Color(0xFF6750A4),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Enter Your Gemini API Key",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The application saves your Gemini API key permanently to your device so you can talk to Aria at any time.",
                                fontSize = 13.sp,
                                color = Color(0xFF49454F),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))

                            OutlinedTextField(
                                value = onboardingKey,
                                onValueChange = { viewModel.onboardingApiKey.value = it },
                                label = { Text("Gemini API Key") },
                                placeholder = { Text("AIzaSy...") },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (onboardingKey.isNotEmpty()) {
                                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                                Icon(
                                                    imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = if (passwordVisible) "Hide key" else "Show key",
                                                    tint = Color(0xFF6750A4)
                                                )
                                            }
                                        }
                                        IconButton(onClick = {
                                            val clipText = clipboardManager.getText()?.text ?: ""
                                            if (clipText.isNotEmpty()) {
                                                viewModel.onboardingApiKey.value = clipText.trim()
                                            }
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.ContentPaste,
                                                contentDescription = "Paste",
                                                tint = Color(0xFF6750A4)
                                            )
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF6750A4),
                                    unfocusedBorderColor = Color(0xFFCAC4D0)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedButton(
                                onClick = { viewModel.testGeminiApiKey(onboardingKey, onboardingModel) },
                                enabled = !isTesting && onboardingKey.isNotBlank(),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isTesting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF6750A4)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Verifying Key...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.CloudDone,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Test Connection")
                                }
                            }

                            if (testMessage != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = if (testSuccess == true) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = testMessage ?: "",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (testSuccess == true) Color(0xFF2E7D32) else Color(0xFFC62828)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "You can get a free Gemini API key at aistudio.google.com",
                                fontSize = 11.sp,
                                color = Color(0xFF79747E)
                            )
                        }
                    }
                    6 -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Security logo",
                                tint = Color(0xFF6750A4),
                                modifier = Modifier.size(68.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Request System Permissions",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D1B20),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "We need Microphone and Camera access to enable the live voice companion and analyze facial expressions. Your data is handled entirely locally on-device.",
                                fontSize = 14.sp,
                                color = Color(0xFF49454F),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { viewModel.completeOnboarding() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("Grant Permission & Launch", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (step > 1) {
                    TextButton(onClick = { step-- }) {
                        Text("Back", color = Color(0xFF6750A4))
                     }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }

                if (step < 6) {
                    Button(
                        onClick = { step++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. HOME SCREEN
// ==========================================
@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val relState by viewModel.relationshipState.collectAsState()
    val memories by viewModel.activeMemories.collectAsState()
    val settings by viewModel.appSettings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(34.dp))
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AI FLUENCY FRIEND",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Good Evening, ${profile?.name ?: "Learner"}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20)
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFFEADDFF), shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User Avatar",
                    tint = Color(0xFF21005D)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        ApiKeyStatusBanner(viewModel = viewModel)
        Spacer(modifier = Modifier.height(14.dp))

        // Professional Polish Companion Core Widget Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(28.dp)),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${settings?.selectedPersonality ?: "Friendly"} Companion",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B20)
                        )
                        Text(
                            text = "Mood: Curious",
                            fontSize = 14.sp,
                            color = Color(0xFF49454F)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFF3EDF7), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Familiarity level: ${relState?.relationshipLevel ?: 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Mini stats line progress bars
                val barProgress = ((relState?.companionshipScore ?: 50.0f) / 100f)
                LinearProgressIndicator(
                    progress = barProgress,
                    color = Color(0xFF6750A4),
                    trackColor = Color(0xFFE8DEF8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                )

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Relationship Bond", fontSize = 11.sp, color = Color(0xFF49454F))
                    Text("${(barProgress * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF3EDF7), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text("SPEAKING TIME", fontSize = 10.sp, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("34 Minutes", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF3EDF7), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text("ENGLISH GROWTH", fontSize = 10.sp, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("92% Fluency", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(Color(0xFFF3EDF7), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text("STREAK", fontSize = 10.sp, color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("7 Days", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Large CTA Launcher banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    viewModel.handleStartSession()
                    viewModel.navigateTo("chat")
                },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF6750A4)),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Launch Vocal Companionship",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Begin real-time English conversation with smart emotional analytics & customized memory feedback.",
                    fontSize = 13.sp,
                    color = Color(0xFFD0BCFF)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Start Conversation", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Recent memories subtitle
        Text(
            text = "Companion Shared Memory Records",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B20),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (memories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Let's create some memories together. Start chatting!",
                    color = Color(0xFF49454F),
                    fontSize = 13.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                memories.take(3).forEach { memory ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFFF3EDF7), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = Color(0xFF6750A4),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = memory.fact,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF1D1B20)
                            )
                            Text(
                                text = "Category: ${memory.category} • Emotional Vibe: ${memory.emotion}",
                                fontSize = 11.sp,
                                color = Color(0xFF49454F)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ==========================================
// 4. CHAT / CONVERSATION SCREEN
// ==========================================
@Composable
fun ChatScreen(viewModel: MainViewModel) {
    val messages by viewModel.chatMessages.collectAsState()
    val testSessionId by viewModel.currentSessionId.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val activeEmotion by viewModel.fusedEmotion.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val liveAudioLevel by viewModel.liveAudioLevel.collectAsState()
    val liveError by viewModel.liveErrorMessage.collectAsState()
    val liveCaptions by viewModel.liveCaptions.collectAsState()
    val isCaptionsOverlayVisible by viewModel.isCaptionsOverlayVisible.collectAsState()
    val isAecNsEnabled by viewModel.isAecNsEnabled.collectAsState()

    val safetyNote by viewModel.safetyNotification.collectAsState()
    val correctionNote by viewModel.lastInvisibleCorrection.collectAsState()
    val isCameraOn by viewModel.isCameraOn.collectAsState()

    // Debug logs state
    val debugLogs by LiveDebugLogger.logs.collectAsState()
    val wsStatus by LiveDebugLogger.wsStatus.collectAsState()
    var isDebugPanelOpen by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val debugListState = rememberLazyListState()
    val captionsListState = rememberLazyListState()

    // Auto-scroll debug logs to the bottom when new logs arrive
    LaunchedEffect(debugLogs.size) {
        if (debugLogs.isNotEmpty()) {
            debugListState.animateScrollToItem(debugLogs.size - 1)
        }
    }

    // Auto-scroll live captions to the latest entry/delta
    LaunchedEffect(liveCaptions.size, liveCaptions.lastOrNull()?.text) {
        if (liveCaptions.isNotEmpty()) {
            captionsListState.animateScrollToItem(liveCaptions.size - 1)
        }
    }

    // Manage mute state
    var isMuted by remember { mutableStateOf(false) }
    var isPersonalitySelectorOpen by remember { mutableStateOf(false) }

    // Start real voice session on entering ChatScreen if idle
    LaunchedEffect(Unit) {
        if (voiceState == "IDLE") {
            viewModel.startRealVoiceSession()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C20), // Midnight Indigo
                        Color(0xFF15102A)  // Rich Purple
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top Section: Empathy Pill, Captions Toggle, Debug Toggle & Warnings
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isMuted) Color.Gray else Color(0xFF34C759),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sync: ${activeEmotion.emotion} (${activeEmotion.confidence}%)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Quick Mode / Personality Switcher Button
                        Button(
                            onClick = { isPersonalitySelectorOpen = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6750A4),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("voice_mode_switcher_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = "Switch Personality Mode",
                                modifier = Modifier.size(14.dp),
                                tint = Color(0xFFEADDFF)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = settings?.selectedPersonality ?: "Friendly",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Dedicated Live Captions Overlay Toggle (Default: ON)
                        Button(
                            onClick = { viewModel.toggleCaptionsOverlay() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCaptionsOverlayVisible) Color(0xFF6750A4) else Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isCaptionsOverlayVisible) Icons.Default.Subtitles else Icons.Default.SubtitlesOff,
                                contentDescription = "Toggle Captions Overlay",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isCaptionsOverlayVisible) "Captions ON" else "Captions OFF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Debug Log Toggle Button
                        Button(
                            onClick = { isDebugPanelOpen = !isDebugPanelOpen },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isDebugPanelOpen) Color(0xFF6750A4) else Color.White.copy(alpha = 0.15f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = "Toggle Debug Log",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isDebugPanelOpen) "Logs" else "Debug",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Dedicated Camera Mode Button / Toggle (Off by default for voice sessions)
                Card(
                    onClick = { viewModel.toggleCamera() },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCameraOn) Color(0xFF1B382B).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isCameraOn) Color(0xFF4CAF50).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.18f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(
                                        if (isCameraOn) Color(0xFF4CAF50).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.1f),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                    contentDescription = if (isCameraOn) "Camera Mode Active" else "Camera Mode Off",
                                    tint = if (isCameraOn) Color(0xFF81C784) else Color.White.copy(alpha = 0.75f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isCameraOn) "Camera Mode: ACTIVE" else "Camera Mode: OFF",
                                    color = if (isCameraOn) Color(0xFF81C784) else Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isCameraOn) "Analyzing facial emotion cues" else "Voice-only (No video/visual feed sent)",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .background(
                                    if (isCameraOn) Color(0xFFB00020).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.15f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isCameraOn) "Turn Off" else "Enable",
                                color = if (isCameraOn) Color(0xFFFFCDD2) else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Error and status notifications
                if (liveError != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFBA1A1A).copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = liveError ?: "",
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else if (safetyNote != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF881111).copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = safetyNote!!,
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                } else if (correctionNote != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF116611).copy(alpha = 0.85f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = correctionNote!!,
                            color = Color.White,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 2. Center Section: Dynamic Pulsing Orb & Transparent Real-Time Captions Overlay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                Text(
                    text = when {
                        isMuted -> "Microphone Muted"
                        voiceState == "LISTENING" -> "Listening..."
                        voiceState == "THINKING" -> "Thinking..."
                        voiceState == "SPEAKING" -> "Speaking..."
                        else -> "Ready to Talk"
                    },
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                val currentProviderLabel = if (settings?.voiceProvider.equals("Inworld", true)) "Inworld AI (Sarah)" else "Gemini Live"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { isPersonalitySelectorOpen = true }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("personality_subtitle_button")
                ) {
                    Text(
                        text = "Aria • ${settings?.selectedPersonality ?: "Friendly"} • $currentProviderLabel",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch Personality",
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Dynamic Audio Scale & Pulsing Orbs
                val infiniteTransition = rememberInfiniteTransition(label = "pulse_trans")
                val pulse1 by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.25f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "p1"
                )
                val pulse2 by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.45f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1800, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "p2"
                )
                val pulse3 by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.7f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2400, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "p3"
                )

                val audioBoost = (liveAudioLevel * 1.5f).coerceIn(0f, 0.8f)

                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Outer aura 3
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale((if (isMuted) 1.0f else if (voiceState == "LISTENING") (pulse3 + audioBoost) else if (voiceState == "SPEAKING") pulse2 else pulse1))
                            .background(
                                color = when {
                                    isMuted -> Color(0xFF424242).copy(alpha = 0.1f)
                                    voiceState == "LISTENING" -> Color(0xFF6750A4).copy(alpha = 0.15f + audioBoost * 0.2f)
                                    voiceState == "THINKING" -> Color(0xFFE8DEF8).copy(alpha = 0.15f)
                                    voiceState == "SPEAKING" -> Color(0xFF00D2FF).copy(alpha = 0.15f)
                                    else -> Color(0xFFE8DEF8).copy(alpha = 0.08f)
                                },
                                shape = CircleShape
                            )
                    )
                    // Mid aura 2
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .scale((if (isMuted) 1.0f else if (voiceState == "LISTENING") (pulse2 + audioBoost) else if (voiceState == "SPEAKING") pulse3 else pulse1))
                            .background(
                                color = when {
                                    isMuted -> Color(0xFF616161).copy(alpha = 0.15f)
                                    voiceState == "LISTENING" -> Color(0xFF6750A4).copy(alpha = 0.22f + audioBoost * 0.2f)
                                    voiceState == "THINKING" -> Color(0xFFE8DEF8).copy(alpha = 0.2f)
                                    voiceState == "SPEAKING" -> Color(0xFF00D2FF).copy(alpha = 0.22f)
                                    else -> Color(0xFFE8DEF8).copy(alpha = 0.12f)
                                },
                                shape = CircleShape
                            )
                    )
                    // Solid central orb
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .scale(1.0f + (audioBoost * 0.3f))
                            .background(
                                brush = Brush.radialGradient(
                                    colors = when {
                                        isMuted -> listOf(Color(0xFF616161), Color(0xFF212121))
                                        voiceState == "LISTENING" -> listOf(Color(0xFFD0BCFF), Color(0xFF6750A4))
                                        voiceState == "THINKING" -> listOf(Color(0xFFE8DEF8), Color(0xFF6750A4))
                                        voiceState == "SPEAKING" -> listOf(Color(0xFF00D2FF), Color(0xFF6750A4))
                                        else -> listOf(Color(0xFFEADDFF), Color(0xFF6750A4))
                                    }
                                ),
                                shape = CircleShape
                            )
                            .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isMuted -> Icons.Default.MicOff
                                voiceState == "LISTENING" -> Icons.Default.Mic
                                voiceState == "THINKING" -> Icons.Default.HourglassTop
                                voiceState == "SPEAKING" -> Icons.Default.VolumeUp
                                else -> Icons.Default.Mic
                            },
                            contentDescription = "Companion Presence State",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // LIVE CONVERSATION CAPTIONS OVERLAY (Transparent, Real-Time Stream, Auto-Scrolling)
                AnimatedVisibility(
                    visible = isCaptionsOverlayVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (liveCaptions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp, max = 150.dp)
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Live captions will appear here in real time as you speak...",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 12.sp,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            state = captionsListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp, max = 190.dp)
                                .padding(horizontal = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(liveCaptions, key = { it.id }) { caption ->
                                val isUser = caption.sender == "user"
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.88f)
                                            .background(
                                                color = if (isUser) Color(0xFF6750A4).copy(alpha = 0.50f) else Color(0xFF1E1A38).copy(alpha = 0.65f),
                                                shape = RoundedCornerShape(
                                                    topStart = 14.dp,
                                                    topEnd = 14.dp,
                                                    bottomStart = if (isUser) 14.dp else 2.dp,
                                                    bottomEnd = if (isUser) 2.dp else 14.dp
                                                )
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isUser) Color(0xFFD0BCFF).copy(alpha = 0.5f) else Color(0xFF81D4FA).copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(
                                                    topStart = 14.dp,
                                                    topEnd = 14.dp,
                                                    bottomStart = if (isUser) 14.dp else 2.dp,
                                                    bottomEnd = if (isUser) 2.dp else 14.dp
                                                )
                                            )
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(
                                                            if (isUser) Color(0xFF80D8FF) else Color(0xFFD0BCFF),
                                                            CircleShape
                                                        )
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isUser) "YOU" else "ARIA",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isUser) Color(0xFF80D8FF) else Color(0xFFEADDFF)
                                                )
                                                if (!caption.isFinal) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "• live",
                                                        fontSize = 9.sp,
                                                        color = Color.White.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = caption.text,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                lineHeight = 17.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Bottom Section: Minimal Control Panel (Mute Microphone & Disconnect Call only)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Microphone Mute Toggle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            if (isMuted) {
                                if (voiceState != "IDLE") {
                                    viewModel.stopRealVoiceSession()
                                }
                            } else {
                                if (voiceState == "IDLE") {
                                    viewModel.startRealVoiceSession()
                                }
                            }
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .background(
                                if (isMuted) Color(0xFF881111) else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isMuted) "Unmute" else "Mute",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Disconnect Button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            viewModel.handleEndSession()
                            viewModel.navigateTo("home")
                        },
                        modifier = Modifier
                            .size(68.dp)
                            .background(Color(0xFFBA1A1A), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Disconnect",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Disconnect",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 4. Overlaid Collapsible Real-Time Debug Log Bottom Sheet/Panel
        AnimatedVisibility(
            visible = isDebugPanelOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF13111E).copy(alpha = 0.96f)),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                border = BorderStroke(1.dp, Color(0xFF6750A4).copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.70f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header with Status badges and Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live Diagnostics",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Copy logs button
                            IconButton(
                                onClick = {
                                    val logText = LiveDebugLogger.getAllLogsText()
                                    clipboardManager.setText(AnnotatedString(logText))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy logs",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            // Clear logs button
                            IconButton(
                                onClick = { LiveDebugLogger.clear() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Clear logs",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            // Close button
                            IconButton(
                                onClick = { isDebugPanelOpen = false },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close debug panel",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status Bar: Voice State & WebSocket Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Voice State: ", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            Box(
                                modifier = Modifier
                                    .background(
                                        when (voiceState) {
                                            "LISTENING" -> Color(0xFF34C759).copy(alpha = 0.2f)
                                            "THINKING" -> Color(0xFFFFCC00).copy(alpha = 0.2f)
                                            "SPEAKING" -> Color(0xFF00D2FF).copy(alpha = 0.2f)
                                            else -> Color.Gray.copy(alpha = 0.2f)
                                        },
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = voiceState,
                                    color = when (voiceState) {
                                        "LISTENING" -> Color(0xFF34C759)
                                        "THINKING" -> Color(0xFFFFCC00)
                                        "SPEAKING" -> Color(0xFF00D2FF)
                                        else -> Color.LightGray
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("WebSocket: ", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                            Box(
                                modifier = Modifier
                                    .background(
                                        when (wsStatus) {
                                            "Connected" -> Color(0xFF34C759).copy(alpha = 0.2f)
                                            "Connecting" -> Color(0xFFFFCC00).copy(alpha = 0.2f)
                                            else -> Color(0xFFBA1A1A).copy(alpha = 0.2f)
                                        },
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = wsStatus,
                                    color = when (wsStatus) {
                                        "Connected" -> Color(0xFF34C759)
                                        "Connecting" -> Color(0xFFFFCC00)
                                        else -> Color(0xFFFF6B6B)
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Hardware AEC / Noise Suppressor Debug Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hardware AEC & Noise Suppressor",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isAecNsEnabled) "Acoustic Echo Canceler ON (prevents AI self-talking loop)" else "AEC & NS OFF (Raw Mic Mode for testing)",
                                color = if (isAecNsEnabled) Color(0xFF81C784) else Color(0xFFFFB74D),
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isAecNsEnabled,
                            onCheckedChange = { viewModel.toggleAecNs() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFD0BCFF),
                                checkedTrackColor = Color(0xFF6750A4)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrolling Log List
                    if (debugLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No live events logged yet. Start speaking or connect.",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            state = debugListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            items(debugLogs) { logEntry ->
                                val color = when (logEntry.level) {
                                    LiveDebugLogger.LogLevel.SUCCESS -> Color(0xFF34C759)
                                    LiveDebugLogger.LogLevel.ERROR -> Color(0xFFFF6B6B)
                                    LiveDebugLogger.LogLevel.WARN -> Color(0xFFFFCC00)
                                    LiveDebugLogger.LogLevel.DATA -> Color(0xFF81D4FA)
                                    LiveDebugLogger.LogLevel.INFO -> Color(0xFFE0E0E0)
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                ) {
                                    Text(
                                        text = logEntry.timestamp,
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = logEntry.message,
                                        color = color,
                                        fontSize = 11.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (isPersonalitySelectorOpen) {
            PersonalitySelectorDialog(
                currentPersonality = settings?.selectedPersonality ?: "Friendly",
                onSelectPersonality = { newPers: String ->
                    viewModel.updateSelectedPersonality(newPers)
                },
                onDismiss = { isPersonalitySelectorOpen = false }
            )
        }
    }
}

data class PersonalityOptionItem(
    val name: String,
    val description: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun PersonalitySelectorDialog(
    currentPersonality: String,
    onSelectPersonality: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val personalities = listOf(
        PersonalityOptionItem("Friendly", "Warm, enthusiastic & encouraging", Icons.Default.Favorite, Color(0xFF4CAF50)),
        PersonalityOptionItem("Flirty", "Charming compliments & sweet teasing", Icons.Default.FavoriteBorder, Color(0xFFE91E63)),
        PersonalityOptionItem("Talkative", "Energetic dialogue & curious questions", Icons.Default.ChatBubble, Color(0xFF2196F3)),
        PersonalityOptionItem("Witty", "Smart humor & clever banter", Icons.Default.AutoAwesome, Color(0xFFFF9800)),
        PersonalityOptionItem("Lovable", "Deeply caring, warm & validating", Icons.Default.Person, Color(0xFF9C27B0)),
        PersonalityOptionItem("Sarcastic", "Playful roasts & dry cheeky humor", Icons.Default.Face, Color(0xFF795548)),
        PersonalityOptionItem("Naughty", "Mischievous spark & lively fun", Icons.Default.Star, Color(0xFFFF5722))
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Personality Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        text = "Switch companion tone & vibe instantly",
                        fontSize = 11.sp,
                        color = Color(0xFF79747E)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                personalities.forEach { option ->
                    val isSelected = option.name.equals(currentPersonality, ignoreCase = true)
                    Card(
                        onClick = {
                            onSelectPersonality(option.name)
                            onDismiss()
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF6750A4).copy(alpha = 0.12f) else Color(0xFFF7F2FA)
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFF6750A4) else Color(0xFFE7E0EC)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("personality_option_${option.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (isSelected) Color(0xFF6750A4) else option.accentColor.copy(alpha = 0.15f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = option.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else option.accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = option.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isSelected) Color(0xFF6750A4) else Color(0xFF1D1B20)
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0xFF6750A4), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = option.description,
                                    fontSize = 11.sp,
                                    color = Color(0xFF49454F)
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF6750A4),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_personality_dialog_button")
            ) {
                Text("Close", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

// ==========================================
// 5. LIVE VIDEO IMMERSION COMPANION SCREEN
// ==========================================
@Composable
fun LiveVideoCompanionScreen(viewModel: MainViewModel) {
    val activeEmotion by viewModel.fusedEmotion.collectAsState()
    val isCameraActive by viewModel.isCameraOn.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Canvas simulator
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (isCameraActive) {
                        // Drawing simulated abstract webcam flow backgrounds
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF2C223E), Color(0xFF141218))
                            )
                        )
                    } else {
                        drawRect(Color(0xFF141218))
                    }
                }
        )

        // Pulsing AI avatar floating centerpiece
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val circleScale by infiniteTransition.animateFloat(
            initialValue = 110f,
            targetValue = 135f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "radius"
        )

        Canvas(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.Center)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFD0BCFF).copy(alpha = 0.4f), Color.Transparent),
                    radius = circleScale * 2f
                ),
                radius = circleScale * 1.5f
            )
            drawCircle(
                color = Color(0xFF6750A4),
                radius = 65f
            )
        }

        // Overlay readouts
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(20.dp)
        ) {
            IconButton(
                onClick = { viewModel.navigateTo("chat") },
                modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Close IMMERSIVE", tint = Color.White)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("FULL IMMERSIVE COMPANION", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Interactive Camera Analysis active", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFFBA1A1A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("LIVE EYE ON", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom triggers panel
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Companion Empathy Sentiment Level: ${activeEmotion.emotion}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1D1B20)
                )
                Text(
                    text = "Analyzing visual smiles, eye blinks, and hesitations and fusing with voice.",
                    fontSize = 11.sp,
                    color = Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = { viewModel.toggleCamera() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCameraActive) Color(0xFF6750A4) else Color(0xFFBA1A1A)
                        )
                    ) {
                        Text(if (isCameraActive) "Camera ON" else "Camera OFF")
                    }

                    Button(
                        onClick = { viewModel.sendMessageDirectly("I feel great today!") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4))
                    ) {
                        Text("Simulate Smile Reaction")
                    }
                }
            }
        }
    }
}

// ==========================================
// 6. JOURNEY SCREEN
// ==========================================
@Composable
fun JourneyScreen(viewModel: MainViewModel) {
    var selectedSubTab by remember { mutableStateOf("timeline") }
    val memories by viewModel.activeMemories.collectAsState()
    val pastSessions by viewModel.pastSessions.collectAsState()
    val unlockedMs by viewModel.unlockedAchievements.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(34.dp))
        Text(
            text = "Shared Journey Timeline",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B20)
        )
        Text(
            text = "Relive discussions, historical milestones, and edit companion memories.",
            fontSize = 13.sp,
            color = Color(0xFF49454F)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Selector Row
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFFF3EDF7), RoundedCornerShape(12.dp)).padding(4.dp)
        ) {
            listOf("timeline" to "Timeline", "memories" to "Memory Manager").forEach { (tabId, tabName) ->
                val isActive = selectedSubTab == tabId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isActive) Color.White else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedSubTab = tabId }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isActive) Color(0xFF6750A4) else Color(0xFF49454F)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(modifier = Modifier.weight(1f)) {
            if (selectedSubTab == "timeline") {
                // Timeline List
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    item {
                        Text("COMPLETED MILESTONES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                    }
                    if (unlockedMs.isEmpty()) {
                        item {
                            Text("No achievements unlocked yet.", color = Color(0xFF49454F), fontSize = 13.sp)
                        }
                    } else {
                        items(unlockedMs) { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(14.dp))
                                    .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFEADDFF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF21005D))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(m.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(m.description, fontSize = 12.sp, color = Color(0xFF49454F))
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("PAST SESSIONS HISTORY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
                    }

                    if (pastSessions.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("Your journey starts here. Record a session!", fontSize = 12.sp, color = Color(0xFF49454F))
                                }
                            }
                        }
                    } else {
                        items(pastSessions) { session ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(12.dp))
                                    .clickable { }
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Companion Session", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Duration: ${session.durationSec} Sec • Dominant feeling: ${session.dominantEmotion}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF49454F)
                                    )
                                    Text(
                                        "Exchange count: ${session.totalMessages} lines",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6750A4),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            } else {
                // Memories list with manual interactions
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                    if (memories.isEmpty()) {
                        item {
                            Text("No memories collected. Speak to your companion to discover facts about yourself!", color = Color(0xFF49454F), fontSize = 13.sp)
                        }
                    } else {
                        items(memories) { memo ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = memo.fact,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1D1B20)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Category: ${memo.category} • Referenced: ${memo.referenceCount} times",
                                        fontSize = 11.sp,
                                        color = Color(0xFF49454F)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = { viewModel.pinMemory(memo.memoryId, memo.importance < 5) }
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (memo.importance >= 5) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (memo.importance >= 5) "Pinned" else "Pin Memory", fontSize = 11.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(
                                            onClick = { viewModel.deleteMemory(memo.memoryId) },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFBA1A1A))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Delete", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(60.dp))
    }
}

// ==========================================
// 7. INSIGHTS SCREEN
// ==========================================
@Composable
fun InsightsScreen(viewModel: MainViewModel) {
    val progressList by viewModel.englishTrends.collectAsState()
    val wordsList by viewModel.learnedVocabulary.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(34.dp))
        Text(
            text = "Analyse Fluency Insights",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B20)
        )
        Text(
            text = "Track English conversational scores, speaking speed and vocabulary growth.",
            fontSize = 13.sp,
            color = Color(0xFF49454F)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Custom canvas draw for statistics
        Text("CONVERSATION FLUENCY TREND (LAST 4 DAYS)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
        Spacer(modifier = Modifier.height(8.dp))

        val scores = progressList.take(4).reversed().map { it.fluencyScore }
        val dates = progressList.take(4).reversed().map { "Day" }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val padding = 30f
                val spacing = (size.width - padding * 2) / 3f
                val maxValue = 100f

                // Draw base axis lines
                drawLine(
                    color = Color(0xFFCAC4D0),
                    start = Offset(padding, size.height - padding),
                    end = Offset(size.width - padding, size.height - padding),
                    strokeWidth = 2f
                )

                // Populate bar parameters
                scores.forEachIndexed { i, scoreValue ->
                    val x = padding + i * spacing
                    val barHeight = (scoreValue / maxValue) * (size.height - padding * 2)
                    val y = size.height - padding - barHeight

                    drawRoundRect(
                        color = Color(0xFF6750A4),
                        topLeft = Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(30f, barHeight),
                        cornerRadius = CornerRadius(8f, 8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Vocab list expansion breakdown
        Text("VOCABULARY EXPANSION RECORDS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
        Spacer(modifier = Modifier.height(8.dp))

        if (wordsList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No advanced synonyms learned yet.", fontSize = 13.sp, color = Color(0xFF49454F))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                wordsList.take(4).forEach { vocab ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFCAC4D0), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(vocab.word, fontWeight = FontWeight.Bold, color = Color(0xFF1D1B20), fontSize = 14.sp)
                            Text(vocab.meaning, fontSize = 12.sp, color = Color(0xFF49454F))
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFEADDFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Active Usage: ${vocab.usageCount}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF21005D))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(60.dp))
    }
}

// ==========================================
// 8. PROFILE / SETTINGS SCREEN
// ==========================================
@Composable
fun ProfileScreen(viewModel: MainViewModel) {
    val profile by viewModel.userProfile.collectAsState()
    val settings by viewModel.appSettings.collectAsState()
    val isAecNsEnabled by viewModel.isAecNsEnabled.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(34.dp))
        Text(
            text = "User Profile & Device Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1D1B20)
        )
        Text(
            text = "Adjust english support, companion voices, and camera permissions.",
            fontSize = 13.sp,
            color = Color(0xFF49454F)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Account Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("ACCOUNT INFO", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF6750A4))
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Name", fontSize = 14.sp, color = Color(0xFF49454F))
                    Text(profile?.name ?: "Marcus", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Divider(modifier = Modifier.padding(vertical = 10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Current English Level", fontSize = 14.sp, color = Color(0xFF49454F))
                    Text(profile?.englishLevel ?: "Intermediate", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Companion selection list
        Text("SELECT COMPANION TONE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
        Spacer(modifier = Modifier.height(8.dp))

        val currentPers = settings?.selectedPersonality ?: "Friendly"
        val optionList = listOf("Friendly", "Witty", "Talkative", "Sarcastic", "Lovable", "Flirty", "Naughty")
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            optionList.forEach { name ->
                val isSel = currentPers == name
                FilterChip(
                    selected = isSel,
                    onClick = { viewModel.updateSelectedPersonality(name) },
                    label = { Text(name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFEADDFF),
                        selectedLabelColor = Color(0xFF21005D)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // AI & Voice Engine Configuration Card
        val isConfigured by viewModel.isApiKeyConfigured.collectAsState()
        val isInworldConfigured by viewModel.isInworldKeyConfigured.collectAsState()
        val currentVoiceProvider by viewModel.selectedVoiceProvider.collectAsState()
        val currentSettingsKey = settings?.geminiApiKey ?: ""
        val currentInworldKey = settings?.inworldApiKey ?: ""
        val currentSettingsModel = settings?.selectedModel ?: "gemini-2.5-flash"

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, if (isConfigured) Color(0xFF6750A4) else Color(0xFFFFB74D))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AI & VOICE CONFIGURATION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF6750A4)
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isConfigured) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isConfigured) "● Ready" else "● Setup Required",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConfigured) Color(0xFF2E7D32) else Color(0xFFE65100)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voice Provider Selector
                Text(
                    text = "ACTIVE VOICE PROVIDER",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF49454F)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Gemini", "Inworld").forEach { provider ->
                        val isSel = currentVoiceProvider.equals(provider, ignoreCase = true)
                        FilterChip(
                            selected = isSel,
                            onClick = { viewModel.updateVoiceProvider(provider) },
                            label = {
                                Text(
                                    text = if (provider == "Gemini") "Google Gemini" else "Inworld AI",
                                    fontSize = 12.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFEADDFF),
                                selectedLabelColor = Color(0xFF21005D)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))

                // Gemini Key Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Gemini AI (Chat & Live)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFF1D1B20)
                        )
                        Text(
                            text = if (currentSettingsKey.isNotEmpty()) "Key: ••••••••${currentSettingsKey.takeLast(4)} ($currentSettingsModel)" else "No key configured",
                            fontSize = 11.sp,
                            color = Color(0xFF49454F)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isConfigured) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isConfigured) "Active" else "Missing",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConfigured) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Inworld Key Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Inworld AI (Voice Engine)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color(0xFF1D1B20)
                        )
                        Text(
                            text = if (currentInworldKey.isNotEmpty()) "Key: ••••••••${currentInworldKey.takeLast(4)}" else "No key configured",
                            fontSize = 11.sp,
                            color = Color(0xFF49454F)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isInworldConfigured || currentInworldKey.isNotEmpty()) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isInworldConfigured || currentInworldKey.isNotEmpty()) "Active" else "Not Set",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isInworldConfigured || currentInworldKey.isNotEmpty()) Color(0xFF2E7D32) else Color(0xFF79747E)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = { viewModel.openApiKeyDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Configure API Keys & Providers",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Safety toggles
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("PRIVACY & ANALYTICS PREFERENCES", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF6750A4))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Camera Analysis", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Processes facial smiles every 2-3 seconds to adjust companion empathy fused emotion.", fontSize = 11.sp, color = Color(0xFF49454F))
                    }
                    Switch(
                        checked = settings?.cameraEnabled ?: true,
                        onCheckedChange = { },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6750A4))
                    )
                }

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("English Invisible Correction", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Naturally models core grammar fixes inside companion conversations.", fontSize = 11.sp, color = Color(0xFF49454F))
                    }
                    Switch(
                        checked = settings?.englishCorrectionEnabled ?: true,
                        onCheckedChange = { },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6750A4))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Hardware Audio Processing & Debug
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("AUDIO HARDWARE & DIAGNOSTICS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF6750A4))
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware Echo Cancellation & NS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            text = if (isAecNsEnabled) "Acoustic Echo Canceler (AEC) & Noise Suppressor enabled to eliminate speaker-to-mic feedback loops."
                            else "AEC & NS disabled. Using raw microphone input for testing.",
                            fontSize = 11.sp,
                            color = Color(0xFF49454F)
                        )
                    }
                    Switch(
                        checked = isAecNsEnabled,
                        onCheckedChange = { viewModel.toggleAecNs() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6750A4))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Hard Actions
        Button(
            onClick = { viewModel.resetCompanionHistory() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA1A1A)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Companion Relationship Level", fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
