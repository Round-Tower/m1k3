package app.m1k3.ai.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.m1k3.ai.assistant.database.MaDatabase
import app.m1k3.ai.assistant.design.components.MaButtonPrimary
import app.m1k3.ai.assistant.design.components.MaButtonSecondary
import app.m1k3.ai.assistant.design.components.MaCard
import app.m1k3.ai.assistant.design.haptics.HapticFeedbackType
import app.m1k3.ai.assistant.design.haptics.rememberHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaFontFamilyCaption
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.history.*
import kotlinx.datetime.Instant

/**
 * 間 AI - Conversation History Screen
 *
 * Comprehensive conversation management:
 * - Browse all conversations by project
 * - Search messages across conversations
 * - Export to JSON/Markdown
 * - Delete conversations
 *
 * Philosophy: Your data, your control - full transparency and portability
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    database: MaDatabase,
    projectId: String,
    onBackClick: () -> Unit = {},
    onConversationClick: (Long) -> Unit = {},
) {
    val haptics = rememberHapticFeedback()
    val scope = rememberCoroutineScope()

    // Initialize ViewModel
    val viewModel =
        remember(projectId) {
            val conversationRepo = ConversationRepository(database)
            val searchRepo = SearchRepository(database)
            val exportManager = ExportManager(database)
            HistoryViewModel(
                conversationRepository = conversationRepo,
                searchRepository = searchRepo,
                exportManager = exportManager,
                scope = scope,
            )
        }

    val state by viewModel.state.collectAsState()

    // Load conversations on first composition
    LaunchedEffect(projectId) {
        viewModel.loadConversations(projectId)
    }

    // Dialog states
    var showDeleteDialog by remember { mutableStateOf<Long?>(null) }
    var showExportDialog by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "💬 Conversation History",
                            style = MaTypography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaColors.TextPrimary,
                        )
                        Text(
                            "${state.conversations.size} conversations",
                            style =
                                TextStyle(
                                    fontFamily = MaFontFamilyCaption,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    letterSpacing = 0.25.sp,
                                ),
                            color = MaColors.TextSecondary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                        onBackClick()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaColors.TextPrimary,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaColors.BgPrimary,
                    ),
            )
        },
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(MaSpacing.md),
            ) {
                // Search bar
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.searchConversations(it) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = MaSpacing.md),
                )

                // Error display
                state.error?.let { error ->
                    ErrorCard(
                        message = error,
                        onDismiss = { viewModel.clearError() },
                        modifier = Modifier.padding(bottom = MaSpacing.md),
                    )
                }

                // Loading indicator
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaColors.Orange,
                        )
                    }
                }

                // Search results or conversation list
                else if (state.searchResults != null) {
                    SearchResultsList(
                        results = state.searchResults!!,
                        onResultClick = { result ->
                            result.conversationId?.let { onConversationClick(it) }
                        },
                    )
                } else {
                    ConversationsList(
                        conversations = state.conversations,
                        onConversationClick = onConversationClick,
                        onDeleteClick = { showDeleteDialog = it },
                        onExportClick = { showExportDialog = it },
                    )
                }
            }

            // Delete confirmation dialog
            showDeleteDialog?.let { conversationId ->
                DeleteConfirmationDialog(
                    conversation = state.conversations.find { it.id == conversationId },
                    onConfirm = {
                        viewModel.deleteConversation(conversationId)
                        showDeleteDialog = null
                    },
                    onDismiss = { showDeleteDialog = null },
                )
            }

            // Export dialog
            showExportDialog?.let { conversationId ->
                ExportDialog(
                    conversation = state.conversations.find { it.id == conversationId },
                    onExport = { format ->
                        val exported = viewModel.exportConversation(conversationId, format)
                        // TODO: Share exported content via Android share sheet
                        println("Exported: ${exported?.take(100)}...")
                        showExportDialog = null
                    },
                    onDismiss = { showExportDialog = null },
                )
            }
        }
    }
}

/**
 * Search bar for filtering conversations
 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    MaCard(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaColors.TextSecondary,
                modifier = Modifier.size(20.dp),
            )

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle =
                    MaTypography.bodyMedium.copy(
                        color = MaColors.TextPrimary,
                    ),
                cursorBrush = SolidColor(MaColors.Orange),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            "Search conversations...",
                            style = MaTypography.bodyMedium,
                            color = MaColors.TextMuted,
                        )
                    }
                    innerTextField()
                },
            )

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = MaColors.TextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * List of conversations
 */
@Composable
private fun ConversationsList(
    conversations: List<ConversationInfo>,
    onConversationClick: (Long) -> Unit,
    onDeleteClick: (Long) -> Unit,
    onExportClick: (Long) -> Unit,
) {
    if (conversations.isEmpty()) {
        EmptyState(
            message = "No conversations yet",
            subtitle = "Start chatting to see your history here",
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
        ) {
            items(conversations, key = { it.id }) { conversation ->
                ConversationCard(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation.id) },
                    onDeleteClick = { onDeleteClick(conversation.id) },
                    onExportClick = { onExportClick(conversation.id) },
                )
            }
        }
    }
}

/**
 * Individual conversation card
 */
