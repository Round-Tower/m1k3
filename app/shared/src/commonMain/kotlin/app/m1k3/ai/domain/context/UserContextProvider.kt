package app.m1k3.ai.domain.context

/**
 * Platform-agnostic contract for providing user context.
 *
 * Each platform implements this with its own data sources.
 * Android: FusedLocation + HealthConnect + UsageStats + AccountManager + NotificationListener
 * iOS: CoreLocation + HealthKit + ScreenTime (future)
 */
interface UserContextProvider {
    /**
     * Fetch a fresh UserContext snapshot.
     *
     * Never throws — returns best-effort context with null fields
     * for anything unavailable or permission-denied.
     */
    suspend fun getContext(): UserContext

    /**
     * Whether any contextual data is available without additional permissions.
     */
    fun hasBasicContext(): Boolean
}
