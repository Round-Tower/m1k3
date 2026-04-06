package app.m1k3.ai.assistant.ai.ma

/**
 * MaTokenCallback - JNI-callable wrapper for Kotlin streaming callbacks.
 *
 * Kotlin lambdas (`(String) -> Unit`) are not directly callable from JNI.
 * This wrapper exposes a named Java method `onToken(String)` that the C++
 * bridge can find and invoke via GetMethodID/CallVoidMethod.
 *
 * Usage in C++:
 * ```c
 * jclass cbClass = env->GetObjectClass(callback);
 * jmethodID cbMethod = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
 * // per token:
 * jstring jPiece = env->NewStringUTF(piece_buf.c_str());
 * env->CallVoidMethod(callback, cbMethod, jPiece);
 * env->DeleteLocalRef(jPiece);
 * ```
 */
class MaTokenCallback(private val callback: (String) -> Unit) {
    /** Called by the Ma JNI bridge for each generated token piece. */
    fun onToken(piece: String) = callback(piece)
}
