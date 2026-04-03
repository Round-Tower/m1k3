package app.m1k3.ai.assistant.ui.drawer

/**
 * DrawerContent Composables
 *
 * Extracted drawer UI components from MainActivity
 *
 * **Responsibilities:**
 * - Render M1K3 branding header
 * - Display navigation menu items (Chat, History, Eco Stats, Settings)
 * - Manage drawer open/close state
 * - Apply theme-aware colors (dark/light mode)
 * - Handle user interactions (item selection, auto-close)
 *
 * **Composables:**
 * - `@Composable fun DrawerContent(...)` - Main drawer composite
 * - `@Composable fun DrawerHeader(...)` - Header with M1K3 branding
 * - `@Composable fun MenuItems(...)` - Navigation item list
 *
 * **Data Classes:**
 * - `SidebarMenuItem` - Single menu item data
 * - `DrawerState` - Manages drawer visibility state
 *
 * **Integration:**
 * Used in MainActivity with ModalNavigationDrawer
 *
 * ```kotlin
 * ModalNavigationDrawer(
 *     drawerContent = {
 *         DrawerContent(
 *             currentRoute = navController.currentRoute,
 *             isDarkMode = isDarkMode,
 *             onItemClick = { screen -> navController.navigate(screen) },
 *             onMenuClose = { drawerOpen = false }
 *         )
 *     }
 * ) {
 *     // Main content
 * }
 * ```
 */

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.m1k3.ai.assistant.design.tokens.MaColors
import app.m1k3.ai.assistant.design.tokens.MaSpacing
import app.m1k3.ai.assistant.design.tokens.MaTypography
import app.m1k3.ai.assistant.navigation.SidebarMenuItem
import app.m1k3.ai.assistant.navigation.sidebarItems

/**
 * DrawerContent - Main drawer composable
 *
 * Renders the navigation drawer with:
 * - M1K3 branding header
 * - Navigation menu items
 * - Theme-aware colors
 * - Auto-close behavior on selection
 *
 * @param currentRoute Current navigation route for highlighting selected item
 * @param isDarkMode Whether dark mode is active
 * @param onItemClick Callback when menu item selected
 * @param onMenuClose Callback to close drawer after navigation
 * @param modifier Optional modifier for drawer content
 */
@Composable
fun DrawerContent(
    currentRoute: String?,
    isDarkMode: Boolean,
    onItemClick: (String) -> Unit,
    onMenuClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp),
        drawerContainerColor = if (isDarkMode) MaColors.BgPrimary else MaColors.BgPrimaryLight
    ) {
        // Header
        DrawerHeader(isDarkMode = isDarkMode)

        HorizontalDivider(
            color = if (isDarkMode) MaColors.BorderSubtle else MaColors.BorderSubtleLight,
            thickness = 1.dp
        )

        Spacer(modifier = Modifier.height(MaSpacing.base))

        // Menu Items
        MenuItems(
            currentRoute = currentRoute,
            isDarkMode = isDarkMode,
            onItemClick = onItemClick,
            onMenuClose = onMenuClose
        )

        // Bottom spacer for visual balance
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(MaSpacing.lg))
    }
}

/**
 * DrawerHeader - M1K3 branding header with animated logo
 *
 * Displays app name, kanji mark, and tagline
 *
 * @param isDarkMode Whether dark mode is active (for potential future styling)
 */
@Composable
private fun DrawerHeader(isDarkMode: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaSpacing.base,
                vertical = MaSpacing.lg
            ),
        horizontalAlignment = Alignment.Start
    ) {
        // Kanji mark + brand name row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaSpacing.md)
        ) {
            // 間 mark
            Text(
                "間",
                style = MaTypography.displaySmall,
                color = MaColors.Orange
            )
            Column {
                Text(
                    "M1K3",
                    style = MaTypography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaColors.textPrimary()
                )
                Text(
                    "Call me Mike",
                    style = MaTypography.bodySmall,
                    color = MaColors.textMuted()
                )
            }
        }
    }
}

/**
 * MenuItems - Navigation menu items list
 *
 * Displays all sidebar navigation items with selection highlighting
 *
 * @param currentRoute Current navigation route
 * @param isDarkMode Whether dark mode is active
 * @param onItemClick Callback when item selected
 * @param onMenuClose Callback to close drawer after selection
 */
@Composable
private fun MenuItems(
    currentRoute: String?,
    isDarkMode: Boolean,
    onItemClick: (String) -> Unit,
    onMenuClose: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    sidebarItems.forEach { item ->
        val isSelected = currentRoute == item.screen.route

        SidebarMenuItem(
            icon = item.icon,
            label = item.label,
            isSelected = isSelected,
            onClick = {
                if (!isSelected) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onItemClick(item.screen.route)
                }
                // Auto-close drawer after selection
                onMenuClose()
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaSpacing.base)
        )

        Spacer(modifier = Modifier.height(MaSpacing.md))
    }
}
