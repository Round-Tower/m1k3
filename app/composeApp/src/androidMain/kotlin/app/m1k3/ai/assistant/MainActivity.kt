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
import androidx.compose.foundation.layout.padding
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
import app.cash.sqldelight.db.SqlDriver
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
import app.m1k3.ai.assistant.embedding.EmbeddingEngineManager
import app.m1k3.ai.assistant.avatar.ProvideSharedEngine
import app.m1k3.ai.assistant.avatar.collectAsState
import app.m1k3.ai.assistant.avatar.rememberAvatarViewModel
import app.m1k3.ai.assistant.chat.ChatScreenViewModel
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.theme.MaTheme
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.di.allModules
import app.m1k3.ai.assistant.embedding.EmbeddingEngine
import app.m1k3.ai.assistant.navigation.BottomNavigationBar
import app.m1k3.ai.assistant.navigation.Screen
import app.m1k3.ai.assistant.navigation.navigateToBottomNav
import app.m1k3.ai.assistant.ui.AboutScreen
import app.m1k3.ai.assistant.ui.ChatScreen
import app.m1k3.ai.assistant.ui.EcoStatsScreen
import app.m1k3.ai.assistant.ui.ExportScreen
import app.m1k3.ai.assistant.ui.FeedbackScreen
import app.m1k3.ai.assistant.ui.HelpScreen
import app.m1k3.ai.assistant.ui.HistoryScreen
import app.m1k3.ai.assistant.ui.PrivacyScreen
import app.m1k3.ai.assistant.ui.components.Toolbar
import app.m1k3.ai.assistant.ui.demo.MaAIDemo
import app.m1k3.ai.assistant.ui.drawer.DrawerContent
import app.m1k3.ai.assistant.utils.FilamentSetup
import app.m1k3.ai.assistant.utils.Logger
import app.m1k3.ai.assistant.avatar.webview.AvatarWebViewDemoScreen
import co.touchlab.kermit.Logger as KermitLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.koin.core.parameter.parametersOf
import android.content.Intent
import app.m1k3.ai.assistant.app.InitializationState
import app.m1k3.ai.assistant.app.InitializationViewModel
import app.m1k3.ai.assistant.app.LoggerAdapter
import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * M1K3 AI - MainActivity
 *
 * Minimalist demo showcasing:
 * - Privacy-first architecture (zero network)
 * - Encrypted database foundation
 * - Beautiful Material 3 design
 * - "Negative space" philosophy
 * - Full Koin DI with koinViewModel()
 */
class MainActivity : ComponentActivity() {
    companion object {
        private val _sharedText = MutableStateFlow<String?>(null)
        val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

        fun consumeSharedText(): String? {
            val text = _sharedText.value
            _sharedText.value = null
            return text
        }
    }

    private val aiEngine: BaseLlmEngine by inject()
    private val embeddingManager: EmbeddingEngineManager by inject()
    private val logger = Logger.withTag("MainActivity")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge for immersive full-screen experience
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.Transparent.toArgb(),
                Color.Transparent.toArgb()
            ),
            navigationBarStyle = SystemBarStyle.auto(
                MaColors.BgPrimaryLight.toArgb(),
                MaColors.BgPrimary.toArgb()
            )
        )

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

        // Initialize embedding engine for RAG/semantic search
        lifecycleScope.launch {
            embeddingManager.initialize().onFailure { e ->
                logger.e(e) { "Embedding engine initialization failed" }
            }.onSuccess {
                logger.i { "Embedding engine loaded successfully" }
            }
        }

        // Handle share intent on cold start
        handleShareIntent(intent)

        setContent {
            MaApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                logger.i { "Received shared text (${text.length} chars)" }
                _sharedText.value = text
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
 * MaApp - Main application composable with initialization management
 *
 * Uses InitializationViewModel to handle database and knowledge base setup.
 * Shows loading, success, or error states based on initialization progress.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaApp() {
    val initViewModel = koinViewModel<InitializationViewModel>()
    val initState by initViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        initViewModel.initialize()
    }

    when (val state = initState) {
        is InitializationState.NotStarted -> {
            // Show nothing or splash
        }
        is InitializationState.Loading -> {
            // TODO: Create proper loading screen
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
            }
        }
        is InitializationState.Success -> {
            MaAppContent(
                knowledgeStatus = state.knowledgeStatus
            )
        }
        is InitializationState.Error -> {
            // TODO: Create proper error screen with retry
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Initialization failed: ${state.message}")
                    TextButton(onClick = { initViewModel.retry() }) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

/**
 * MaAppContent - Main app UI after successful initialization
 *
 * Contains navigation, drawer, and all app screens.
 * Database is available via Koin injection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaAppContent(
    knowledgeStatus: String
) {
    // Get database from Koin for screens that need it directly
    val database = koinInject<MaDatabase>()

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

            ModalNavigationDrawer(
                drawerContent = {
                    DrawerContent(
                        currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route,
                        isDarkMode = isDarkMode,
                        onItemClick = { route ->
                            // Map route to Screen and navigate
                            when (route) {
                                "chat" -> navController.navigateToBottomNav(Screen.Chat)
                                "history" -> navController.navigateToBottomNav(Screen.History)
                                "ecostats" -> navController.navigateToBottomNav(Screen.EcoStats)
                                "settings" -> navController.navigateToBottomNav(Screen.Settings)
                                "avatar-webview-demo" -> navController.navigate(Screen.AvatarWebViewDemo.route)
                                else -> navController.navigateToBottomNav(Screen.Chat)
                            }
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
                        bottomBar = {
                            BottomNavigationBar(
                                navController = navController,
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
                                modifier = Modifier.padding(paddingValues)
                            ) {
                                // Demo Screen
                                composable(Screen.Demo.route) {
                                    MaAIDemo(
                                        onChatClick = { navController.navigate(Screen.Chat.route) },
                                        knowledgeStatus = knowledgeStatus
                                    )
                                }

                                // Chat Screen
                                composable(Screen.Chat.route) {
                                    ChatScreen(
                                        onEcoStatsClick = { navController.navigate(Screen.EcoStats.route) },
                                        projectId = "default",
                                    )
                                }

                                // History Screen
                                composable(Screen.History.route) {
                                    HistoryScreen(
                                        database = database,
                                        projectId = "default",
                                        onBackClick = { navController.navigateUp() },
                                        onConversationClick = { conversationId ->
                                            navController.navigate("conversation/$conversationId")
                                        }
                                    )
                                }

                                // Eco Stats Screen
                                composable(Screen.EcoStats.route) {
                                    EcoStatsScreen(
                                        database = database,
                                        projectId = "default",
                                        onBackClick = { navController.navigateUp() }
                                    )
                                }

                                // Settings Screen
                                composable(Screen.Settings.route) {
                                    app.m1k3.ai.assistant.ui.SettingsScreen()
                                }

                                // Meta Screens (Drawer actions)
                                composable(Screen.About.route) {
                                    AboutScreen()
                                }

                                composable(Screen.Help.route) {
                                    HelpScreen()
                                }

                                composable(Screen.Feedback.route) {
                                    FeedbackScreen()
                                }

                                composable(Screen.Privacy.route) {
                                    PrivacyScreen()
                                }

                                composable(Screen.Export.route) {
                                    ExportScreen()
                                }

                                // WebView Avatar Demo (Phase 1)
                                composable("avatar-webview-demo") {
                                    AvatarWebViewDemoScreen(
                                        onBackClick = { navController.navigateUp() }
                                    )
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
