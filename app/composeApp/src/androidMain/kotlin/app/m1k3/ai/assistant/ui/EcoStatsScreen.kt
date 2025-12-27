package app.m1k3.ai.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackType
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaFontFamilyCaption
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.eco.rememberEcoStatsViewModel
import app.m1k3.ai.assistant.history.collectAsState
import app.m1k3.ai.assistant.ui.components.*
import kotlinx.coroutines.launch

/**
 * EcoStatsScreen - Environmental Statistics Screen
 *
 * Comprehensive environmental impact dashboard:
 * - Lifetime savings across all projects
 * - Project-specific metrics
 * - Local vs Cloud comparison
 * - Visual charts and progress indicators
 *
 * **Architecture:**
 * - Uses EcoStatsViewModel for state management
 * - Delegates to extracted components (LifetimeStatsCard, CloudComparisonCard, etc.)
 * - Minimal UI logic - ViewModel handles business logic
 *
 * Philosophy: Transparency breeds accountability - show real impact.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcoStatsScreen(
    database: MaDatabase,
    projectId: String? = null,
    onBackClick: () -> Unit = {}
) {
    val haptics = rememberHapticFeedback()
    val scope = rememberCoroutineScope()

    // EcoStatsViewModel - Single source of truth for eco stats state
    val viewModel = rememberEcoStatsViewModel(database = database)
    val state by viewModel.collectAsState()

    // Load stats on first composition
    LaunchedEffect(projectId) {
        viewModel.loadLifetimeStats()
        projectId?.let { viewModel.loadProjectStats(it) }
    }

    Scaffold(
        topBar = {
            EcoStatsTopBar(
                onBackClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    onBackClick()
                },
                onRefreshClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                    scope.launch {
                        viewModel.loadLifetimeStats()
                        projectId?.let { viewModel.loadProjectStats(it) }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (state.isLoading) {
                // Loading indicator
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaColors.Orange)
                }
            } else {
                EcoStatsContent(
                    state = state,
                    projectId = projectId,
                    onClearError = { viewModel.clearError() }
                )
            }
        }
    }
}

/**
 * EcoStatsTopBar - Top app bar for eco stats screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EcoStatsTopBar(
    onBackClick: () -> Unit,
    onRefreshClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    "Environmental Impact",
                    style = MaTypography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaColors.TextPrimary
                )
                Text(
                    "100% local AI inference",
                    style = TextStyle(
                        fontFamily = MaFontFamilyCaption,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.25.sp
                    ),
                    color = MaColors.TextSecondary
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaColors.TextPrimary
                )
            }
        },
        actions = {
            IconButton(onClick = onRefreshClick) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = MaColors.TextPrimary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaColors.BgPrimary
        )
    )
}

/**
 * EcoStatsContent - Main content area for eco stats.
 */
@Composable
private fun EcoStatsContent(
    state: app.m1k3.ai.assistant.history.EcoStatsState,
    projectId: String?,
    onClearError: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MaSpacing.md),
        verticalArrangement = Arrangement.spacedBy(MaSpacing.md)
    ) {
        // Error display
        state.error?.let { error ->
            EcoErrorCard(
                message = error,
                onDismiss = onClearError
            )
        }

        // Lifetime stats
        state.lifetimeStats?.let { stats ->
            LifetimeStatsCard(stats = stats)
        }

        // Cloud comparison
        state.cloudComparison?.let { comparison ->
            CloudComparisonCard(comparison = comparison)
        }

        // Project-specific stats
        if (projectId != null) {
            state.projectMetrics?.let { metrics ->
                ProjectMetricsCard(
                    projectId = projectId,
                    metrics = metrics
                )
            }
        }

        // Empty state
        if (state.lifetimeStats == null && !state.isLoading) {
            EmptyStatsCard()
        }

        // Privacy statement
        PrivacyStatementCard()
    }
}
