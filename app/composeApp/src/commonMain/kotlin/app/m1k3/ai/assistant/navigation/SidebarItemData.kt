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
 * Sidebar menu items configuration - Meta/secondary actions
 *
 * **Philosophy:**
 * Bottom nav = primary workflow (Chat, History, Eco Stats, Settings, Demo)
 * Drawer = meta/app-level actions (About, Help, Feedback, etc.)
 *
 * This differentiation prevents redundancy and gives each navigation
 * method a clear purpose.
 */
val sidebarItems = listOf(
    NavItem(
        screen = Screen.About,
        label = "About M1K3",
        icon = Icons.Default.Info
    ),
    NavItem(
        screen = Screen.Help,
        label = "Help & Docs",
        icon = Icons.Default.Help
    ),
    NavItem(
        screen = Screen.Feedback,
        label = "Send Feedback",
        icon = Icons.Default.Feedback
    ),
    NavItem(
        screen = Screen.Privacy,
        label = "Privacy Policy",
        icon = Icons.Default.Shield
    ),
    NavItem(
        screen = Screen.Export,
        label = "Export Data",
        icon = Icons.Default.Download
    )
)
