package app.m1k3.ai.assistant.ui.demo

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import app.m1k3.ai.assistant.avatar.AvatarEmotion
import app.m1k3.ai.assistant.avatar.AvatarView
import app.m1k3.ai.assistant.avatar.MiniAvatarIndicator
import app.m1k3.ai.assistant.avatar.rememberAvatarViewModel
import app.m1k3.ai.assistant.design.components.MaButtonPrimary
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import kotlinx.coroutines.launch

/**
 * DemoScreenComposables
 *
 * Extracted composable functions for the demo/welcome screen
 *
 * **Composables:**
 * - `MaAIDemo` - Main demo screen with avatar, system status, architecture info
 * - `StatusCard` - Individual status item card
 * - `ArchitectureCard` - Card showing tech stack layers
 * - `ArchitectureLayer` - Single architecture layer entry
 *
 * **Utilities:**
 * - `getSystemStatus` - Generate system status items for display
 * - `StatusItem` - Data class for status item
 *
 * **Features:**
 * - Displays interactive 3D avatar
 * - Shows system initialization status
 * - Lists architecture layers
 * - Provides navigation to chat screen
 * - Avatar responds to knowledge base status
 */

/**
 * StatusItem - represents a system status line item
 */
data class StatusItem(
    val name: String,
    val description: String,
    val icon: String,
    val isSuccess: Boolean
)

/**
 * Get system status items for demo display
 *
 * @param knowledgeStatus Knowledge import status message (from DatabaseInitializer)
 * @return List of StatusItem for rendering
 */
fun getSystemStatus(knowledgeStatus: String? = null): List<StatusItem> {
    return listOf(
        StatusItem(
            name = "Privacy Protection",
            description = "Zero network permission • 100% local",
            icon = "🔒",
            isSuccess = true
        ),
        StatusItem(
            name = "Database Foundation",
            description = "SQLDelight with encryption ready",
            icon = "🗄️",
            isSuccess = true
        ),
        StatusItem(
            name = "Knowledge Base",
            description = knowledgeStatus ?: "Loading...",
            icon = "📚",
            isSuccess = knowledgeStatus?.startsWith("✅") == true
        ),
        StatusItem(
            name = "Package Name",
            description = "app.m1k3.ai.assistant (ASO optimized)",
            icon = "📦",
            isSuccess = true
        ),
        StatusItem(
            name = "AI Engine",
            description = "SmolLM2-360M (Production Ready)",
            icon = "🤖",
            isSuccess = true
        ),
        StatusItem(
            name = "Design System",
            description = "AMOLED Black • Liquid Glass • Complete",
            icon = "🎨",
            isSuccess = true
        ),
        StatusItem(
            name = "Robot Avatar",
            description = "9 Emotions • 6 Activities • Canvas Rendering",
            icon = "🤖",
            isSuccess = true
        )
    )
}

