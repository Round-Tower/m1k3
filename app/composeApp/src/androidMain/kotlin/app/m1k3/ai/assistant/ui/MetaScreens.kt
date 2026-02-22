package app.m1k3.ai.assistant.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography

/**
 * About M1K3 screen - App mission, privacy-first messaging, version info
 */
@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaSpacing.base),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(MaSpacing.lg))

        Text(
            "M1K3",
            style = MaTypography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaColors.Orange
        )

        Text(
            "Call me Mike",
            style = MaTypography.bodyLarge,
            color = MaColors.textSecondary()
        )

        Spacer(modifier = Modifier.height(MaSpacing.xl))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaColors.BgSecondary
            )
        ) {
            Column(modifier = Modifier.padding(MaSpacing.base)) {
                Text(
                    "Privacy-First AI Assistant",
                    style = MaTypography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(MaSpacing.sm))

                Text(
                    "M1K3 is a 100% local, on-device AI assistant. Zero network permission. " +
                    "Your conversations never leave your device.",
                    style = MaTypography.bodyMedium,
                    color = MaColors.textSecondary()
                )

                Spacer(modifier = Modifier.height(MaSpacing.base))

                MetaInfoRow(icon = Icons.Default.Security, label = "Zero Network", value = "100% Local")
                MetaInfoRow(icon = Icons.Default.Eco, label = "Eco Impact", value = "Tracked")
                MetaInfoRow(icon = Icons.Default.Smartphone, label = "On-Device AI", value = "SmolLM2-360M")
                MetaInfoRow(icon = Icons.Default.Code, label = "Version", value = "1.0.0")
            }
        }

        Spacer(modifier = Modifier.height(MaSpacing.base))

        Text(
            "Built with ❤️ by developers who care about privacy",
            style = MaTypography.bodySmall,
            color = MaColors.textSecondary()
        )
    }
}

/**
 * Help & Documentation screen
 */
@Composable
fun HelpScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaSpacing.base)
    ) {
        Text(
            "Help & Documentation",
            style = MaTypography.displayMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        HelpSection(
            title = "Getting Started",
            items = listOf(
                "Chat with M1K3 using natural language",
                "Your conversations are 100% private and local",
                "No internet connection required"
            )
        )

        HelpSection(
            title = "Eco Stats",
            items = listOf(
                "Track water, energy, and CO2 saved vs cloud AI",
                "Every message contributes to environmental savings",
                "View detailed stats in the Eco Stats screen"
            )
        )

        HelpSection(
            title = "Privacy",
            items = listOf(
                "M1K3 has ZERO network permission",
                "All AI processing happens on your device",
                "Your data never leaves your phone"
            )
        )
    }
}

/**
 * Send Feedback screen
 */
@Composable
fun FeedbackScreen() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaSpacing.base),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Feedback,
            contentDescription = "Feedback",
            modifier = Modifier.size(64.dp),
            tint = MaColors.Orange
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        Text(
            "Send Feedback",
            style = MaTypography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(MaSpacing.sm))

        Text(
            "Found a bug? Have a feature request?\nWe'd love to hear from you!",
            style = MaTypography.bodyMedium,
            color = MaColors.textSecondary()
        )

        Spacer(modifier = Modifier.height(MaSpacing.xl))

        Button(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/anthropics/m1k3/issues"))
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaColors.Orange
            )
        ) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open GitHub Issues")
        }
    }
}

/**
 * Privacy Policy screen
 */
@Composable
fun PrivacyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaSpacing.base)
    ) {
        Text(
            "Privacy Policy",
            style = MaTypography.displayMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaColors.Success.copy(alpha = 0.1f)
            )
        ) {
            Column(modifier = Modifier.padding(MaSpacing.base)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaColors.Success
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Zero Network Permission",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaColors.Success
                    )
                }

                Spacer(modifier = Modifier.height(MaSpacing.sm))

                Text(
                    "M1K3 has ZERO network permission in its AndroidManifest.xml. " +
                    "It is technically impossible for this app to send your data anywhere.",
                    style = MaTypography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.height(MaSpacing.base))

        PrivacySection(
            title = "What We Don't Collect",
            content = "We don't collect ANY data. Not your conversations, not your usage patterns, " +
                    "not your device information. Nothing. All AI processing happens locally on your device."
        )

        PrivacySection(
            title = "Data Storage",
            content = "Your conversations are stored locally on your device in an encrypted SQLite database. " +
                    "Only you have access to this data."
        )

        PrivacySection(
            title = "Third-Party Services",
            content = "M1K3 uses ZERO third-party services. No analytics, no crash reporting, no telemetry. " +
                    "The app is completely self-contained."
        )
    }
}

/**
 * Export Data screen
 */
@Composable
fun ExportScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MaSpacing.base),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = "Export",
            modifier = Modifier.size(64.dp),
            tint = MaColors.Orange
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        Text(
            "Export Your Data",
            style = MaTypography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(MaSpacing.sm))

        Text(
            "Export your conversations and eco stats",
            style = MaTypography.bodyMedium,
            color = MaColors.textSecondary()
        )

        Spacer(modifier = Modifier.height(MaSpacing.xl))

        OutlinedButton(
            onClick = { /* TODO: Implement export */ }
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Conversations (JSON)")
        }

        Spacer(modifier = Modifier.height(MaSpacing.sm))

        OutlinedButton(
            onClick = { /* TODO: Implement export */ }
        ) {
            Icon(Icons.Default.Eco, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Eco Stats (CSV)")
        }

        Spacer(modifier = Modifier.height(MaSpacing.xl))

        Text(
            "Your data is always yours. Export it anytime.",
            style = MaTypography.bodySmall,
            color = MaColors.textSecondary()
        )
    }
}

// Helper composables

@Composable
private fun MetaInfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaColors.Orange
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                label,
                style = MaTypography.bodyMedium,
                color = MaColors.textSecondary()
            )
        }
        Text(
            value,
            style = MaTypography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun HelpSection(title: String, items: List<String>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaTypography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(MaSpacing.sm))

        items.forEach { item ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "•",
                    style = MaTypography.bodyMedium,
                    color = MaColors.Orange,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    item,
                    style = MaTypography.bodyMedium,
                    color = MaColors.textSecondary()
                )
            }
        }

        Spacer(modifier = Modifier.height(MaSpacing.base))
    }
}

@Composable
private fun PrivacySection(title: String, content: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaTypography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(MaSpacing.sm))

        Text(
            content,
            style = MaTypography.bodyMedium,
            color = MaColors.textSecondary()
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))
    }
}
