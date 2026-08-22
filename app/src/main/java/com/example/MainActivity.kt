package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                val currentScreen by viewModel.currentScreen.collectAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        // Display navigation bar only on main application views
                        if (currentScreen in listOf("home", "journey", "insights", "profile")) {
                            BottomNavBar(
                                currentScreen = currentScreen,
                                onNavigate = { viewModel.navigateTo(it) }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                bottom = if (currentScreen in listOf("home", "journey", "insights", "profile")) {
                                    innerPadding.calculateBottomPadding() / 2f
                                } else 0.dp
                            )
                    ) {
                        when (currentScreen) {
                            "splash" -> SplashScreen()
                            "onboarding" -> OnboardingScreen(viewModel = viewModel)
                            "home" -> HomeScreen(viewModel = viewModel)
                            "chat" -> ChatScreen(viewModel = viewModel)
                            "video_room" -> LiveVideoCompanionScreen(viewModel = viewModel)
                            "journey" -> JourneyScreen(viewModel = viewModel)
                            "insights" -> InsightsScreen(viewModel = viewModel)
                            "profile" -> ProfileScreen(viewModel = viewModel)
                            else -> HomeScreen(viewModel = viewModel)
                        }

                        val showApiKeyDialog by viewModel.showApiKeyDialog.collectAsState()
                        if (showApiKeyDialog) {
                            ApiKeySetupDialog(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavBar(
    currentScreen: String,
    onNavigate: (String) -> Unit
) {
    Surface(
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFEF7FF)),
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(72.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navItems = listOf(
                NavItem("home", "Home", Icons.Default.Home),
                NavItem("chat", "Chat", Icons.Default.ChatBubble),
                NavItem("journey", "Journey", Icons.Default.Timeline),
                NavItem("insights", "Insights", Icons.Default.BarChart),
                NavItem("profile", "Profile", Icons.Default.Person)
            )

            navItems.forEach { item ->
                val isActive = currentScreen == item.route
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onNavigate(item.route) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(32.dp)
                            .background(
                                color = if (isActive) Color(0xFFEADDFF) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (isActive) Color(0xFF21005D) else Color(0xFF49454F),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) Color(0xFF21005D) else Color(0xFF49454F)
                    )
                }
            }
        }
    }
}

private data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)
