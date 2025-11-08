package app.m1k3.ai.assistant.navigation

/**
 * 間 AI - Navigation Routes
 *
 * Type-safe navigation destinations for the 間 AI mobile app.
 * Implements sealed class hierarchy for compile-time safety.
 *
 * **Philosophy:**
 * Navigation should be simple, predictable, and type-safe.
 * Every screen is a destination, every destination has a route.
 *
 * **Bottom Nav Tabs:**
 * - Chat: Main AI conversation interface
 * - History: Browse past conversations
 * - Eco Stats: Environmental impact dashboard
 * - Avatar: 3D avatar visualization + debug
 * - Settings: App configuration and preferences
 * - Demo: Welcome/demo screen for new users
 */
sealed class Screen(val route: String) {
    /**
     * Home/Demo screen - Welcome and feature showcase
     */
    data object Demo : Screen("demo")

    /**
     * Chat screen - Main AI conversation interface
     */
    data object Chat : Screen("chat")

    /**
     * History screen - Browse and search past conversations
     */
    data object History : Screen("history")

    /**
     * Eco Stats screen - Environmental impact dashboard
     */
    data object EcoStats : Screen("eco_stats")

    /**
     * Avatar screen - 3D avatar visualization and debug controls
     */
    data object Avatar : Screen("avatar")

    /**
     * 3D WebView screen - Three.js WebGL avatar rendering proof of concept
     */
    data object Avatar3DWebView : Screen("avatar_3d_webview")

    /**
     * Settings screen - App configuration and preferences
     */
    data object Settings : Screen("settings")

    /**
     * Conversation Detail screen - View specific conversation messages
     *
     * Route: "conversation/{conversationId}"
     * Args: conversationId (Long)
     */
    data class ConversationDetail(val conversationId: Long) : Screen("conversation/$conversationId") {
        companion object {
            const val route = "conversation/{conversationId}"
            const val argConversationId = "conversationId"
        }
    }

    companion object {
        /**
         * Get all bottom nav destinations
         */
        val bottomNavScreens = listOf(
            Chat,
            History,
            EcoStats,
            Avatar,
            Settings,
            Demo
        )

        /**
         * Get route from Screen instance
         */
        fun Screen.getRoute(): String = this.route

        /**
         * Check if route is a bottom nav destination
         */
        fun isBottomNavDestination(route: String?): Boolean {
            return bottomNavScreens.any { it.route == route }
        }
    }
}
