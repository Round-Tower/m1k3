# Qwen2.5-Coder Dynamic Feature Module - ProGuard Rules

# ONNX Runtime classes
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep model inference classes (prevent obfuscation)
-keep class com.m1k3.codingmodule.** { *; }

# Keep serialization classes if using JSON
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# JNI libraries
-keep class * {
    native <methods>;
}