@Composable
private fun ConversationCard(
    conversation: ConversationInfo,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onExportClick: () -> Unit,
) {
    val haptics = rememberHapticFeedback()
    var expanded by remember { mutableStateOf(false) }

    MaCard(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
            onClick()
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
        ) {
            // Title and message count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = conversation.title ?: "Untitled Conversation",
                        style = MaTypography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaColors.TextPrimary,
                        maxLines = 2,
                    )

                    Spacer(modifier = Modifier.height(MaSpacing.xs))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaSpacing.md),
                    ) {
                        Text(
                            text = "${conversation.messageCount} messages",
                            style =
                                TextStyle(
                                    fontFamily = MaFontFamilyCaption,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    letterSpacing = 0.25.sp,
                                ),
                            color = MaColors.TextSecondary,
                        )

                        Text(
                            text = "${conversation.tokenCount} tokens",
                            style =
                                TextStyle(
                                    fontFamily = MaFontFamilyCaption,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    letterSpacing = 0.25.sp,
                                ),
                            color = MaColors.TextSecondary,
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                        expanded = !expanded
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector =
                            if (expanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaColors.TextSecondary,
                    )
                }
            }

            // Timestamp
            Spacer(modifier = Modifier.height(MaSpacing.xs))
            Text(
                text = formatTimestamp(conversation.lastMessageAt),
                style = MaTypography.bodySmall,
                color = MaColors.TextMuted,
            )

            // Expanded actions
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(MaSpacing.md))

                    HorizontalDivider(
                        color = MaColors.BorderLight,
                        modifier = Modifier.padding(vertical = MaSpacing.sm),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                    ) {
                        MaButtonSecondary(
                            text = "Export",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                onExportClick()
                            },
                            modifier = Modifier.weight(1f),
                        )

                        MaButtonSecondary(
                            text = "Delete",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
                                onDeleteClick()
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Search results list
 */
@Composable
private fun SearchResultsList(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
) {
    if (results.isEmpty()) {
        EmptyState(
            message = "No results found",
            subtitle = "Try a different search term",
        )
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
        ) {
            items(results, key = { it.id }) { result ->
                SearchResultCard(
                    result = result,
                    onClick = { onResultClick(result) },
                )
            }
        }
    }
}

/**
 * Individual search result card
 */
@Composable
private fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
) {
    val haptics = rememberHapticFeedback()

    MaCard(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LIGHT)
            onClick()
        },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
        ) {
            // Relevance score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = result.role.uppercase(),
                    style = MaTypography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (result.role == "user") MaColors.Orange else MaColors.TextSecondary,
                )

                Text(
                    text = "${(result.relevanceScore * 100).toInt()}% match",
                    style = MaTypography.labelSmall,
                    color = MaColors.TextMuted,
                )
            }

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            // Content preview
            Text(
                text = result.content,
                style = MaTypography.bodyMedium,
                color = MaColors.TextPrimary,
                maxLines = 3,
            )

            Spacer(modifier = Modifier.height(MaSpacing.sm))

            // Timestamp
            Text(
                text = formatTimestamp(result.timestamp),
                style = MaTypography.bodySmall,
                color = MaColors.TextMuted,
            )
        }
    }
}

/**
 * Delete confirmation dialog
 */
@Composable
private fun DeleteConfirmationDialog(
    conversation: ConversationInfo?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Delete Conversation?",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Text(
                "\"${conversation?.title}\" and all ${conversation?.messageCount} messages will be permanently deleted.",
                style = MaTypography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaColors.Error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaColors.TextSecondary)
            }
        },
        containerColor = MaColors.BgElevated,
        textContentColor = MaColors.TextPrimary,
    )
}

/**
 * Export format selection dialog
 */
@Composable
private fun ExportDialog(
    conversation: ConversationInfo?,
    onExport: (ExportFormat) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Export Conversation",
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
            ) {
                Text(
                    "Choose export format for \"${conversation?.title}\":",
                    style = MaTypography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(MaSpacing.sm))

                MaButtonPrimary(
                    text = "JSON (Machine-readable)",
                    onClick = { onExport(ExportFormat.JSON) },
                    modifier = Modifier.fillMaxWidth(),
                )

                MaButtonSecondary(
                    text = "Markdown (Human-readable)",
                    onClick = { onExport(ExportFormat.MARKDOWN) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaColors.TextSecondary)
            }
        },
        containerColor = MaColors.BgElevated,
        textContentColor = MaColors.TextPrimary,
    )
}

/**
 * Error card display
 */
@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MaCard(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(MaSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(MaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = MaColors.Error,
                )
                Text(
                    text = message,
                    style = MaTypography.bodyMedium,
                    color = MaColors.Error,
                )
            }

            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaColors.Error,
                )
            }
        }
    }
}

/**
 * Empty state display
 */
@Composable
private fun EmptyState(
    message: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaSpacing.sm),
        ) {
            Text(
                text = message,
                style = MaTypography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaColors.TextSecondary,
            )
            Text(
                text = subtitle,
                style = MaTypography.bodyMedium,
                color = MaColors.TextMuted,
            )
        }
    }
}

/**
 * Format timestamp for display
 */
private fun formatTimestamp(timestampMs: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestampMs)
    val now =
        kotlinx.datetime.Clock.System
            .now()

    val diffMs = now.toEpochMilliseconds() - timestampMs
    val diffMinutes = diffMs / 60000
    val diffHours = diffMinutes / 60
    val diffDays = diffHours / 24

    return when {
        diffMinutes < 1 -> "Just now"
        diffMinutes < 60 -> "$diffMinutes min ago"
        diffHours < 24 -> "$diffHours hours ago"
        diffDays < 7 -> "$diffDays days ago"
        else -> {
            // Format as date
            val date = instant.toString().substringBefore("T")
            date
        }
    }
}
