plugins {
    id("com.android.dynamic-feature")
    kotlin("android")
}

android {
    namespace = "app.m1k3.ai.assistant.gemma"
    compileSdk = 35

    defaultConfig {
        minSdk = 27
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Reference to base app module
    implementation(project(":composeApp"))

    // Kotlin coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    // ONNX Runtime for Gemma inference
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")
}