/**
 * MaAIDemo - Main demo/welcome screen composable
 *
 * Displays:
 * - Interactive 3D avatar
 * - Chat navigation button
 * - System status dashboard
 * - Technology stack information
 *
 * @param onChatClick Callback when user taps chat button
 * @param onDebugClick Callback when user taps debug button (optional)
 * @param knowledgeStatus Status message for knowledge base import
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaAIDemo(
    onChatClick: () -> Unit,
    onDebugClick: () -> Unit = {},
    knowledgeStatus: String? = null
) {
    var systemStatus by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Avatar state
    val avatarVM = rememberAvatarViewModel()
    val avatarState by avatarVM.avatarState.collectAsState()

    LaunchedEffect(knowledgeStatus) {
        scope.launch {
            systemStatus = getSystemStatus(knowledgeStatus)
            // Set avatar to happy when knowledge loads successfully
            if (knowledgeStatus?.startsWith("✅") == true) {
                avatarVM.setEmotion(AvatarEmotion.HAPPY, 0.8f)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "M1K3",
                                style = MaTypography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaColors.textPrimary()
                            )
                            Text(
                                "Privacy-First Mobile Assistant",
                                style = MaTypography.bodySmall,
                                color = MaColors.textSecondary()
                            )
                        }
                        // Mini avatar in top bar
                        MiniAvatarIndicator(
                            state = avatarState,
                            modifier = Modifier.size(48.dp),
                            onClick = {
                                // Cycle through emotions on click (for demo)
                                val emotions = listOf(
                                    AvatarEmotion.HAPPY, AvatarEmotion.EXCITED,
                                    AvatarEmotion.LOVE, AvatarEmotion.THINKING
                                )
                                val nextEmotion = emotions.random()
                                avatarVM.setEmotion(nextEmotion, 0.8f)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md)
        ) {
            // Avatar Display - 3D Colobus Monkey with third eye perspective
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(
                        state = avatarState,
                        showInfo = true,
                        use3D = true,  // ✅ ENABLED: Reference-counted engine prevents crashes
                        onClick = {
                            avatarVM.flashEmotion(AvatarEmotion.EXCITED, 1500)
                        }
                    )
                }
            }

            // Chat Button
            item {
                MaButtonPrimary(
                    onClick = onChatClick,
                    text = "💬 Chat with M1K3 AI",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Debug Button
            item {
                MaCard(
                    onClick = onDebugClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaSpacing.base),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🎨 Avatar Debug Lab",
                                style = MaTypography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaColors.textPrimary()
                            )
                            Text(
                                "Test 3D avatar • All emotions • Performance metrics",
                                style = MaTypography.bodySmall,
                                color = MaColors.textSecondary()
                            )
                        }
                        Text("→", style = MaTypography.headlineMedium, color = MaColors.Orange)
                    }
                }
            }

            // Hero Section
            item {
                MaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(MaSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
                    ) {
                        Text(
                            "🎉 Design System + Avatar Complete",
                            style = MaTypography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.Orange
                        )
                        Text(
                            "AMOLED Black • Liquid Glass • Robot Avatar • Streaming Inference",
                            style = MaTypography.bodyMedium,
                            color = MaColors.textSecondary()
                        )
                    }
                }
            }

            // System Status Section
            item {
                Text(
                    "System Status",
                    style = MaTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaColors.textPrimary(),
                    modifier = Modifier.padding(vertical = MaSpacing.sm)
                )
            }

            items(systemStatus) { status ->
                StatusCard(status)
            }

            // Architecture Section
            item {
                Text(
                    "Architecture",
                    style = MaTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaColors.textPrimary(),
                    modifier = Modifier.padding(vertical = MaSpacing.sm)
                )
            }

            item {
                ArchitectureCard()
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(MaSpacing.base))
                Text(
                    "💡 100% Local • Zero Network • Privacy-First",
                    style = MaTypography.bodySmall,
                    color = MaColors.textDisabled(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * StatusCard - displays a single system status item
 *
 * @param status Status item to display
 */
@Composable
fun StatusCard(status: StatusItem) {
    MaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    status.name,
                    style = MaTypography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (status.isSuccess) MaColors.textPrimary() else MaColors.Error
                )
                Text(
                    status.description,
                    style = MaTypography.bodySmall,
                    color = MaColors.textSecondary()
                )
            }
            Text(
                status.icon,
                style = MaTypography.headlineMedium
            )
        }
    }
}

/**
 * ArchitectureCard - displays the tech stack layers
 */
@Composable
fun ArchitectureCard() {
    MaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md)
        ) {
            ArchitectureLayer("Kotlin Multiplatform 2.2.20", "Cross-platform foundation")
            HorizontalDivider(color = MaColors.BorderSubtle)
            ArchitectureLayer("Compose Multiplatform 1.9.1", "Modern UI framework")
            HorizontalDivider(color = MaColors.BorderSubtle)
            ArchitectureLayer("SQLDelight 2.0.2", "Type-safe database")
            HorizontalDivider(color = MaColors.BorderSubtle)
            ArchitectureLayer("ONNX Runtime 1.23.1", "Local AI inference")
            HorizontalDivider(color = MaColors.BorderSubtle)
            ArchitectureLayer("CameraX + ML Kit", "Multi-modal vision")
        }
    }
}

/**
 * ArchitectureLayer - displays a single architecture layer
 *
 * @param name Layer name with version
 * @param description Layer purpose/description
 */
@Composable
fun ArchitectureLayer(name: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaColors.textPrimary()
            )
            Text(
                description,
                style = MaTypography.bodySmall,
                color = MaColors.textSecondary()
            )
        }
    }
}
