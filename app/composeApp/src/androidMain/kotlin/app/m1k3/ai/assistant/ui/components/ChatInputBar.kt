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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.preview.PreviewFixtures
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.domain.ai.LlmModel

/**
 * ChatInputBar - Glassmorphic floating input with integrated send.
 *
 * Modern pill-shaped input field inspired by Claude/ChatGPT:
 * - Glassmorphic container with subtle border
 * - Orange glow on focus
 * - Animated send button (appears when text present)
 * - Model chip above input for quick switching
 * - Haptic feedback on typing start and send
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    currentModel: LlmModel? = null,
    onModelSwitch: ((LlmModel) -> Unit)? = null,
    autoVoiceReply: Boolean = false,
    onAutoVoiceToggle: (() -> Unit)? = null,
    isListening: Boolean = false,
    onMicClick: (() -> Unit)? = null,
    listeningPartialText: String = "",
    onNewChatClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val hasText = text.isNotBlank()
    val haptics = rememberHapticFeedback()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var previousText by remember { mutableStateOf(text) }

    LaunchedEffect(text) {
        if (previousText.isEmpty() && text.isNotEmpty()) {
            haptics.light()
        }
        previousText = text
    }

    // Animated border color: orange on focus, subtle otherwise
    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaColors.BorderSubtle
            isFocused -> MaColors.Orange.copy(alpha = 0.6f)
            else -> MaColors.BorderLight
        },
        animationSpec = tween(durationMillis = 250),
        label = "borderColor"
    )

    // Send button scale: bouncy entrance when text appears
    val sendButtonScale by animateFloatAsState(
        targetValue = if (hasText && enabled) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "sendButtonScale"
    )

    val pillShape = RoundedCornerShape(MaRadius.xl)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = MaSpacing.md, end = MaSpacing.md, bottom = MaSpacing.sm)
    ) {
        // Model chip row
        if (currentModel != null && onModelSwitch != null) {
            ModelChip(
                currentModel = currentModel,
                onModelSwitch = onModelSwitch,
                enabled = enabled,
                modifier = Modifier.padding(
                    start = MaSpacing.xs,
                    bottom = MaSpacing.xs
                )
            )
        }

        // Glassmorphic pill input
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(pillShape)
                .background(
                    color = MaColors.BgElevated,
                    shape = pillShape
                )
                .border(
                    width = if (isFocused) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = pillShape
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // New chat — pencil/edit icon, shown when there are messages
                if (onNewChatClick != null) {
                    androidx.compose.material3.IconButton(
                        onClick = onNewChatClick,
                        modifier = Modifier
                            .padding(start = MaSpacing.xs, bottom = MaSpacing.xs)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EditNote,
                            contentDescription = "New chat",
                            tint = MaColors.textMuted(),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Voice toggle — small speaker icon
                if (onAutoVoiceToggle != null) {
                    val voiceIconAlpha by animateFloatAsState(
                        targetValue = if (autoVoiceReply) 1f else 0.4f,
                        animationSpec = tween(200),
                        label = "voiceIconAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = MaSpacing.xs, bottom = MaSpacing.xs)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (autoVoiceReply) MaColors.Orange.copy(alpha = 0.15f)
                                else MaColors.BorderSubtle.copy(alpha = 0.1f),
                                CircleShape
                            )
                            .clickable(
                                onClick = onAutoVoiceToggle,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        VoiceToggleIcon(
                            active = autoVoiceReply,
                            color = if (autoVoiceReply) MaColors.Orange else MaColors.textMuted(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Text field — takes remaining space
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .testTag("input_field")
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 160.dp)
                        .padding(
                            start = MaSpacing.base,
                            top = MaSpacing.md,
                            bottom = MaSpacing.md,
                            end = MaSpacing.xs
                        )
                        .focusRequester(focusRequester)
                        .verticalScroll(rememberScrollState()),
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
                        onSend = {
                            if (hasText && enabled) {
                                haptics.strong()
                                onSend()
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }
                        }
                    ),
                    interactionSource = interactionSource,
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.isEmpty()) {
                                Text(
                                    text = if (isListening) {
                                        listeningPartialText.ifBlank { "Listening..." }
                                    } else {
                                        "Chat with M1K3..."
                                    },
                                    style = MaTypography.bodyLarge,
                                    color = if (isListening) MaColors.Orange.copy(alpha = 0.7f) else MaColors.textMuted()
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Mic button — shows when no text and mic available
                if (!hasText && onMicClick != null && sendButtonScale < 0.01f) {
                    // Pulsing animation when listening
                    val micPulse by animateFloatAsState(
                        targetValue = if (isListening) 1.15f else 1f,
                        animationSpec = if (isListening) {
                            infiniteRepeatable(
                                animation = tween(600, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        } else {
                            tween(200)
                        },
                        label = "micPulse"
                    )

                    Box(
                        modifier = Modifier
                            .testTag("mic_button")
                            .padding(end = MaSpacing.xs, bottom = MaSpacing.xs)
                            .size(36.dp)
                            .scale(micPulse)
                            .clip(CircleShape)
                            .background(
                                if (isListening) MaColors.Orange else MaColors.Orange.copy(alpha = 0.15f),
                                CircleShape
                            )
                            .clickable(
                                enabled = enabled,
                                onClick = {
                                    haptics.medium()
                                    onMicClick()
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        MicIcon(
                            color = if (isListening) MaColors.White else MaColors.Orange
                        )
                    }
                }

                // Send button — circular, aligned to bottom-right
                if (sendButtonScale > 0.01f) {
                    Box(
                        modifier = Modifier
                            .testTag("send_button")
                            .padding(end = MaSpacing.xs, bottom = MaSpacing.xs)
                            .size(36.dp)
                            .scale(sendButtonScale)
                            .clip(CircleShape)
                            .background(MaColors.Orange, CircleShape)
                            .clickable(
                                enabled = hasText && enabled,
                                onClick = {
                                    haptics.strong()
                                    onSend()
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        SendArrowIcon(color = MaColors.White)
                    }
                }
            }
        }
    }
}

/**
 * Custom send arrow icon drawn with Canvas.
 */
