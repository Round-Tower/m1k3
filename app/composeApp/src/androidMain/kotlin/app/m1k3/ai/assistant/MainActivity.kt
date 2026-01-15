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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.m1k3.ai.assistant.navigation.SidebarMenuItem
import app.m1k3.ai.assistant.navigation.Screen
import app.m1k3.ai.assistant.navigation.navigateToBottomNav
import app.m1k3.ai.assistant.navigation.sidebarItems
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.ai.LlamaCppEngine
import app.m1k3.ai.assistant.database.AndroidDatabaseFactory
import app.m1k3.ai.assistant.database.DatabaseConfig
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.components.MaButtonPrimary
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaRadius
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.avatar.*
import app.m1k3.ai.assistant.knowledge.KnowledgeBaseImporter
import app.m1k3.ai.assistant.knowledge.KnowledgeImportManager
import app.m1k3.ai.assistant.ui.ChatScreen
import app.m1k3.ai.assistant.ui.Avatar3DDebugScreen
import app.m1k3.ai.assistant.ui.HistoryScreen
import app.m1k3.ai.assistant.ui.EcoStatsScreen
import app.m1k3.ai.assistant.di.allModules
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.assistant.utils.FilamentSetup
import com.google.android.filament.utils.Utils
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
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
    private val logger = Logger.withTag("MainActivity")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Koin dependency injection
        try {
            startKoin {
                androidContext(this@MainActivity)
                modules(allModules)
            }
            logger.i { "Koin DI initialized successfully" }
        } catch (e: Exception) {
            logger.e(e) { "Failed to initialize Koin DI - falling back to manual DI" }
        }

        // Initialize Filament 3D engine
        FilamentSetup.initialize()
        logger.i { "Filament 3D engine initialized" }

        // Enable edge-to-edge for immersive full-screen experience
        enableEdgeToEdge()

        aiEngine = LlamaCppEngine(this)

        // Initialize database and import knowledge base
        lifecycleScope.launch {
            try {
                knowledgeImportStatus = "Loading knowledge..."

                // Initialize database
                val databaseFactory = AndroidDatabaseFactory(this@MainActivity)
                val passphrase = databaseFactory.getDatabasePassphrase()
                driver = databaseFactory.createDriver(passphrase)
                database = MaDatabase(driver!!)

                // Import knowledge using manager
                val knowledgeManager = KnowledgeImportManager(this@MainActivity, database!!)
                val result = knowledgeManager.importIfNeeded()

                knowledgeImportStatus = when (result) {
                    is KnowledgeImportManager.ImportResult.Success -> {
                        "✅ Knowledge ready: ${result.totalDocs} documents (${result.comprehensiveDocs} comprehensive + ${result.systemDocs} system)"
                    }
                    is KnowledgeImportManager.ImportResult.AlreadyImported -> {
                        "✅ Knowledge ready: ${result.existingDocs} documents"
                    }
                    is KnowledgeImportManager.ImportResult.Error -> {
                        logger.e(result.error) { result.message }
                        "⚠️ Knowledge unavailable: ${result.message}"
                    }
                }

            } catch (e: Exception) {
                logger.e(e) { "Knowledge import failed" }
                knowledgeImportStatus = "⚠️ Knowledge unavailable"
            }
        }

        setContent {
            ProvideSharedEngine {
                MaTheme {
                    val navController = rememberNavController()
                    var drawerOpen by remember { mutableStateOf(false) }

                    val drawerState = rememberDrawerState(DrawerValue.Closed)

                    LaunchedEffect(drawerOpen) {
                        if (drawerOpen) {
                            drawerState.open()
                        } else {
                            drawerState.close()
                        }
                    }

                    LaunchedEffect(drawerState.currentValue) {
                        drawerOpen = drawerState.currentValue == DrawerValue.Open
                    }

                    val haptics = LocalHapticFeedback.current
                    val isDarkMode = isSystemInDarkTheme()

                    // Animate content offset based on drawer state
                    val contentOffset by animateDpAsState(
                        targetValue = if (drawerOpen) 280.dp else 0.dp,
                        animationSpec = tween(durationMillis = 300)
                    )

                    ModalNavigationDrawer(
                        drawerContent = {
                            ModalDrawerSheet(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(280.dp),
                                drawerContainerColor = if (isDarkMode) Color.Black else Color.White,
                                drawerShape = RoundedCornerShape(
                                    topEnd = MaRadius.lg,
                                    bottomEnd = MaRadius.lg
                                )
                            ) {
                                // M1K3 Header
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = MaSpacing.base,
                                            vertical = MaSpacing.lg
                                        ),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        "M1K3",
                                        style = MaTypography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaColors.Orange,
                                        modifier = Modifier.padding(bottom = MaSpacing.sm)
                                    )
                                    Text(
                                        "Privacy-First AI",
                                        style = MaTypography.bodySmall,
                                        color = if (isDarkMode) MaColors.TextSecondary else MaColors.TextSecondaryLight
                                    )
                                }

                                HorizontalDivider(
                                    color = if (isDarkMode) MaColors.BorderSubtle else MaColors.BorderSubtleLight,
                                    thickness = 1.dp
                                )

                                Spacer(modifier = Modifier.height(MaSpacing.base))

                                // Sidebar menu items
                                sidebarItems.forEach { item ->
                                    val isSelected = navController.currentBackStackEntryAsState().value?.destination?.hierarchy?.any {
                                        it.route == item.screen.route
                                    } == true

                                    SidebarMenuItem(
                                        icon = item.icon,
                                        label = item.label,
                                        isSelected = isSelected,
                                        onClick = {
                                            if (!isSelected) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                navController.navigateToBottomNav(item.screen)
                                            }
                                            // Auto-close drawer after selection
                                            drawerOpen = false
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = MaSpacing.base)
                                    )

                                    Spacer(modifier = Modifier.height(MaSpacing.md))
                                }

                                // Bottom spacer for visual balance
                                Spacer(modifier = Modifier.weight(1f))
                                Spacer(modifier = Modifier.height(MaSpacing.lg))
                            }
                        },
                        scrimColor = if (isDarkMode) MaColors.ScrimMedium else MaColors.ScrimMediumLight,
                        drawerState = drawerState
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = contentOffset)
                                .shadow(
                                    elevation = if (drawerOpen) 8.dp else 0.dp,
                                    shape = RectangleShape
                                )
                                .clip(RoundedCornerShape(MaRadius.lg))
                        ) {
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
                                                Text("M1K3", style = MaTypography.headlineSmall)
                                                Text("Privacy-First Mobile Assistant", style = MaTypography.bodySmall)
                                            }
                                        }
                                    },
                                    navigationIcon = {
                                        IconButton(onClick = { drawerOpen = !drawerOpen }) {
                                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaColors.BgPrimary
                                    )
                                )
                            },
                            contentWindowInsets = WindowInsets.systemBars
                        ) { paddingValues ->
                            NavHost(
                                navController = navController,
                                startDestination = Screen.Chat.route,
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

                            // Avatar Screen (3D Debug Screen)
                            composable(Screen.Avatar.route) {
                                database?.let { db ->
                                    Avatar3DDebugScreen(
                                        database = db,
                                        onBackClick = { navController.navigateUp() }
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

                        // Destroy Filament engine (CRITICAL: prevents memory leaks)
                        FilamentEngineManager.forceDestroy()
                    } catch (e: Exception) {
                        logger.w(e) { "Error during cleanup" }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            logger.w { "Cleanup timeout - forcing shutdown" }
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
                        use3D = true,  // ✅ ENABLED: Reference-counted engine prevents crashes
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
