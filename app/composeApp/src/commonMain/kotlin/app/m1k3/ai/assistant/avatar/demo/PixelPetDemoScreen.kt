package app.m1k3.ai.assistant.avatar.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.*
import app.m1k3.ai.assistant.avatar.ui.PetStatsBar
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.eco.EcoCalculator
import app.m1k3.ai.assistant.eco.EcoMetricsRepository
import app.m1k3.ai.assistant.eco.EcoSavings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 間 AI Pixel Pet Demo Screen
 *
 * Interactive demonstration of the eco-integrated pixel pet system.
 * Shows real-time updates, particle effects, evolution progression, and user interactions.
 *
 * **Features Demonstrated:**
 * - Eco metrics → pet stat boosts
 * - Particle effects (water/energy/CO2)
 * - Evolution progression (BASIC → LEGENDARY)
 * - Touch interactions (pat/double-tap/long-press)
 * - Stat bars with eco tooltips
 * - Achievement unlocking
 * - Real-time visual feedback
 *
 * **Usage:**
 * ```kotlin
 * @Composable
 * fun DemoApp(database: MaDatabase) {
 *     PixelPetDemoScreen(database = database)
 * }
 * ```
 */
@Composable
fun PixelPetDemoScreen(
    database: MaDatabase,
    modifier: Modifier = Modifier
) {
    // Setup
    val scope = rememberCoroutineScope()
    val ecoRepo = remember { EcoMetricsRepository(database) }
    val petViewModel = remember { PetViewModel(ecoRepo, scope) }
    val avatarState = remember { mutableStateOf(AvatarState(emotion = AvatarEmotion.HAPPY)) }

    // State
    val petState by petViewModel.petState.collectAsState()
    var isSimulating by remember { mutableStateOf(false) }
    var simulationStatus by remember { mutableStateOf("Ready") }
    var showPixelGrid by remember { mutableStateOf(false) }
    var showResolutionDebug by remember { mutableStateOf(true) }
    var useRoundedPixels by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaColors.BgPrimary)
            .verticalScroll(rememberScrollState())
            .padding(MaSpacing.base),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Text(
            text = "🌱 Pixel Pet Demo",
            style = MaTypography.headlineMedium,
            color = MaColors.TextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(MaSpacing.sm))

        Text(
            text = "Eco-Integrated Virtual Companion",
            style = MaTypography.bodyMedium,
            color = MaColors.TextSecondary
        )

        Spacer(modifier = Modifier.height(MaSpacing.lg))

        // Pixel Pet Display
        PixelPetView(
            petState = petState,
            avatarState = avatarState.value,
            petViewModel = petViewModel,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp),
            showStatBars = true,
            showEnvironment = true,
            enableInteractions = true,
            showPixelGrid = showPixelGrid,
            showResolutionDebug = showResolutionDebug,
            useRoundedPixels = useRoundedPixels
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        // Stats Overview Card
        StatsOverviewCard(petState = petState)

        Spacer(modifier = Modifier.height(MaSpacing.base))

        // Simulation Status
        if (isSimulating) {
            MaCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaSpacing.base),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🔄 $simulationStatus",
                        style = MaTypography.bodyMedium,
                        color = MaColors.Orange
                    )
                }
            }
            Spacer(modifier = Modifier.height(MaSpacing.base))
        }

        // Display Settings Card
        DisplaySettingsCard(
            showPixelGrid = showPixelGrid,
            onShowPixelGridChange = { showPixelGrid = it },
            showResolutionDebug = showResolutionDebug,
            onShowResolutionDebugChange = { showResolutionDebug = it },
            useRoundedPixels = useRoundedPixels,
            onUseRoundedPixelsChange = { useRoundedPixels = it }
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        // Control Panel
        ControlPanel(
            petViewModel = petViewModel,
            avatarState = avatarState,
            isSimulating = isSimulating,
            onSimulationStart = {
                isSimulating = true
                scope.launch {
                    simulateDailyUsage(
                        petViewModel = petViewModel,
                        avatarState = avatarState,
                        onStatusUpdate = { simulationStatus = it }
                    )
                    isSimulating = false
                    simulationStatus = "Simulation complete! 🎉"
                }
            },
            onEvolutionBoost = {
                scope.launch {
                    // Massive eco savings to trigger evolution
                    val hugeSavings = EcoSavings(1000, 120000, 300000, 2000, 0) // 120L water
                    petViewModel.onEcoMetricsRecorded(hugeSavings)
                    avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.EXCITED)
                    delay(500)
                    avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.HAPPY)
                }
            },
            onReset = {
                // Reset pet to initial state
                scope.launch {
                    // This would require adding a reset method to PetViewModel
                    simulationStatus = "Reset functionality coming soon!"
                }
            }
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        // Quick Action Buttons
        QuickActionsPanel(
            petViewModel = petViewModel,
            avatarState = avatarState
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        // Documentation
        DocumentationCard()

        Spacer(modifier = Modifier.height(MaSpacing.xl))
    }
}

