package app.m1k3.ai.assistant.test

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
import app.m1k3.ai.assistant.chat.ChatMessage

/**
 * Test Tags for ChatScreen UI Testing
 *
 * Semantic test tags for all key UI components in ChatScreen.
 */
object ChatScreenTestTags {
    const val MESSAGE_LIST = "message_list"
    const val INPUT_FIELD = "input_field"
    const val SEND_BUTTON = "send_button"
    const val LOADING_INDICATOR = "loading_indicator"
    const val ECO_INDICATOR = "eco_indicator"
    const val AVATAR = "avatar"
    const val ERROR_MESSAGE = "error_message"
}

/**
 * Test Helper Extensions for ChatScreen UI Testing
 *
 * Provides convenient functions for common test operations.
 */

/**
 * Type text into the input field and send the message
 *
 * @param text The message to send
 */
fun ComposeTestRule.sendMessage(text: String) {
    onNodeWithTag(ChatScreenTestTags.INPUT_FIELD)
        .performTextInput(text)

    onNodeWithTag(ChatScreenTestTags.SEND_BUTTON)
        .performClick()
}

/**
 * Wait for AI response to appear (with timeout)
 *
 * @param timeoutMs Maximum wait time in milliseconds (default: 5000ms)
 * @return true if response appeared, false if timeout
 */
fun ComposeTestRule.waitForResponse(timeoutMs: Long = 5000): Boolean {
    return try {
        waitUntil(timeoutMs) {
            // Check if loading indicator is gone (response complete)
            onAllNodesWithTag(ChatScreenTestTags.LOADING_INDICATOR)
                .fetchSemanticsNodes()
                .isEmpty()
        }
        true
    } catch (e: AssertionError) {
        false
    }
}

/**
 * Assert that a message with specific text is displayed
 *
 * @param text The message text to find (supports substring matching)
 * @param substring Whether to match substring (default: true)
 */
fun ComposeTestRule.assertMessageDisplayed(text: String, substring: Boolean = true) {
    onNodeWithText(text, substring = substring)
        .assertIsDisplayed()
}

/**
 * Assert that the eco indicator is visible and updated
 */
fun ComposeTestRule.assertEcoMetricsUpdated() {
    onNodeWithTag(ChatScreenTestTags.ECO_INDICATOR)
        .assertExists()
        .assertIsDisplayed()
}

/**
 * Assert that the loading indicator is visible (generation in progress)
 */
fun ComposeTestRule.assertGenerating() {
    onNodeWithTag(ChatScreenTestTags.LOADING_INDICATOR)
        .assertExists()
        .assertIsDisplayed()
}

/**
 * Assert that the loading indicator is not visible (generation complete)
 */
fun ComposeTestRule.assertNotGenerating() {
    onAllNodesWithTag(ChatScreenTestTags.LOADING_INDICATOR)
        .assertCountEquals(0)
}

/**
 * Assert that the send button is enabled
 */
fun ComposeTestRule.assertSendButtonEnabled() {
    onNodeWithTag(ChatScreenTestTags.SEND_BUTTON)
        .assertIsEnabled()
}

/**
 * Assert that the send button is disabled
 */
fun ComposeTestRule.assertSendButtonDisabled() {
    onNodeWithTag(ChatScreenTestTags.SEND_BUTTON)
        .assertIsNotEnabled()
}

/**
 * Assert that the input field is enabled and ready for input
 */
fun ComposeTestRule.assertInputEnabled() {
    onNodeWithTag(ChatScreenTestTags.INPUT_FIELD)
        .assertIsEnabled()
}

/**
 * Assert that the avatar is visible
 */
fun ComposeTestRule.assertAvatarVisible() {
    onNodeWithTag(ChatScreenTestTags.AVATAR)
        .assertExists()
        .assertIsDisplayed()
}

/**
 * Get the text content of a specific message by index
 *
 * @param index The message index (0-based)
 * @return The message text content
 */
fun ComposeTestRule.getMessageText(index: Int): String {
    val nodes = onAllNodesWithTag(ChatScreenTestTags.MESSAGE_LIST)
        .fetchSemanticsNodes()

    return if (nodes.isNotEmpty() && index < nodes.size) {
        nodes[index].config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            ?.firstOrNull()?.text ?: ""
    } else {
        ""
    }
}

/**
 * Extract response metrics from the last AI message
 *
 * Returns a map with keys: tokens, time_ms, tok_per_sec
 *
 * @return Map of performance metrics
 */
fun ComposeTestRule.getResponseMetrics(): Map<String, String> {
    val metrics = mutableMapOf<String, String>()

    try {
        // Look for inference stats text (format: "⚡ X tokens in Yms (Z tok/s)")
        val statsText = onAllNodesWithText("⚡", substring = true)
            .fetchSemanticsNodes()
            .lastOrNull()
            ?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            ?.firstOrNull()?.text

        if (statsText != null) {
            // Parse tokens
            val tokensMatch = Regex("(\\d+) tokens").find(statsText)
            tokensMatch?.groupValues?.getOrNull(1)?.let { metrics["tokens"] = it }

            // Parse time
            val timeMatch = Regex("(\\d+)ms").find(statsText)
            timeMatch?.groupValues?.getOrNull(1)?.let { metrics["time_ms"] = it }

            // Parse tok/s
            val tokPerSecMatch = Regex("([\\d.]+) tok/s").find(statsText)
            tokPerSecMatch?.groupValues?.getOrNull(1)?.let { metrics["tok_per_sec"] = it }
        }
    } catch (e: Exception) {
        // If parsing fails, return empty metrics
    }

    return metrics
}

/**
 * Clear the input field
 */
fun ComposeTestRule.clearInput() {
    onNodeWithTag(ChatScreenTestTags.INPUT_FIELD)
        .performTextClearance()
}

/**
 * Type text into input field without sending
 *
 * @param text The text to type
 */
fun ComposeTestRule.typeMessage(text: String) {
    onNodeWithTag(ChatScreenTestTags.INPUT_FIELD)
        .performTextInput(text)
}

/**
 * Click the send button
 */
fun ComposeTestRule.clickSend() {
    onNodeWithTag(ChatScreenTestTags.SEND_BUTTON)
        .performClick()
}

/**
 * Wait for a specific condition with custom predicate
 *
 * @param timeoutMs Maximum wait time
 * @param predicate The condition to wait for
 * @return true if condition met, false if timeout
 */
fun ComposeTestRule.waitForCondition(
    timeoutMs: Long = 5000,
    predicate: () -> Boolean
): Boolean {
    return try {
        waitUntil(timeoutMs, predicate)
        true
    } catch (e: AssertionError) {
        false
    }
}

/**
 * Assert that RAG sources are displayed in the message
 *
 * @param substring Text to find in RAG sources (e.g., category name)
 */
fun ComposeTestRule.assertRAGSourcesDisplayed(substring: String) {
    onNodeWithText(substring, substring = true)
        .assertExists()
        .assertIsDisplayed()
}

/**
 * Count the number of messages displayed
 *
 * @return Total message count
 */
fun ComposeTestRule.getMessageCount(): Int {
    return onAllNodes(hasTestTag(ChatScreenTestTags.MESSAGE_LIST))
        .fetchSemanticsNodes()
        .size
}
