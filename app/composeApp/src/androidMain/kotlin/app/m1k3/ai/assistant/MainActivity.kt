package app.m1k3.ai.assistant

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.m1k3.ai.assistant.ai.BaseLlmEngine
import app.m1k3.ai.assistant.ai.LlamaCppEngine
import app.m1k3.ai.assistant.app.AndroidDatabaseInitializer
import app.m1k3.ai.assistant.app.AppInitializationManager
import app.m1k3.ai.assistant.app.DatabaseInitResult
import app.m1k3.ai.assistant.app.ILogger
import app.m1k3.ai.assistant.app.InitializationResult
import app.m1k3.ai.assistant.app.KnowledgeImportResult
import app.m1k3.ai.assistant.avatar.FilamentEngineManager
import app.m1k3.ai.assistant.avatar.LocalSharedAvatarVM
import app.m1k3.ai.assistant.avatar.ProvideSharedEngine
import app.m1k3.ai.assistant.avatar.collectAsState
import app.m1k3.ai.assistant.avatar.rememberAvatarViewModel
import app.m1k3.ai.assistant.chat.ChatScreenViewModel
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.di.allModules
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.navigation.Screen
import app.m1k3.ai.assistant.navigation.navigateToBottomNav
import app.m1k3.ai.assistant.ui.ChatScreen
import app.m1k3.ai.assistant.ui.EcoStatsScreen
import app.m1k3.ai.assistant.ui.HistoryScreen
import app.m1k3.ai.assistant.ui.components.Toolbar
import app.m1k3.ai.assistant.ui.demo.MaAIDemo
import app.m1k3.ai.assistant.ui.drawer.DrawerContent
import app.m1k3.ai.assistant.utils.FilamentSetup
import app.m1k3.ai.assistant.utils.Logger
import co.touchlab.kermit.Logger as KermitLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import app.m1k3.ai.assistant.chat.createChatScreenViewModel

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

        // Enable edge-to-edge for immersive full-screen experience
        enableEdgeToEdge()

        // Initialize Koin & Filament using AppInitializationManager
        val appInitManager = AppInitializationManager(
            logger = LoggerAdapter(logger),
            koinInitializer = {
                startKoin {
                    androidContext(this@MainActivity)
                    modules(allModules)
                }
            },
            filamentInitializer = { FilamentSetup.initialize() }
        )

        val koinResult = appInitManager.initializeKoin()
        if (koinResult !is InitializationResult.Success) {
            logger.e { "Koin initialization failed" }
        }

        val filamentResult = appInitManager.initializeFilament()
        if (filamentResult !is InitializationResult.Success) {
            logger.e { "Filament initialization failed" }
        }

        // Initialize AI engine
        aiEngine = LlamaCppEngine(this)

        // Initialize database and import knowledge base
        lifecycleScope.launch {
            try {
                knowledgeImportStatus = "Loading knowledge..."

                val initializer = AndroidDatabaseInitializer(this@MainActivity, LoggerAdapter(logger))

                // Initialize database
                val dbResult = initializer.initializeDatabase()
                when (dbResult) {
                    is DatabaseInitResult.Success -> {
                        database = dbResult.database as MaDatabase
                        driver = null // Driver is managed by MaDatabase

                        // Import knowledge
                        val knowledgeResult = initializer.importKnowledge(database!!)
                        knowledgeImportStatus = when (knowledgeResult) {
                            is KnowledgeImportResult.Success -> {
                                "✅ Knowledge ready: ${knowledgeResult.totalDocs} documents (${knowledgeResult.comprehensiveDocs} comprehensive + ${knowledgeResult.systemDocs} system)"
                            }
                            is KnowledgeImportResult.AlreadyImported -> {
                                "✅ Knowledge ready: ${knowledgeResult.existingDocs} documents"
                            }
                            is KnowledgeImportResult.Error -> {
                                "⚠️ Knowledge unavailable: ${knowledgeResult.message}"
                            }
                        }
                    }
                    is DatabaseInitResult.Error -> {
                        logger.e { "Database initialization failed: ${dbResult.message}" }
                        knowledgeImportStatus = "⚠️ Knowledge unavailable"
                        driver = null
                    }
                }
            } catch (e: Exception) {
                logger.e(e) { "Database/Knowledge initialization failed" }
                knowledgeImportStatus = "⚠️ Knowledge unavailable"
            }
        }

        setContent {
            ProvideSharedEngine {
                MaTheme {
                    val navController = rememberNavController()
                    val appAvatarVM = rememberAvatarViewModel()
                    val appAvatarState by appAvatarVM.collectAsState()
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

                    val chatViewModel = rememberChatScreenViewModel(
                        aiEngine = aiEngine,
                        database = database,
                        context = this,
                        projectId = "default",
                    )

                    ModalNavigationDrawer(
                        drawerContent = {
                            DrawerContent(
                                currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route,
                                isDarkMode = isDarkMode,
                                onItemClick = { route ->
                                    // Map route to Screen and navigate
                                    val screen = when (route) {
                                        "chat" -> Screen.Chat
                                        "history" -> Screen.History
                                        "ecostats" -> Screen.EcoStats
                                        "settings" -> Screen.Settings
                                        else -> Screen.Chat
                                    }
                                    navController.navigateToBottomNav(screen)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onMenuClose = { drawerOpen = false },
                            )
                        },
                        scrimColor = if (isDarkMode) MaColors.ScrimMedium else MaColors.ScrimMediumLight,
                        drawerState = drawerState
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .offset(x = contentOffset)
                        ) {
                            Scaffold(
                                topBar = {
                                    Toolbar(
                                        screenName = getScreenName(navController.currentBackStackEntryAsState().value?.destination?.route),
                                        engineInitialized = true,
                                        avatarState = appAvatarState,
                                        onMenuClick = { drawerOpen = !drawerOpen },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                            contentWindowInsets = WindowInsets.systemBars
                        ) { paddingValues ->
                            CompositionLocalProvider(
                                LocalSharedAvatarVM provides appAvatarVM
                            ) {
                                NavHost(
                                    navController = navController,
                                    startDestination = Screen.Chat.route,
                                ) {
                                // Demo Screen
                                composable(Screen.Demo.route) {
                                    MaAIDemo(
                                        onChatClick = { navController.navigate(Screen.Chat.route) },
                                        knowledgeStatus = knowledgeImportStatus
                                    )
                                }

                                // Chat Screen
                                composable(Screen.Chat.route) {
                                    if (database != null) {
                                        ChatScreen(
                                            onEcoStatsClick = { navController.navigate(Screen.EcoStats.route) },
                                            viewModel = chatViewModel
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

/**
 * Helper to get screen name from route for UnifiedToolbar
 */
private fun getScreenName(route: String?): String = when (route) {
    Screen.Chat.route -> "Chat"
    Screen.History.route -> "History"
    Screen.EcoStats.route -> "Environmental Impact"
    Screen.Settings.route -> "Settings"
    Screen.Demo.route -> "Welcome"
    else -> "M1K3"
}

/**
 * Adapter to convert KermitLogger to ILogger interface
 * Bridges the application's logger with AppInitializationManager's expectations
 */
class LoggerAdapter(private val logger: KermitLogger) : ILogger {
    override fun i(message: String) {
        logger.i { message }
    }

    override fun e(error: Throwable?, message: String) {
        logger.e(error) { message }
    }
}

@Composable
fun rememberChatScreenViewModel(
    aiEngine: BaseLlmEngine,
    database: MaDatabase?,
    context: Context,
    projectId: String,
    embeddingEngine: EmbeddingEngine? = null
): ChatScreenViewModel? {
    val scope = rememberCoroutineScope()
    if (database == null) {
        return null
    }
    return remember(projectId, aiEngine, database) {
        createChatScreenViewModel(
            aiEngine = aiEngine,
            database = database,
            context = context,
            projectId = projectId,
            scope = scope,
            embeddingEngine = embeddingEngine
        )
    }
}