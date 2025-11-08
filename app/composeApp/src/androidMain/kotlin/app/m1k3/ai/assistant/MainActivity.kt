package app.m1k3.ai.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.m1k3.ai.assistant.navigation.BottomNavigationBar
import app.m1k3.ai.assistant.navigation.Screen
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.ai.LlamaCppEngine
import app.m1k3.ai.assistant.database.AndroidDatabaseFactory
import app.m1k3.ai.assistant.database.DatabaseConfig
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.components.MaButtonPrimary
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.avatar.*
import app.m1k3.ai.assistant.avatar.webview.AvatarWebViewScreen
import app.m1k3.ai.assistant.knowledge.KnowledgeBaseImporter
import app.m1k3.ai.assistant.ui.ChatScreen
import app.m1k3.ai.assistant.ui.AvatarDebugScreen
import app.m1k3.ai.assistant.ui.HistoryScreen
import app.m1k3.ai.assistant.ui.EcoStatsScreen
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * M1K3 AI - MainActivity
 *
 * Minimalist demo showcasing:
 * - Privacy-first architecture (zero network)
 * - Encrypted database foundation
 * - Beautiful Material 3 design
 * - "Negative space" philosophy
 */
class MainActivity : ComponentActivity() {
    private lateinit var aiEngine: BaseLlmEngine
    private var driver: app.cash.sqldelight.db.SqlDriver? = null
    private var database: MaDatabase? = null
    private var knowledgeImportStatus by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge for immersive full-screen experience
        enableEdgeToEdge()

        // Configure system bars for AMOLED black theme
        WindowCompat.setDecorFitsSystemWindows(window, false)

        aiEngine = LlamaCppEngine(this)

        // Import knowledge base on first startup
        lifecycleScope.launch {
            try {
                knowledgeImportStatus = "Loading knowledge..."

                // Initialize database
                val databaseFactory = AndroidDatabaseFactory(this@MainActivity)
                val passphrase = databaseFactory.getDatabasePassphrase()
                driver = databaseFactory.createDriver(passphrase)
                database = MaDatabase(driver!!)
                val importer = KnowledgeBaseImporter(database!!)

                // Check if knowledge already imported
                val existingCount = database!!.triviaFactQueries.getTotalFactCount().executeAsOne()

                // TEMPORARY: Force re-import to load consolidated KB (1,391 docs) + M1K3 system KB (10 docs)
                val forceReimport = existingCount > 0 && existingCount < 1400

                if (forceReimport) {
                    println("🔄 [M1K3] Force re-importing knowledge base (current: $existingCount docs, expected: 1,401)")
                    database!!.triviaFactQueries.deleteAllFacts()
                }

                if (existingCount == 0L || forceReimport) {
                    println("📚 [M1K3] Importing knowledge bases (1,401 documents from 2 sources)...")

                    // 1. Load comprehensive knowledge base (1,391 docs)
                    val comprehensiveJson = assets.open("composeResources/myapplication.composeapp.generated.resources/files/comprehensive_knowledge_base.json").use { input ->
                        BufferedReader(InputStreamReader(input)).use { reader ->
                            reader.readText()
                        }
                    }

                    val comprehensiveResult = importer.importKnowledgeBase(comprehensiveJson)
                    println("📚 Comprehensive KB: ${comprehensiveResult.imported} documents imported")

                    // 2. Load M1K3 system knowledge base (10 docs)
                    val systemJson = assets.open("composeResources/myapplication.composeapp.generated.resources/files/m1k3_system_knowledge.json").use { input ->
                        BufferedReader(InputStreamReader(input)).use { reader ->
                            reader.readText()
                        }
                    }

                    val systemResult = importer.importKnowledgeBase(systemJson)
                    println("🤖 M1K3 System KB: ${systemResult.imported} documents imported")

                    // Verify combined import
                    val verification = importer.verifyImport()
                    println(verification.toString())

                    val totalImported = comprehensiveResult.imported + systemResult.imported
                    knowledgeImportStatus = "✅ Knowledge ready: $totalImported documents (${comprehensiveResult.imported} comprehensive + ${systemResult.imported} system)"
                } else {
                    println("📚 [M1K3] Knowledge base already loaded ($existingCount documents)")
                    knowledgeImportStatus = "✅ Knowledge ready: $existingCount documents"
                }

            } catch (e: Exception) {
                println("❌ [M1K3] Knowledge import failed: ${e.message}")
                e.printStackTrace()
                knowledgeImportStatus = "⚠️ Knowledge unavailable"
            }
        }

