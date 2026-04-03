package app.m1k3.ai.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.components.MaButtonPrimary
import app.m1k3.ai.assistant.design.effects.glassmorphicCard
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * 間 AI - Demo/Welcome Screen
 *
 * Introduction to 間 AI features and philosophy.
 *
 * **Features:**
 * - Privacy-first explanation (0 bytes transmitted)
 * - On-device AI showcase (SmolLM2-360M)
 * - Feature highlights (RAG, multi-modal, memory)
 * - Quick start guide
 *
 * **Philosophy:**
 * 間 (Ma) - Negative space. Computational sufficiency.
 * Beauty in simplicity and privacy.
 */
@Composable
fun DemoScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var currentSection by remember { mutableIntStateOf(0) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaColors.bgPrimary())
            .padding(horizontal = MaSpacing.base),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaSpacing.base)
    ) {
        // Welcome Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaSpacing.md),
                modifier = Modifier.padding(top = MaSpacing.xxl, bottom = MaSpacing.base)
            ) {
                // Animated Logo
                PulsingCircle()

                Text(
                    text = "間 AI",
                    style = MaTypography.displayMedium,
                    color = MaColors.textPrimary()
                )

                Text(
                    text = "Privacy-First On-Device AI",
                    style = MaTypography.titleMedium,
                    color = MaColors.textSecondary()
                )
            }
        }

        // Privacy Promise — hero card
        item {
            FeatureCard(
                title = "100% Private",
                description = "All AI runs on your device. Zero data leaves. No cloud. No compromise.",
                icon = Icons.Default.Lock,
                iconColor = MaColors.Orange
            )
        }

        // Section header
        item {
            Text(
                text = "CAPABILITIES",
                style = MaTypography.labelMedium,
                color = MaColors.Orange,
                letterSpacing = MaTypography.labelMedium.letterSpacing * 2,
                modifier = Modifier.padding(top = MaSpacing.md)
            )
        }

        item {
            FeatureCard(
                title = "SmolLM2-360M",
                description = "360M parameter model. Fast, capable, entirely on-device.",
                icon = Icons.Default.Memory
            )
        }

        item {
            FeatureCard(
                title = "Semantic Memory",
                description = "24K context with intelligent recall across conversations.",
                icon = Icons.Default.Psychology
            )
        }

        item {
            FeatureCard(
                title = "Knowledge Base",
                description = "1,401 documents across 24 categories. Local RAG, no internet.",
                icon = Icons.Default.MenuBook
            )
        }

        item {
            FeatureCard(
                title = "Multi-Modal Vision",
                description = "CameraX + ML Kit. All vision processing stays on your device.",
                icon = Icons.Default.CameraAlt
            )
        }

        item {
            FeatureCard(
                title = "Eco-Credentials",
                description = "Every local inference saves ~3Wh and ~120ml water vs cloud AI.",
                icon = Icons.Default.Eco,
                iconColor = MaColors.Success
            )
        }

        // Philosophy section
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaSpacing.md),
                modifier = Modifier.padding(vertical = MaSpacing.lg)
            ) {
                Text(
                    text = "間 (Ma)",
                    style = MaTypography.headlineSmall,
                    color = MaColors.Orange,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Negative space. Computational sufficiency.\nBeauty in what's enough.",
                    style = MaTypography.bodyMedium,
                    color = MaColors.textSecondary(),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Get Started
        item {
            MaButtonPrimary(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onGetStarted()
                },
                text = "Get Started",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MaSpacing.lg)
            )
        }

        // Bottom padding for nav bar
        item {
            Spacer(modifier = Modifier.height(MaSpacing.xxxl))
        }
    }
}

/**
 * Feature card with icon and description — glassmorphic M1K3 styling
 */
@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    iconColor: androidx.compose.ui.graphics.Color = MaColors.textSecondary()
) {
    val cardShape = RoundedCornerShape(MaRadius.md)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassmorphicCard(shape = cardShape)
            .padding(MaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MaSpacing.md)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.xs)
        ) {
            Text(
                text = title,
                style = MaTypography.titleMedium,
                color = MaColors.textPrimary()
            )
            Text(
                text = description,
                style = MaTypography.bodyMedium,
                color = MaColors.textSecondary()
            )
        }
    }
}

/**
 * Pulsing M1K3 logo — concentric orange rings breathing on AMOLED black
 */
@Composable
private fun PulsingCircle() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val outerScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outerScale"
    )
    val innerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "innerAlpha"
    )

    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer pulsing ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(outerScale)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = MaColors.Orange.copy(alpha = innerAlpha),
                    shape = CircleShape
                )
        )

        // Middle ring
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaColors.Orange.copy(alpha = 0.08f))
                .border(
                    width = 1.dp,
                    color = MaColors.OrangeDim,
                    shape = CircleShape
                )
        )

        // Center text mark
        Text(
            text = "間",
            style = MaTypography.displaySmall,
            color = MaColors.Orange
        )
    }
}

/**
 * Preview for Demo Screen
 */
@Preview(showBackground = true)
@Composable
private fun DemoScreenPreview() {
    MaTheme {
        DemoScreen(onGetStarted = {})
    }
}