/**
 * Stats overview card showing key metrics
 */
@Composable
private fun StatsOverviewCard(petState: PixelPetState) {
    MaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base)
        ) {
            Text(
                text = "📊 Pet Statistics",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            // Evolution Stage
            StatRow(
                label = "Evolution Stage",
                value = "${petState.evolutionStage.displayName} (${petState.evolutionStage.ordinal}/4)",
                color = MaColors.Orange
            )

            // Conversations
            StatRow(
                label = "Conversations",
                value = "${petState.conversationCount}",
                color = MaColors.TextPrimary
            )

            // Pats Received
            StatRow(
                label = "Pats Received",
                value = "${petState.patsReceived}",
                color = Color(0xFFE91E63)
            )

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            // Eco Metrics
            Text(
                text = "🌍 Environmental Impact",
                style = MaTypography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(MaSpacing.xs))

            StatRow(
                label = "Water Saved",
                value = EcoCalculator.formatWater(petState.lifetimeWaterMl.toInt()),
                color = Color(0xFF4CAF50)
            )

            StatRow(
                label = "Energy Saved",
                value = EcoCalculator.formatEnergy(petState.lifetimeEnergyWh.toInt()),
                color = Color(0xFFFFEB3B)
            )

            StatRow(
                label = "CO₂ Prevented",
                value = EcoCalculator.formatCO2(petState.lifetimeCO2G.toInt()),
                color = Color(0xFF8BC34A)
            )

            // Current Achievement
            if (petState.currentAchievement != null) {
                Spacer(modifier = Modifier.height(MaSpacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaColors.Orange.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(MaSpacing.sm),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🏆 ${petState.currentAchievement}",
                        style = MaTypography.bodySmall,
                        color = MaColors.Orange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Single stat row
 */
@Composable
private fun StatRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaTypography.bodySmall,
            color = MaColors.TextSecondary
        )
        Text(
            text = value,
            style = MaTypography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Main control panel with simulation and evolution controls
 */
@Composable
private fun ControlPanel(
    petViewModel: PetViewModel,
    avatarState: MutableState<AvatarState>,
    isSimulating: Boolean,
    onSimulationStart: () -> Unit,
    onEvolutionBoost: () -> Unit,
    onReset: () -> Unit
) {
    MaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base)
        ) {
            Text(
                text = "🎮 Control Panel",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(MaSpacing.base))

            // Simulate Daily Usage Button
            Button(
                onClick = onSimulationStart,
                enabled = !isSimulating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaColors.Orange,
                    disabledContainerColor = MaColors.TextDisabled
                )
            ) {
                Text(
                    text = if (isSimulating) "⏳ Simulating..." else "🚀 Simulate Daily Usage (20 chats)",
                    style = MaTypography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            // Evolution Boost Button
            Button(
                onClick = onEvolutionBoost,
                enabled = !isSimulating,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaColors.Orange
                )
            ) {
                Text(
                    text = "⚡ Evolution Boost (120L water)",
                    style = MaTypography.bodyMedium
                )
            }
        }
    }
}

/**
 * Quick actions panel for interactions
 */
@Composable
private fun QuickActionsPanel(
    petViewModel: PetViewModel,
    avatarState: MutableState<AvatarState>
) {
    val scope = rememberCoroutineScope()

    MaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base)
        ) {
            Text(
                text = "👆 Quick Actions",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(MaSpacing.base))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm)
            ) {
                // Single Pat
                Button(
                    onClick = {
                        scope.launch {
                            petViewModel.onPat(InteractionType.PAT)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.LOVE)
                            delay(500)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.HAPPY)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
                ) {
                    Text("💙 Pat", style = MaTypography.bodySmall)
                }

                // Double Tap
                Button(
                    onClick = {
                        scope.launch {
                            petViewModel.onPat(InteractionType.DOUBLE_TAP)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.EXCITED)
                            delay(500)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.HAPPY)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B9D))
                ) {
                    Text("💕 Double", style = MaTypography.bodySmall)
                }

                // Long Press
                Button(
                    onClick = {
                        scope.launch {
                            petViewModel.onPat(InteractionType.LONG_PRESS)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.LOVE)
                            delay(1000)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.HAPPY)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
                ) {
                    Text("✨ Long", style = MaTypography.bodySmall)
                }
            }

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm)
            ) {
                // Small Chat
                Button(
                    onClick = {
                        scope.launch {
                            val savings = EcoSavings(50, 60, 1500, 1, 0) // Small query
                            petViewModel.onEcoMetricsRecorded(savings)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.THINKING)
                            delay(500)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.HAPPY)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                ) {
                    Text("💬 Chat\n(50 tok)", style = MaTypography.labelSmall)
                }

                // Medium Chat
                Button(
                    onClick = {
                        scope.launch {
                            val savings = EcoSavings(150, 180, 4500, 3, 0) // Medium query
                            petViewModel.onEcoMetricsRecorded(savings)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.THINKING)
                            delay(800)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.HAPPY)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4))
                ) {
                    Text("💬 Chat\n(150 tok)", style = MaTypography.labelSmall)
                }

                // Large Chat
                Button(
                    onClick = {
                        scope.launch {
                            val savings = EcoSavings(500, 600, 15000, 10, 0) // Large query
                            petViewModel.onEcoMetricsRecorded(savings)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.THINKING)
                            delay(1500)
                            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.HAPPY)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BCD4))
                ) {
                    Text("💬 Chat\n(500 tok)", style = MaTypography.labelSmall)
                }
            }
        }
    }
}

