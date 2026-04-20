package app.m1k3.ai.assistant.app

/**
 * Sealed class representing database initialization result.
 *
 * Type-safe result handling without exceptions for expected failures.
 */
sealed class DatabaseInitResult {
    data class Success(
        val database: Any,
    ) : DatabaseInitResult()

    data class Error(
        val message: String,
        val error: Exception?,
    ) : DatabaseInitResult()
}
