package app.m1k3.ai.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.avatar.AvatarEmotion

/**
 * 間 AI - Avatar & Statistics Screen
 *
 * Combined interface for avatar controls and app statistics.
 *
 * **Features:**
 * - Avatar emotion control (8 emotions)
 * - 3D avatar preview (when available)
 * - App usage statistics
 * - Performance metrics
 * - Debug controls
 *
 * **Philosophy:**
 * Visual feedback and transparency through avatar expressions
 * and detailed statistics about local AI performance.
 */
@Composable
fun AvatarStatsScreen(
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedTab = 0
                },
                text = { Text("Avatar") },
                icon = { Icon(Icons.Default.Face, contentDescription = null) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    selectedTab = 1
                },
                text = { Text("Stats") },
                icon = { Icon(Icons.Default.Analytics, contentDescription = null) }
            )
        }

        // Content
        when (selectedTab) {
            0 -> AvatarTab()
            1 -> StatsTab()
        }
    }
}

/**
 * Avatar control tab
 */
@Composable
private fun AvatarTab() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Avatar Preview Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Face,
                            contentDescription = "Avatar",
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "3D Avatar & Emotion Controls",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Coming Soon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Avatar Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Avatar System",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    InfoRow(label = "Status", value = "Phase 3")
                    InfoRow(label = "Emotions", value = "8 types")
                    InfoRow(label = "Rendering", value = "3D Canvas")
                }
            }
        }
    }
}

/**
 * Statistics tab
 */
@Composable
private fun StatsTab() {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Usage Statistics
        item {
            Text(
                text = "Usage Statistics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatRow(
                        icon = Icons.Default.Chat,
                        label = "Total Messages",
                        value = "0", // TODO: Get from database
                        color = MaterialTheme.colorScheme.primary
                    )

                    StatRow(
                        icon = Icons.Default.History,
                        label = "Conversations",
                        value = "0", // TODO: Get from database
                        color = MaterialTheme.colorScheme.secondary
                    )

                    StatRow(
                        icon = Icons.Default.Token,
                        label = "Tokens Processed",
                        value = "0", // TODO: Get from database
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Performance Metrics
        item {
            Text(
                text = "Performance",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatRow(
                        icon = Icons.Default.Speed,
                        label = "Avg. Speed",
                        value = "0 tok/s", // TODO: Calculate from metrics
                        color = MaterialTheme.colorScheme.primary
                    )

                    StatRow(
                        icon = Icons.Default.Memory,
                        label = "Memory Usage",
                        value = "~180MB", // Model size
                        color = MaterialTheme.colorScheme.secondary
                    )

                    StatRow(
                        icon = Icons.Default.Eco,
                        label = "CO₂ Saved",
                        value = "0g", // TODO: Get from eco metrics
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Model Information
        item {
            Text(
                text = "AI Model",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoRow(label = "Model", value = "SmolLM2-360M")
                    InfoRow(label = "Parameters", value = "360 million")
                    InfoRow(label = "Quantization", value = "4-bit")
                    InfoRow(label = "Context Window", value = "24,576 tokens")
                    InfoRow(label = "Size", value = "180MB")
                }
            }
        }
    }
}

/**
 * Statistic row with icon, label, and value
 */
@Composable
private fun StatRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Info row with label and value
 */
@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Preview for Avatar Stats Screen
 */
@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AvatarStatsScreenPreview() {
    MaterialTheme {
        AvatarStatsScreen()
    }
}
