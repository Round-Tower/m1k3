import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    // JVM target for desktop testing (optional)
    jvm()

    // Note: JS and WASM targets removed - not needed for mobile app
    // and incompatible with SQLDelight database dependency

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)

            // M1K3 AI - Android-specific dependencies
            implementation(libs.sqldelight.driver.android)
            // TODO: Re-enable SQLCipher once we resolve native library setup
            // implementation(libs.sqlcipher)
            implementation(libs.androidx.security.crypto)
            implementation(libs.onnxruntime.android)

            // CameraX
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)

            // ML Kit
            implementation(libs.mlkit.vision)
            implementation(libs.mlkit.text.recognition)
            implementation(libs.mlkit.objectdetection)
            implementation(libs.mlkit.image.labeling)

            // Google Fonts for custom typography
            implementation(libs.compose.ui.text.googlefonts)

            // SceneView for 3D avatar rendering
            implementation(libs.sceneview)

            // Coding module for code generation
            implementation(project(":codingModule"))

            // Play Core for dynamic feature delivery
            implementation("com.google.android.play:core:1.10.3")
            implementation("com.google.android.play:core-ktx:1.8.1")

            // TODO: JVector for HNSW vector similarity search (not yet in Maven Central)
            // Using linear search fallback for now (fine for <10K vectors)
            // implementation("io.github.jbellis:jvector-base:1.0.0")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(projects.shared)

            // 間 AI - Common dependencies
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        iosMain.dependencies {
            // 間 AI - iOS-specific dependencies
            implementation(libs.sqldelight.driver.native)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)

            // 間 AI - JVM/Desktop dependencies
            implementation(libs.sqldelight.driver.sqlite)
        }
    }
}

android {
    namespace = "app.m1k3.ai.assistant"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.m1k3.ai.assistant"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        // Instrumented test runner
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Dynamic feature modules
    dynamicFeatures += setOf(":gemmaEmbedding")
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }

    // App Bundle configuration for optimized distribution
    bundle {
        language {
            // Disable language splits to include all languages in base
            // (Can enable later for further size optimization)
            enableSplit = false
        }
        density {
            // Enable density splits for screen-specific resources
            enableSplit = true
        }
        abi {
            // Enable ABI splits for architecture-specific native libraries
            enableSplit = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)

    // LeakCanary for memory leak detection (debug only)
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")

    // 間 AI - Testing dependencies
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
}

compose.desktop {
    application {
        mainClass = "app.m1k3.ai.assistant.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "app.m1k3.ai.assistant"
            packageVersion = "1.0.0"
        }
    }
}

// 間 AI - SQLDelight Configuration
sqldelight {
    databases {
        create("MaDatabase") {
            packageName.set("app.m1k3.ai.assistant.database")
            // Schema will be defined in src/commonMain/sqldelight
            schemaOutputDirectory.set(file("build/dbs"))
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:2.0.2")
        }
    }
}
