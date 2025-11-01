package app.m1k3.ai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.m1k3.ai.assistant.database.AndroidDatabaseFactory
import app.m1k3.ai.assistant.database.DatabaseConfig
import kotlinx.coroutines.launch

/**
 * 間 AI - MainActivity
 *
 * Minimalist demo showcasing:
 * - Privacy-first architecture (zero network)
 * - Encrypted database foundation
 * - Beautiful Material 3 design
 * - "Negative space" philosophy
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MaAIDemo()
                }
            }
        }
    }
}

@Composable
fun MaAITheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF00BCD4),
            secondary = androidx.compose.ui.graphics.Color(0xFF03DAC5),
            background = androidx.compose.ui.graphics.Color(0xFF121212),
            surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaAIDemo() {
    var systemStatus by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            systemStatus = getSystemStatus()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "間 AI",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Privacy-First Mobile Assistant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "🎉 Phase 0 Foundation Complete",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "7/15 tickets implemented (46.7%)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // System Status Section
            item {
                Text(
                    "System Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            items(systemStatus) { status ->
                StatusCard(status)
            }

            // Architecture Section
            item {
                Text(
                    "Architecture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                ArchitectureCard()
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "💡 100% Local • Zero Network • Privacy-First",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun StatusCard(status: StatusItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (status.isSuccess) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    status.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    status.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Text(
                status.icon,
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Composable
fun ArchitectureCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ArchitectureLayer("Kotlin Multiplatform 2.2.20", "Cross-platform foundation")
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ArchitectureLayer("Compose Multiplatform 1.9.1", "Modern UI framework")
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ArchitectureLayer("SQLDelight 2.0.2", "Type-safe database")
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ArchitectureLayer("ONNX Runtime 1.17.0", "Local AI inference")
            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ArchitectureLayer("CameraX + ML Kit", "Multi-modal vision")
        }
    }
}

@Composable
fun ArchitectureLayer(name: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

data class StatusItem(
    val name: String,
    val description: String,
    val icon: String,
    val isSuccess: Boolean
)

fun getSystemStatus(): List<StatusItem> {
    return listOf(
        StatusItem(
            name = "Privacy Protection",
            description = "Zero network permission • 100% local",
            icon = "🔒",
            isSuccess = true
        ),
        StatusItem(
            name = "Database Foundation",
            description = "SQLDelight with encryption ready",
            icon = "🗄️",
            isSuccess = true
        ),
        StatusItem(
            name = "Knowledge Base",
            description = "1,341 documents • 20 categories",
            icon = "📚",
            isSuccess = true
        ),
        StatusItem(
            name = "Package Name",
            description = "app.m1k3.ai.assistant (ASO optimized)",
            icon = "📦",
            isSuccess = true
        ),
        StatusItem(
            name = "AI Engine",
            description = "Coming in Phase 1 (SmolLM2-360M)",
            icon = "🤖",
            isSuccess = false
        ),
        StatusItem(
            name = "Multi-Modal Vision",
            description = "Coming in Phase 4 (CameraX + ML Kit)",
            icon = "📸",
            isSuccess = false
        )
    )
}