@Composable
private fun SendArrowIcon(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier.size(18.dp)
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.5f
        val centerX = size.width / 2
        val centerY = size.height / 2
        val arrowLength = size.height * 0.5f

        drawLine(
            color = color,
            start = Offset(centerX, centerY + arrowLength / 2),
            end = Offset(centerX, centerY - arrowLength / 2),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(centerX, centerY - arrowLength / 2),
            end = Offset(centerX - arrowLength / 3, centerY - arrowLength / 2 + arrowLength / 3),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(centerX, centerY - arrowLength / 2),
            end = Offset(centerX + arrowLength / 3, centerY - arrowLength / 2 + arrowLength / 3),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Microphone icon drawn with Canvas.
 */
@Composable
private fun MicIcon(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier.size(18.dp)
) {
    Canvas(modifier = modifier) {
        val sw = 2.5f
        val cx = size.width / 2
        val cy = size.height / 2

        // Mic body (rectangle with rounded top)
        val micWidth = size.width * 0.25f
        val micHeight = size.height * 0.4f
        val micTop = cy - micHeight / 2 - size.height * 0.05f

        drawLine(color, Offset(cx - micWidth / 2, micTop + micHeight), Offset(cx - micWidth / 2, micTop + micWidth / 2), sw, StrokeCap.Round)
        drawLine(color, Offset(cx + micWidth / 2, micTop + micHeight), Offset(cx + micWidth / 2, micTop + micWidth / 2), sw, StrokeCap.Round)
        // Top arc (approximated)
        drawLine(color, Offset(cx - micWidth / 2, micTop + micWidth / 2), Offset(cx, micTop), sw, StrokeCap.Round)
        drawLine(color, Offset(cx + micWidth / 2, micTop + micWidth / 2), Offset(cx, micTop), sw, StrokeCap.Round)

        // Base cup
        val cupY = micTop + micHeight + size.height * 0.03f
        drawLine(color, Offset(cx - micWidth, cupY), Offset(cx - micWidth, cupY - size.height * 0.08f), sw, StrokeCap.Round)
        drawLine(color, Offset(cx + micWidth, cupY), Offset(cx + micWidth, cupY - size.height * 0.08f), sw, StrokeCap.Round)
        drawLine(color, Offset(cx - micWidth, cupY), Offset(cx + micWidth, cupY), sw, StrokeCap.Round)

        // Stand
        val standBottom = size.height * 0.85f
        drawLine(color, Offset(cx, cupY), Offset(cx, standBottom), sw, StrokeCap.Round)
        drawLine(color, Offset(cx - micWidth * 0.8f, standBottom), Offset(cx + micWidth * 0.8f, standBottom), sw, StrokeCap.Round)
    }
}

/**
 * Voice toggle icon — speaker with optional sound waves.
 *
 * When active: speaker with radiating waves (auto-reply ON)
 * When inactive: speaker with X (auto-reply OFF)
 */
@Composable
private fun VoiceToggleIcon(
    active: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier.size(18.dp)
) {
    Canvas(modifier = modifier) {
        val sw = 2f
        val cx = size.width * 0.35f
        val cy = size.height / 2

        // Speaker body (small rectangle)
        drawRect(
            color = color,
            topLeft = Offset(size.width * 0.15f, cy - size.height * 0.15f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.15f, size.height * 0.3f)
        )

        // Speaker cone (triangle via lines)
        drawLine(color, Offset(cx, cy - size.height * 0.15f), Offset(cx + size.width * 0.15f, cy - size.height * 0.3f), sw, StrokeCap.Round)
        drawLine(color, Offset(cx, cy + size.height * 0.15f), Offset(cx + size.width * 0.15f, cy + size.height * 0.3f), sw, StrokeCap.Round)
        drawLine(color, Offset(cx + size.width * 0.15f, cy - size.height * 0.3f), Offset(cx + size.width * 0.15f, cy + size.height * 0.3f), sw, StrokeCap.Round)

        if (active) {
            // Sound waves (arcs approximated as small curves)
            val waveX = size.width * 0.65f
            drawLine(color, Offset(waveX, cy - size.height * 0.15f), Offset(waveX + size.width * 0.05f, cy), sw, StrokeCap.Round)
            drawLine(color, Offset(waveX + size.width * 0.05f, cy), Offset(waveX, cy + size.height * 0.15f), sw, StrokeCap.Round)

            val wave2X = size.width * 0.75f
            drawLine(color, Offset(wave2X, cy - size.height * 0.25f), Offset(wave2X + size.width * 0.08f, cy), sw, StrokeCap.Round)
            drawLine(color, Offset(wave2X + size.width * 0.08f, cy), Offset(wave2X, cy + size.height * 0.25f), sw, StrokeCap.Round)
        } else {
            // X mark (muted)
            val xCenter = size.width * 0.75f
            val xSize = size.height * 0.15f
            drawLine(color, Offset(xCenter - xSize, cy - xSize), Offset(xCenter + xSize, cy + xSize), sw, StrokeCap.Round)
            drawLine(color, Offset(xCenter + xSize, cy - xSize), Offset(xCenter - xSize, cy + xSize), sw, StrokeCap.Round)
        }
    }
}

/**
 * Model chip for switching between LLM models.
 */
@Composable
private fun ModelChip(
    currentModel: LlmModel,
    onModelSwitch: (LlmModel) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberHapticFeedback()

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(MaRadius.sm))
                .background(MaColors.Orange.copy(alpha = if (enabled) 0.15f else 0.08f))
                .clickable(enabled = enabled) {
                    haptics.light()
                    expanded = true
                }
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
                        Column {
                            Text(
                                text = model.displayName,
                                style = MaTypography.bodyMedium,
                                color = if (model == currentModel) MaColors.Orange else MaColors.textPrimary()
                            )
                            if (model.minRamGB > 0) {
                                Text(
                                    text = "${model.minRamGB}GB+ RAM required",
                                    style = MaTypography.labelSmall,
                                    color = MaColors.textMuted()
                                )
                            }
                        }
                    },
                    onClick = {
                        haptics.medium()
                        expanded = false
                        if (model != currentModel) onModelSwitch(model)
                    }
                )
            }
        }
    }
}

/**
 * Container for ChatInputBar with gradient fade above.
 *
 * Creates a smooth transition from chat content to the input area.
 */
@Composable
fun ChatInputBarContainer(
    inputBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Gradient fade from transparent to background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaColors.bgPrimary().copy(alpha = 0f),
                            MaColors.bgPrimary()
                        )
                    )
                )
        )

        // Input bar on solid background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaColors.bgPrimary())
        ) {
            inputBar()
        }
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
