package app.m1k3.ai.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.preview.PreviewFixtures
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.domain.ai.LlmModel

/**
 * ChatInputBar - Beautiful input field with integrated send button.
 *
 * Features:
 * - Claude-style integrated send button
 * - Focus animations with glow effect
 * - Haptic feedback on typing and send
 * - Accessible with test tags
 *
 * @param text Current input text
 * @param onTextChange Callback when text changes
 * @param onSend Callback when send button is pressed
 * @param enabled Whether input is enabled
 * @param modifier Modifier for the container
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    currentModel: LlmModel? = null,
    onModelSwitch: ((LlmModel) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val hasText = text.isNotBlank()

    // Haptic feedback controller
    val haptics = rememberHapticFeedback()

    // Track previous text for "typing start" detection
    var previousText by remember { mutableStateOf(text) }

    // Detect typing start for haptic feedback
    LaunchedEffect(text) {
        if (previousText.isEmpty() && text.isNotEmpty()) {
            haptics.light() // Subtle feedback when typing starts
        }
        previousText = text
    }

    // Enhanced focus animations
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaColors.BorderLight
            isFocused -> MaColors.Orange
            else -> MaColors.BorderLight
        },
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "borderColor"
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else 1.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "borderWidth"
    )

    // Subtle elevation on focus
    val elevation by animateDpAsState(
        targetValue = if (isFocused) 4.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "elevation"
    )

    // Subtle scale on focus for depth perception
    val fieldScale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "fieldScale"
    )

    // Animated send button scale with bouncy spring
    val sendButtonScale by animateFloatAsState(
        targetValue = if (hasText && enabled) 1f else 0.85f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sendButtonScale"
    )

    Surface(
        modifier = modifier.fillMaxWidth().background(
            color = MaColors.BgSecondary
        ),
        color = MaColors.BgSecondary // Transparent to show gradient overlay
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Model chip for switching between models
            if (currentModel != null && onModelSwitch != null) {
                ModelChip(
                    currentModel = currentModel,
                    onModelSwitch = onModelSwitch,
                    enabled = enabled,
                    modifier = Modifier.padding(
                        start = MaSpacing.base,
                        top = MaSpacing.xs,
                        bottom = MaSpacing.xs
                    )
                )
            }

            // Input field with send button
            Box(modifier = Modifier.fillMaxWidth()) {
            // Integrated input field with send button
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .testTag("input_field")
                    .fillMaxWidth()
                    .heightIn(min = 56.dp, max = 180.dp)
                    .scale(fieldScale)
                    .padding(MaSpacing.base),
                enabled = enabled,
                textStyle = MaTypography.bodyLarge.copy(
                    color = if (enabled) MaColors.textPrimary() else MaColors.textDisabled()
                ),
                cursorBrush = SolidColor(MaColors.Orange),
                maxLines = 6,
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = { if (hasText && enabled) onSend() }
                ),
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaSpacing.base)

                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = "Chat with M1K3",
                                style = MaTypography.bodyLarge,
                                color = MaColors.textDisabled(),
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // Integrated send button (Claude-style)
            Box(
                modifier = Modifier
                    .testTag("send_button")
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
                    .size(40.dp)
                    .scale(sendButtonScale)
                    .clip(CircleShape)
                    .background(
                        color = if (hasText && enabled) MaColors.Orange else MaColors.BgSecondary,
                        shape = CircleShape
                    )
                    .clickable(
                        enabled = hasText && enabled,
                        onClick = {
                            haptics.strong() // Strong feedback on send
                            onSend()
                        },
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Custom arrow icon (↑)
                SendArrowIcon(
                    color = if (hasText && enabled) MaColors.White else MaColors.textDisabled()
                )
            }
            } // Box (input + send button)
        } // Column
    }
}

/**
 * Custom send arrow icon drawn with Canvas.
 */
@Composable
private fun SendArrowIcon(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier.size(20.dp)
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.5f
        val centerX = size.width / 2
        val centerY = size.height / 2
        val arrowLength = size.height * 0.5f

        // Draw arrow shaft (vertical line)
        drawLine(
            color = color,
            start = Offset(centerX, centerY + arrowLength / 2),
            end = Offset(centerX, centerY - arrowLength / 2),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Draw arrow head (left line)
        drawLine(
            color = color,
            start = Offset(centerX, centerY - arrowLength / 2),
            end = Offset(
                centerX - arrowLength / 3,
                centerY - arrowLength / 2 + arrowLength / 3
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        // Draw arrow head (right line)
        drawLine(
            color = color,
            start = Offset(centerX, centerY - arrowLength / 2),
            end = Offset(
                centerX + arrowLength / 3,
                centerY - arrowLength / 2 + arrowLength / 3
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Model chip for switching between LLM models.
 *
 * Shows current model name; tap to open dropdown with all available models.
 */
@Composable
private fun ModelChip(
    currentModel: LlmModel,
    onModelSwitch: (LlmModel) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaColors.Orange.copy(alpha = if (enabled) 0.15f else 0.08f))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = currentModel.displayName,
                style = MaTypography.labelSmall,
                color = if (enabled) MaColors.Orange else MaColors.textDisabled()
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LlmModel.all().forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = model.displayName,
                            style = MaTypography.bodyMedium,
                            color = if (model == currentModel) MaColors.Orange else MaColors.textPrimary()
                        )
                    },
                    onClick = {
                        expanded = false
                        if (model != currentModel) onModelSwitch(model)
                    }
                )
            }
        }
    }
}

/**
 * Container for ChatInputBar with gradient overlay for liquid glass effect.
 *
 * @param inputBar The ChatInputBar composable
 * @param modifier Modifier for positioning
 */
@Composable
fun ChatInputBarContainer(
    inputBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        // Gradient overlay for liquid glass effect (theme-aware)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.0f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Input bar content
        inputBar()
    }
}

// ============================================================
// Previews
// ============================================================

@Preview
@Composable
private fun ChatInputBarEmptyPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            ChatInputBar(
                text = "",
                onTextChange = PreviewFixtures.noOpOnTextChange,
                onSend = PreviewFixtures.noOpOnClick,
                enabled = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun ChatInputBarWithTextPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            ChatInputBar(
                text = "What is machine learning?",
                onTextChange = PreviewFixtures.noOpOnTextChange,
                onSend = PreviewFixtures.noOpOnClick,
                enabled = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun ChatInputBarMultilinePreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            ChatInputBar(
                text = """Can you help me understand:
1. Machine learning basics
2. Neural networks
3. Applications""",
                onTextChange = PreviewFixtures.noOpOnTextChange,
                onSend = PreviewFixtures.noOpOnClick,
                enabled = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Preview
@Composable
private fun ChatInputBarDisabledPreview() {
    MaTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.BgPrimary)
                .padding(MaSpacing.base)
        ) {
            ChatInputBar(
                text = "Processing...",
                onTextChange = PreviewFixtures.noOpOnTextChange,
                onSend = PreviewFixtures.noOpOnClick,
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
