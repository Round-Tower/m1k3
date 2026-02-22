package app.m1k3.ai.assistant.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.chat.SessionEcoStats
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * ClearConversationDialog - Confirmation dialog for clearing chat history.
 *
 * Displays:
 * - Message count
 * - Session eco stats (if messageCount > 0)
 * - Destructive "Clear" button with haptic feedback
 * - "Cancel" button
 *
 * @param sessionStats Current session statistics
 * @param onConfirm Callback when user confirms clearing
 * @param onDismiss Callback when user cancels
 */
@Composable
fun ClearConversationDialog(
    sessionStats: SessionEcoStats,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberHapticFeedback()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Conversation?") },
        text = {
            Column {
                Text("This will permanently delete ${sessionStats.messageCount} messages.")

                if (sessionStats.messageCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Session stats will be reset:\n" +
                        "• ${sessionStats.totalTokens} tokens\n" +
                        "• ${formatWater(sessionStats.waterMl)} water saved\n" +
                        "• ${formatEnergy(sessionStats.energyWh)} energy saved",
                        style = MaTypography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptics.strong()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaColors.Error
                )
            ) {
                Text("Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Format water usage in human-readable format.
 */
private fun formatWater(waterMl: Long): String {
    return if (waterMl >= 1000) {
        String.format("%.2f L", waterMl.toDouble() / 1000)
    } else {
        String.format("%d ml", waterMl)
    }
}

/**
 * Format energy usage in human-readable format.
 */
private fun formatEnergy(energyWh: Long): String {
    return if (energyWh >= 1000) {
        String.format("%.2f kWh", energyWh.toDouble() / 1000)
    } else {
        String.format("%d Wh", energyWh)
    }
}