/**
 * Documentation card explaining the system
 */
@Composable
private fun DocumentationCard() {
    MaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base)
        ) {
            Text(
                text = "📖 How It Works",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            DocumentationPoint(
                emoji = "💧",
                title = "Water → Health",
                description = "100ml water saved = +0.1 health point"
            )

            DocumentationPoint(
                emoji = "⚡",
                title = "Energy → Energy",
                description = "10 Wh saved = +0.15 energy point"
            )

            DocumentationPoint(
                emoji = "🌱",
                title = "CO₂ → Happiness",
                description = "20g CO₂ prevented = +0.2 happiness point"
            )

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            Text(
                text = "🎯 Evolution Stages",
                style = MaTypography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(MaSpacing.xs))

            Text(
                text = "• BASIC: 0-4,999ml\n• INTERMEDIATE: 5,000ml (Bucket)\n• ADVANCED: 100,000ml (Bathtub)\n• EXPERT: 1,000,000ml (Pool)\n• LEGENDARY: 2,500,000ml (Olympic Pool)",
                style = MaTypography.bodySmall,
                color = MaColors.TextSecondary
            )
        }
    }
}

/**
 * Single documentation point
 */
@Composable
private fun DocumentationPoint(emoji: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = emoji,
            style = MaTypography.bodyMedium,
            modifier = Modifier.width(32.dp)
        )
        Column {
            Text(
                text = title,
                style = MaTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaColors.TextPrimary
            )
            Text(
                text = description,
                style = MaTypography.bodySmall,
                color = MaColors.TextSecondary
            )
        }
    }
}

