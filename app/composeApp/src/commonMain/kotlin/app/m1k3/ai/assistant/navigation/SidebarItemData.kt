package app.m1k3.ai.assistant.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation item data class for sidebar menu
 */
data class NavItem(
    val screen: Screen,
    val label: String,
    val icon: ImageVector
)

/**
 * Sidebar menu items configuration
 */
val sidebarItems = listOf(
    NavItem(
        screen = Screen.Chat,
        label = "Chat",
        icon = Icons.Default.Chat
    ),
    NavItem(
        screen = Screen.History,
        label = "History",
        icon = Icons.Default.History
    ),
    NavItem(
        screen = Screen.EcoStats,
        label = "Eco Stats",
        icon = Icons.Default.Eco
    ),
    NavItem(
        screen = Screen.Settings,
        label = "Settings",
        icon = Icons.Default.Settings
    ),
    NavItem(
        screen = Screen.Demo,
        label = "Demo",
        icon = Icons.Default.Info
    )
)
