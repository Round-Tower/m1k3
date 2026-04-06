package app.m1k3.ai.assistant.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
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

// ─────────────────────────────────────────────────────────────
// Open Source Licenses Screen
// ─────────────────────────────────────────────────────────────

/**
 * LicensesScreen — Full attribution for all open source libraries and assets.
 *
 * Every project we ship stands on the shoulders of open source work.
 * This screen gives credit where credit is due.
 *
 * — MurphySig (https://murphysig.dev) | confidence: high | context: pure Compose, no deps
 */
@Composable
fun LicensesScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaColors.bgPrimary())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MaSpacing.base)
    ) {
        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        Spacer(modifier = Modifier.height(MaSpacing.lg))

        // Header
        Text(
            text = "Open Source",
            style = MaTypography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaColors.Orange
        )
        Spacer(modifier = Modifier.height(MaSpacing.xs))
        Text(
            text = "Built on the shoulders of giants.",
            style = MaTypography.bodyMedium,
            color = MaColors.textSecondary()
        )

        Spacer(modifier = Modifier.height(MaSpacing.xl))

        // 3D Assets
        LicenseSection(
            title = "3D Assets",
            entries = listOf(
                LicenseEntry(
                    name = "Omabuarts Quirky Series",
                    license = "CC0 1.0",
                    licenseType = LicenseType.CC0,
                    author = "omabuarts.com",
                    description = "Colobus, Sparrow, Gecko, Herring, Muskrat, Pudu, Taipan, Inkfish"
                ),
                LicenseEntry(
                    name = "Quaternius Animal/Dino/Fish Packs",
                    license = "CC0 1.0",
                    licenseType = LicenseType.CC0,
                    author = "poly.pizza/u/Quaternius",
                    description = "28 models: dinosaurs, animals, fish"
                ),
                LicenseEntry(
                    name = "Khronos glTF Sample Models",
                    license = "CC0 1.0",
                    licenseType = LicenseType.CC0,
                    author = "github.com/KhronosGroup/glTF-Sample-Models",
                    description = "Fox, CesiumMan, BrainStem"
                ),
                LicenseEntry(
                    name = "Mask (IzLoM39)",
                    license = "CC-BY 4.0",
                    licenseType = LicenseType.CCBY,
                    author = "sketchfab.com",
                    description = "Mask avatar model"
                )
            )
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        // Apache 2.0
        LicenseSection(
            title = "Apache License 2.0",
            entries = listOf(
                LicenseEntry("Kotlin & KMP", "Apache 2.0", LicenseType.APACHE, "JetBrains", version = "2.2.20"),
                LicenseEntry("Compose Multiplatform", "Apache 2.0", LicenseType.APACHE, "JetBrains", version = "1.9.2"),
                LicenseEntry("AndroidX Core", "Apache 2.0", LicenseType.APACHE, "Google", version = "1.17.0"),
                LicenseEntry("AndroidX Lifecycle", "Apache 2.0", LicenseType.APACHE, "Google", version = "2.9.5"),
                LicenseEntry("AndroidX Navigation", "Apache 2.0", LicenseType.APACHE, "Google", version = "2.9.1"),
                LicenseEntry("AndroidX WorkManager", "Apache 2.0", LicenseType.APACHE, "Google", version = "2.10.1"),
                LicenseEntry("AndroidX CameraX", "Apache 2.0", LicenseType.APACHE, "Google", version = "1.4.0"),
                LicenseEntry("AndroidX Security", "Apache 2.0", LicenseType.APACHE, "Google", version = "1.1.0-alpha06"),
                LicenseEntry("Koin", "Apache 2.0", LicenseType.APACHE, "Insert-Koin.io", version = "4.1.0"),
                LicenseEntry("SQLDelight", "Apache 2.0", LicenseType.APACHE, "CashApp", version = "2.0.2"),
                LicenseEntry("ONNX Runtime", "Apache 2.0", LicenseType.APACHE, "Microsoft", version = "1.23.2"),
                LicenseEntry("Ktor", "Apache 2.0", LicenseType.APACHE, "JetBrains", version = "3.3.1"),
                LicenseEntry("kotlinx-serialization", "Apache 2.0", LicenseType.APACHE, "JetBrains", version = "1.7.3"),
                LicenseEntry("kotlinx-coroutines", "Apache 2.0", LicenseType.APACHE, "JetBrains", version = "1.10.2"),
                LicenseEntry("kotlinx-datetime", "Apache 2.0", LicenseType.APACHE, "JetBrains", version = "0.6.2"),
                LicenseEntry("Logback Classic", "Apache 2.0", LicenseType.APACHE, "QOS.ch", version = "1.5.20"),
                LicenseEntry("ML Kit Vision", "Apache 2.0", LicenseType.APACHE, "Google", version = "17.0.2"),
                LicenseEntry("ML Kit Text Recognition", "Apache 2.0", LicenseType.APACHE, "Google", version = "19.0.0"),
                LicenseEntry("Play Services Location", "Apache 2.0", LicenseType.APACHE, "Google", version = "21.3.0"),
                LicenseEntry("Health Connect", "Apache 2.0", LicenseType.APACHE, "Google", version = "1.1.0-rc01")
            )
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        // MIT
        LicenseSection(
            title = "MIT License",
            entries = listOf(
                LicenseEntry("Kermit Logging", "MIT", LicenseType.MIT, "Touchlab", version = "2.0.4"),
                LicenseEntry("Three.js", "MIT", LicenseType.MIT, "three.js.org", description = "3D WebGL renderer for web avatar")
            )
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        // BSD
        LicenseSection(
            title = "BSD License",
            entries = listOf(
                LicenseEntry(
                    name = "SQLCipher",
                    license = "BSD",
                    licenseType = LicenseType.BSD,
                    author = "Zetetic LLC",
                    version = "4.5.4",
                    description = "Encrypted SQLite for Android"
                )
            )
        )

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// ─────────────────────────────────────────────────────────────
// License data model
// ─────────────────────────────────────────────────────────────

private enum class LicenseType { CC0, CCBY, APACHE, MIT, BSD }

private data class LicenseEntry(
    val name: String,
    val license: String,
    val licenseType: LicenseType,
    val author: String,
    val version: String? = null,
    val description: String? = null
)

// ─────────────────────────────────────────────────────────────
// License composables
// ─────────────────────────────────────────────────────────────

@Composable
private fun LicenseSection(title: String, entries: List<LicenseEntry>) {
    val sectionShape = RoundedCornerShape(MaRadius.md)

    Column(verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)) {
        // Section label — small ALL-CAPS
        Text(
            text = title.uppercase(),
            style = MaTypography.labelSmall,
            color = MaColors.Orange,
            modifier = Modifier.padding(start = MaSpacing.xs, bottom = MaSpacing.xs)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(sectionShape)
                .background(MaColors.bgElevated())
                .border(1.dp, MaColors.borderSubtle(), sectionShape)
        ) {
            entries.forEachIndexed { index, entry ->
                LicenseEntryRow(entry = entry)
                if (index < entries.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = MaSpacing.md),
                        color = MaColors.borderSubtle()
                    )
                }
            }
        }
    }
}

@Composable
private fun LicenseEntryRow(entry: LicenseEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaSpacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaTypography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaColors.textPrimary()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = buildString {
                    append(entry.author)
                    entry.version?.let { append(" · $it") }
                },
                style = MaTypography.labelSmall,
                color = MaColors.textMuted()
            )
            entry.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaTypography.labelSmall,
                    color = MaColors.textMuted()
                )
            }
        }

        Spacer(modifier = Modifier.width(MaSpacing.sm))
        LicenseBadge(entry.license, entry.licenseType)
    }
}

@Composable
private fun LicenseBadge(label: String, type: LicenseType) {
    val (bgColor, textColor) = when (type) {
        LicenseType.CC0, LicenseType.MIT -> MaColors.Success.copy(alpha = 0.12f) to MaColors.Success
        LicenseType.APACHE -> MaColors.Info.copy(alpha = 0.12f) to MaColors.Info
        LicenseType.CCBY -> MaColors.Orange.copy(alpha = 0.12f) to MaColors.Orange
        LicenseType.BSD -> MaColors.TextMuted.copy(alpha = 0.12f) to MaColors.TextMuted
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(MaRadius.sm))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaTypography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
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
