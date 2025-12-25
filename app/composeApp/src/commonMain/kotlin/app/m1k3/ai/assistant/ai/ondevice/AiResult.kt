package app.m1k3.ai.assistant.ai.ondevice

/**
 * Result type for on-device AI operations.
 *
 * Provides a type-safe way to handle success and error cases from AI inference.
 * Inspired by Kotlin's Result type but specialized for AI operations.
 *
 * @param T The type of successful result data
 */
sealed class AiResult<out T> {

    /**
     * Represents a successful AI operation.
     *
     * @property data The result data from the operation
     */
    data class Success<T>(val data: T) : AiResult<T>()

    /**
     * Represents a failed AI operation.
     *
     * @property code The error code categorizing the failure
     * @property message Human-readable error message for debugging
     */
    data class Error(val code: AiErrorCode, val message: String) : AiResult<Nothing>()

    /**
     * Whether this result is a success.
     */
    val isSuccess: Boolean get() = this is Success

    /**
     * Whether this result is an error.
     */
    val isError: Boolean get() = this is Error

    /**
     * Transforms the success value using the given function.
     * If this is an Error, returns the same Error unchanged.
     *
     * @param transform Function to apply to the success value
     * @return A new AiResult with the transformed value or the original error
     */
    inline fun <R> map(transform: (T) -> R): AiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
    }

    /**
     * Returns the success value or null if this is an error.
     */
    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> null
    }

    /**
     * Returns the success value or throws AiException if this is an error.
     *
     * @throws AiException if this is an Error
     */
    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw AiException(code, message)
    }

    /**
     * Returns the success value or the default value if this is an error.
     *
     * @param default The value to return if this is an Error
     */
    fun getOrDefault(default: @UnsafeVariance T): T = when (this) {
        is Success -> data
        is Error -> default
    }

    /**
     * Applies one of two functions depending on success or error.
     *
     * @param onSuccess Function to apply for Success
     * @param onError Function to apply for Error
     * @return The result of the applied function
     */
    inline fun <R> fold(
        onSuccess: (T) -> R,
        onError: (AiErrorCode, String) -> R
    ): R = when (this) {
        is Success -> onSuccess(data)
        is Error -> onError(code, message)
    }

    /**
     * Executes the given action if this is a Success.
     *
     * @param action Action to execute with the success value
     * @return This result unchanged for chaining
     */
    inline fun onSuccess(action: (T) -> Unit): AiResult<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Executes the given action if this is an Error.
     *
     * @param action Action to execute with the error code and message
     * @return This result unchanged for chaining
     */
    inline fun onError(action: (AiErrorCode, String) -> Unit): AiResult<T> {
        if (this is Error) action(code, message)
        return this
    }
}

/**
 * Exception thrown when [AiResult.getOrThrow] is called on an Error.
 *
 * @property code The error code from the original AiResult.Error
 */
class AiException(
    val code: AiErrorCode,
    message: String
) : Exception(message)
