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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
                                    text = "Chat with M1K3...",
                                    style = MaTypography.bodyLarge,
                                    color = MaColors.textMuted()
                                )
                            }
                            innerTextField()
                        }
                    }
                )

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

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(MaRadius.sm))
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
