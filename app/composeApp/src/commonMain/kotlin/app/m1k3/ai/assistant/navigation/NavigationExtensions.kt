package app.m1k3.ai.assistant.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions

/**
 * 間 AI - Navigation Extensions
 *
 * Helper functions for type-safe navigation throughout the app.
 *
 * **Philosophy:**
 * Make the right thing easy and the wrong thing hard.
 * Type-safe navigation prevents runtime crashes from typos.
 */

/**
 * Navigate to a screen with type safety
 *
 * @param screen Destination screen
 * @param builder Optional navigation options
 */
fun NavController.navigateTo(
    screen: Screen,
    builder: (NavOptionsBuilder.() -> Unit)? = null
) {
    val navOptions = builder?.let { navOptions(it) }
    navigate(screen.route, navOptions)
}

/**
 * Navigate to a bottom nav destination with proper back stack management
 *
 * Pops to start destination and uses single top to avoid duplicate screens.
 */
fun NavController.navigateToBottomNav(screen: Screen) {
    navigate(screen.route) {
        // Pop up to the start destination of the graph to
        // avoid building up a large stack of destinations
        // on the back stack as users select items
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        // Avoid multiple copies of the same destination when
        // reselecting the same item
        launchSingleTop = true
        // Restore state when reselecting a previously selected item
        restoreState = true
    }
}

/**
 * Navigate to conversation detail screen
 *
 * @param conversationId ID of conversation to view
 */
fun NavController.navigateToConversationDetail(conversationId: Long) {
    navigateTo(Screen.ConversationDetail(conversationId))
}

/**
 * Navigate back with safety check
 *
 * @return true if navigation was successful, false if no destination to pop
 */
fun NavController.navigateBack(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}

/**
 * Get current route safely
 *
 * @return Current route or null if not available
 */
fun NavController.getCurrentRoute(): String? {
    return currentBackStackEntry?.destination?.route
}

/**
 * Check if current destination is a bottom nav screen
 *
 * @return true if current screen is in bottom nav
 */
fun NavController.isOnBottomNavScreen(): Boolean {
    val currentRoute = getCurrentRoute()
    return Screen.isBottomNavDestination(currentRoute)
}