/**
 * Simulate daily usage pattern
 */
private suspend fun simulateDailyUsage(
    petViewModel: PetViewModel,
    avatarState: MutableState<AvatarState>,
    onStatusUpdate: (String) -> Unit
) {
    onStatusUpdate("Starting simulation...")
    delay(500)

    // Simulate 20 conversations throughout the day
    repeat(20) { i ->
        onStatusUpdate("Chat ${i + 1}/20 - Processing...")

        // Vary query sizes (realistic mix)
        val savings = when (i % 3) {
            0 -> EcoSavings(50, 60, 1500, 1, 0)      // Small query
            1 -> EcoSavings(150, 180, 4500, 3, 0)    // Medium query
            else -> EcoSavings(300, 360, 9000, 6, 0) // Large query
        }

        // Animate avatar
        avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.THINKING)
        delay(300)

        // Record eco metrics
        petViewModel.onEcoMetricsRecorded(savings)

        avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.HAPPY)
        delay(400)

        // Occasional pats
        if (i % 5 == 0) {
            onStatusUpdate("Chat ${i + 1}/20 - Pat received!")
            petViewModel.onPat(InteractionType.PAT)
            avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.LOVE)
            delay(300)
        }

        delay(200) // Pause between chats
    }

    onStatusUpdate("Simulation complete! 🎉")
    avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.EXCITED)
    delay(2000)
    avatarState.value = avatarState.value.copy(emotion = AvatarEmotion.HAPPY)
}


/**
 * Display settings card with adaptive resolution toggles
 */
@Composable
private fun DisplaySettingsCard(
    showPixelGrid: Boolean,
    onShowPixelGridChange: (Boolean) -> Unit,
    showResolutionDebug: Boolean,
    onShowResolutionDebugChange: (Boolean) -> Unit,
    useRoundedPixels: Boolean,
    onUseRoundedPixelsChange: (Boolean) -> Unit
) {
    MaCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base)
        ) {
            Text(
                text = "🎨 Display Settings",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaColors.TextPrimary
            )

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            Text(
                text = "Adaptive Resolution: Pixel grid scales based on container size (16x16 → 64x64)",
                style = MaTypography.bodySmall,
                color = MaColors.TextSecondary
            )

            Spacer(modifier = Modifier.height(MaSpacing.base))

            // Pixel Grid Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Pixel Grid",
                        style = MaTypography.bodyMedium,
                        color = MaColors.TextPrimary
                    )
                    Text(
                        text = "Subtle grid lines for extra detail",
                        style = MaTypography.bodySmall,
                        color = MaColors.TextSecondary
                    )
                }
                Switch(
                    checked = showPixelGrid,
                    onCheckedChange = onShowPixelGridChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaColors.Orange,
                        checkedTrackColor = MaColors.Orange.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            // Resolution Debug Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Resolution Debug",
                        style = MaTypography.bodyMedium,
                        color = MaColors.TextPrimary
                    )
                    Text(
                        text = "Shows current grid size (16/32/48/64 pixels)",
                        style = MaTypography.bodySmall,
                        color = MaColors.TextSecondary
                    )
                }
                Switch(
                    checked = showResolutionDebug,
                    onCheckedChange = onShowResolutionDebugChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaColors.Orange,
                        checkedTrackColor = MaColors.Orange.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            // Rounded Pixels Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Rounded Pixels",
                        style = MaTypography.bodyMedium,
                        color = MaColors.TextPrimary
                    )
                    Text(
                        text = "Adds 1px padding and subtle corner rounding (15%)",
                        style = MaTypography.bodySmall,
                        color = MaColors.TextSecondary
                    )
                }
                Switch(
                    checked = useRoundedPixels,
                    onCheckedChange = onUseRoundedPixelsChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaColors.Orange,
                        checkedTrackColor = MaColors.Orange.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}
