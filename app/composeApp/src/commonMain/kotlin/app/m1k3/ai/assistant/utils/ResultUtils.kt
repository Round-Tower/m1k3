package app.m1k3.ai.assistant.utils

/**
 * Standard utilities for Result-based error handling.
 *
 * M1K3 uses Kotlin's built-in `Result<T>` for error handling instead of exceptions.
 * This provides:
 * - Explicit error handling (no surprise exceptions)
 * - Type-safe error propagation
 * - Easier testing (no need to catch exceptions)
 * - Better composability with functional programming
 *
 * ## Usage Patterns
 *
 * ### Basic Pattern
 * ```kotlin
 * suspend fun loadData(): Result<Data> = resultOf {
 *     // Your code here
 *     Data(...)
 * }
 * ```
 *
 * ### With Error Mapping
 * ```kotlin
 * fun processData(data: Data): Result<ProcessedData> = resultOf {
 *     if (!data.isValid()) {
 *         throw IllegalArgumentException("Invalid data")
 *     }
 *     ProcessedData(data)
 * }
 * ```
 *
 * ### Chaining Results
 * ```kotlin
 * val result = loadData()
 *     .mapCatching { processData(it) }
 *     .onSuccess { logger.i { "Success: $it" } }
 *     .onFailure { logger.e(it) { "Failed" } }
 * ```
 *
 * ### Unwrapping Results
 * ```kotlin
 * when (val result = loadData()) {
 *     is Success -> println("Got: ${result.value}")
 *     is Failure -> println("Error: ${result.error}")
 * }
 *
 * // Or use built-in methods:
 * val data = result.getOrNull()
 * val data = result.getOrDefault(defaultValue)
 * val data = result.getOrElse { defaultValue }
 * ```
 */

/**
 * Execute a block and wrap result in Result<T>.
 *
 * Catches all exceptions and returns Result.failure.
 * Cleaner than manual try-catch blocks.
 *
 * Example:
 * ```kotlin
 * suspend fun loadUser(id: String): Result<User> = resultOf {
 *     val json = httpClient.get("/users/$id")
 *     Json.decodeFromString(json)
 * }
 * ```
 *
 * @param block Code to execute
 * @return Result.success(value) or Result.failure(exception)
 */
inline fun <T> resultOf(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Suspend version of resultOf for coroutine contexts.
 *
 * Example:
 * ```kotlin
 * suspend fun loadUserAsync(id: String): Result<User> = suspendResultOf {
 *     delay(100)  // Simulated network delay
 *     database.users.find(id)
 * }
 * ```
 *
 * @param block Suspend code to execute
 * @return Result.success(value) or Result.failure(exception)
 */
suspend inline fun <T> suspendResultOf(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Map a Result to another type with error handling.
 *
 * If the original Result is failure, returns failure.
 * If transform throws, returns failure.
 *
 * Example:
 * ```kotlin
 * val userResult: Result<User> = loadUser(id)
 * val nameResult: Result<String> = userResult.mapResult { it.name }
 * ```
 *
 * @param transform Function to transform success value
 * @return Transformed result or original/new failure
 */
inline fun <T, R> Result<T>.mapResult(transform: (T) -> R): Result<R> {
    return when {
        isSuccess -> resultOf { transform(getOrThrow()) }
        else -> Result.failure(exceptionOrNull() ?: Exception("Unknown error"))
    }
}

/**
 * Unwrap Result or throw with custom message.
 *
 * Example:
 * ```kotlin
 * val user = loadUser(id).getOrThrow("User not found: $id")
 * ```
 *
 * @param message Custom error message
 * @throws Exception with custom message if Result is failure
 */
fun <T> Result<T>.getOrThrow(message: String): T {
    return getOrElse { throw Exception(message, it) }
}

/**
 * Log Result failure with context.
 *
 * Example:
 * ```kotlin
 * loadUser(id)
 *     .logFailure(logger) { "Failed to load user $id" }
 *     .getOrNull()
 * ```
 *
 * @param logger Logger instance
 * @param message Lazy message provider (only evaluated on failure)
 * @return Original result (for chaining)
 */
inline fun <T> Result<T>.logFailure(logger: Logger, crossinline message: () -> String): Result<T> {
    onFailure { exception ->
        logger.e(exception, message())
    }
    return this
}

/**
 * Convert nullable to Result.
 *
 * Example:
 * ```kotlin
 * val user: User? = findUser(id)
 * val result: Result<User> = user.toResult { "User $id not found" }
 * ```
 *
 * @param errorMessage Lazy error message if null
 * @return Result.success(value) or Result.failure
 */
inline fun <T : Any> T?.toResult(errorMessage: () -> String): Result<T> {
    return if (this != null) {
        Result.success(this)
    } else {
        Result.failure(Exception(errorMessage()))
    }
}

/**
 * Combine multiple Results into one.
 *
 * If all succeed, returns Result.success(list of values).
 * If any fail, returns first failure.
 *
 * Example:
 * ```kotlin
 * val results = listOf(
 *     loadUser("user1"),
 *     loadUser("user2"),
 *     loadUser("user3")
 * )
 * val combined: Result<List<User>> = results.combine()
 * ```
 *
 * @return Result of combined values or first failure
 */
fun <T> List<Result<T>>.combine(): Result<List<T>> {
    val values = mutableListOf<T>()
    for (result in this) {
        when {
            result.isSuccess -> values.add(result.getOrThrow())
            else -> return Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }
    return Result.success(values)
}

/**
 * Recover from failure with fallback value.
 *
 * Example:
 * ```kotlin
 * val user = loadUser(id)
 *     .recover { User.anonymous() }
 *     .getOrThrow()
 * ```
 *
 * @param fallback Function to provide fallback value on failure
 * @return Result with success value or fallback
 */
inline fun <T> Result<T>.recover(fallback: (Throwable) -> T): Result<T> {
    return when {
        isSuccess -> this
        else -> Result.success(fallback(exceptionOrNull() ?: Exception("Unknown error")))
    }
}