        setContent {
            ProvideSharedEngine {
                MaTheme {
                    val navController = rememberNavController()

                    Scaffold(
                        bottomBar = {
                            BottomNavigationBar(navController = navController)
                        }
                    ) { paddingValues ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Demo.route,
                            modifier = Modifier.padding(paddingValues)
                        ) {
                            // Demo Screen
                            composable(Screen.Demo.route) {
                                MaAIDemo(
                                    onChatClick = { navController.navigate(Screen.Chat.route) },
                                    onDebugClick = { navController.navigate(Screen.Avatar.route) },
                                    knowledgeStatus = knowledgeImportStatus
                                )
                            }

                            // Chat Screen
                            composable(Screen.Chat.route) {
                                if (database != null) {
                                    ChatScreen(
                                        onBackClick = { navController.navigateUp() },
                                        onDebugClick = { navController.navigate(Screen.Avatar.route) },
                                        onHistoryClick = { navController.navigate(Screen.History.route) },
                                        onEcoStatsClick = { navController.navigate(Screen.EcoStats.route) },
                                        aiEngine = aiEngine,
                                        database = database!!
                                    )
                                }
                            }

                            // History Screen
                            composable(Screen.History.route) {
                                if (database != null) {
                                    HistoryScreen(
                                        database = database!!,
                                        projectId = "default",
                                        onBackClick = { navController.navigateUp() },
                                        onConversationClick = { conversationId ->
                                            navController.navigate("conversation/$conversationId")
                                        }
                                    )
                                }
                            }

                            // Eco Stats Screen
                            composable(Screen.EcoStats.route) {
                                if (database != null) {
                                    EcoStatsScreen(
                                        database = database!!,
                                        projectId = "default",
                                        onBackClick = { navController.navigateUp() }
                                    )
                                }
                            }

                            // Avatar Screen (replaces AvatarDebugScreen)
                            composable(Screen.Avatar.route) {
                                database?.let { db ->
                                    AvatarDebugScreen(
                                        database = db,
                                        onBackClick = { navController.navigateUp() },
                                        on3DWebViewClick = { navController.navigate(Screen.Avatar3DWebView.route) }
                                    )
                                } ?: run {
                                    // Show loading state if database not initialized
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Loading database...")
                                    }
                                }
                            }

                            // 3D WebView Screen - Three.js POC
                            composable(Screen.Avatar3DWebView.route) {
                                AvatarWebViewScreen(
                                    avatarViewModel = rememberAvatarViewModel(),
                                    onBackClick = { navController.navigateUp() }
                                )
                            }

                            // Settings Screen
                            composable(Screen.Settings.route) {
                                app.m1k3.ai.assistant.ui.SettingsScreen()
                            }

                            // Conversation Detail Screen
                            composable(
                                route = Screen.ConversationDetail.route,
                                arguments = listOf(
                                    navArgument(Screen.ConversationDetail.argConversationId) {
                                        type = NavType.LongType
                                    }
                                )
                            ) { backStackEntry ->
                                val conversationId = backStackEntry.arguments?.getLong(
                                    Screen.ConversationDetail.argConversationId
                                ) ?: 0L

                                // TODO: Create ConversationDetailScreen in Phase 3
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Text(
                                            "Conversation Detail",
                                            style = MaterialTheme.typography.headlineMedium
                                        )
                                        Text("ID: $conversationId")
                                        TextButton(onClick = { navController.navigateUp() }) {
                                            Text("← Back")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        // Cleanup resources synchronously with timeout to prevent ANR
        // Note: lifecycleScope is cancelled before onDestroy(), so we use runBlocking
        try {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(5000) {  // 5 second timeout
                    try {
                        // Close database driver (lightweight operation)
                        driver?.close()

                        // Close AI engine (ONNX cleanup on IO thread)
                        aiEngine.close()
                    } catch (e: Exception) {
                        println("⚠️ Error during cleanup: ${e.message}")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            println("⚠️ Cleanup timeout - forcing shutdown")
        }
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaAIDemo(onChatClick: () -> Unit, onDebugClick: () -> Unit = {}, knowledgeStatus: String? = null) {
    var systemStatus by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    val scope = rememberCoroutineScope()

    // Avatar state
    val avatarVM = rememberAvatarViewModel()
    val avatarState by avatarVM.collectAsState()

    LaunchedEffect(knowledgeStatus) {
        scope.launch {
            systemStatus = getSystemStatus(knowledgeStatus)
            // Set avatar to happy when knowledge loads successfully
            if (knowledgeStatus?.startsWith("✅") == true) {
                avatarVM.setEmotion(AvatarEmotion.HAPPY, 0.8f)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "M1K3",
                                style = MaTypography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaColors.TextPrimary
                            )
                            Text(
                                "Privacy-First Mobile Assistant",
                                style = MaTypography.bodySmall,
                                color = MaColors.TextSecondary
                            )
                        }
                        // Mini avatar in top bar
                        MiniAvatarIndicator(
                            state = avatarState,
                            modifier = Modifier.size(48.dp),
                            onClick = {
                                // Cycle through emotions on click (for demo)
                                val emotions = listOf(
                                    AvatarEmotion.HAPPY, AvatarEmotion.EXCITED,
                                    AvatarEmotion.LOVE, AvatarEmotion.THINKING
                                )
                                val nextEmotion = emotions.random()
                                avatarVM.setEmotion(nextEmotion, 0.8f)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaColors.BgPrimary
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md)
        ) {
            // Avatar Display - 3D Colobus Monkey with third eye perspective
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(
                        state = avatarState,
                        showInfo = true,
                        use3D = false,  // DISABLED: 3D causes SIGSEGV crashes during navigation
                        onClick = {
                            avatarVM.flashEmotion(AvatarEmotion.EXCITED, 1500)
                        }
                    )
                }
            }

            // Chat Button
            item {
                MaButtonPrimary(
                    onClick = onChatClick,
                    text = "💬 Chat with M1K3 AI",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Debug Button
            item {
                MaCard(
                    onClick = onDebugClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(MaSpacing.base),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "🎨 Avatar Debug Lab",
                                style = MaTypography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaColors.TextPrimary
                            )
                            Text(
                                "Test 3D avatar • All emotions • Performance metrics",
                                style = MaTypography.bodySmall,
                                color = MaColors.TextSecondary
                            )
                        }
                        Text("→", style = MaTypography.headlineMedium, color = MaColors.Orange)
                    }
                }
            }

            // Hero Section
            item {
                MaCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(MaSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(MaSpacing.sm)
                    ) {
                        Text(
                            "🎉 Design System + Avatar Complete",
                            style = MaTypography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.Orange
                        )
                        Text(
                            "AMOLED Black • Liquid Glass • Robot Avatar • Streaming Inference",
                            style = MaTypography.bodyMedium,
                            color = MaColors.TextSecondary
                        )
                    }
                }
            }

            // System Status Section
            item {
                Text(
                    "System Status",
                    style = MaTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaColors.TextPrimary,
                    modifier = Modifier.padding(vertical = MaSpacing.sm)
                )
            }

            items(systemStatus) { status ->
                StatusCard(status)
            }

            // Architecture Section
            item {
                Text(
                    "Architecture",
                    style = MaTypography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaColors.TextPrimary,
                    modifier = Modifier.padding(vertical = MaSpacing.sm)
                )
            }

            item {
                ArchitectureCard()
            }

            // Footer
            item {
                Spacer(modifier = Modifier.height(MaSpacing.base))
                Text(
                    "💡 100% Local • Zero Network • Privacy-First",
                    style = MaTypography.bodySmall,
                    color = MaColors.TextDisabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun StatusCard(status: StatusItem) {
    MaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaSpacing.base),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    status.name,
                    style = MaTypography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (status.isSuccess) MaColors.TextPrimary else MaColors.Error
                )
                Text(
                    status.description,
                    style = MaTypography.bodySmall,
                    color = MaColors.TextSecondary
                )
            }
            Text(
                status.icon,
                style = MaTypography.headlineMedium
            )
        }
    }
}

@Composable
fun ArchitectureCard() {
    MaCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MaSpacing.base),
            verticalArrangement = Arrangement.spacedBy(MaSpacing.md)
        ) {
            ArchitectureLayer("Kotlin Multiplatform 2.2.20", "Cross-platform foundation")
            HorizontalDivider(color = MaColors.BorderSubtle)
            ArchitectureLayer("Compose Multiplatform 1.9.1", "Modern UI framework")
            HorizontalDivider(color = MaColors.BorderSubtle)
            ArchitectureLayer("SQLDelight 2.0.2", "Type-safe database")
            HorizontalDivider(color = MaColors.BorderSubtle)
            ArchitectureLayer("ONNX Runtime 1.23.1", "Local AI inference")
            HorizontalDivider(color = MaColors.BorderSubtle)
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
                style = MaTypography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaColors.TextPrimary
            )
            Text(
                description,
                style = MaTypography.bodySmall,
                color = MaColors.TextSecondary
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

fun getSystemStatus(knowledgeStatus: String? = null): List<StatusItem> {
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
            description = knowledgeStatus ?: "Loading...",
            icon = "📚",
            isSuccess = knowledgeStatus?.startsWith("✅") == true
        ),
        StatusItem(
            name = "Package Name",
            description = "app.m1k3.ai.assistant (ASO optimized)",
            icon = "📦",
            isSuccess = true
        ),
        StatusItem(
            name = "AI Engine",
            description = "SmolLM2-360M (Production Ready)",
            icon = "🤖",
            isSuccess = true
        ),
        StatusItem(
            name = "Design System",
            description = "AMOLED Black • Liquid Glass • Complete",
            icon = "🎨",
            isSuccess = true
        ),
        StatusItem(
            name = "Robot Avatar",
            description = "9 Emotions • 6 Activities • Canvas Rendering",
            icon = "🤖",
            isSuccess = true
        )
    )
}
