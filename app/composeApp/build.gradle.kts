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
            baseName = "M1K3"
            isStatic = true
        }
    }

    // JVM target for desktop testing (optional)
    jvm()

    // Note: JS and WASM targets removed - not needed for mobile app

    sourceSets {
        all {
            languageSettings {
                // Enable experimental datetime APIs to suppress warnings
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlinx.datetime.ExperimentalDateTimeApi")
            }
        }

        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)

            // M1K3 AI - Android-specific dependencies
            implementation(libs.sqldelight.driver.android)
            // TODO: Re-enable SQLCipher once we resolve native library setup
            // implementation(libs.sqlcipher)
            implementation(libs.androidx.security.crypto)

            // Inference engines
            implementation(libs.onnxruntime.android)  // Keep for Gemma3Engine (Phase 4)

            // CameraX
            implementation(libs.camerax.core)
            implementation(libs.camerax.camera2)
            implementation(libs.camerax.lifecycle)
            implementation(libs.camerax.view)

            // ML Kit Vision
            implementation(libs.mlkit.vision)
            implementation(libs.mlkit.text.recognition)
            implementation(libs.mlkit.objectdetection)
            implementation(libs.mlkit.image.labeling)

            // ML Kit GenAI (Gemini Nano on-device)
            implementation(libs.mlkit.genai.prompt)
            implementation(libs.mlkit.genai.summarization)

            // Google Fonts for custom typography
            implementation(libs.compose.ui.text.googlefonts)

            // SceneView for 3D avatar rendering
            implementation(libs.sceneview)

            // Coding module for code generation
//            implementation(project(":codingModule"))

            // Play Core for dynamic feature delivery
            implementation("com.google.android.play:core:1.10.3")
            implementation("com.google.android.play:core-ktx:1.8.1")

            // Koin Android
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.compose)

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
            implementation(libs.androidx.navigation.compose)
            implementation(projects.shared)

            // Common dependencies
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            // Llamatik - KMP llama.cpp binding for GGUF models
            implementation("com.llamatik:library:0.13.0")

            // WebView for Three.js 3D avatar rendering
            implementation("io.github.kevinnzou:compose-webview-multiplatform:2.0.3")

            // Kermit - Multiplatform logging
            implementation(libs.kermit)

            // Koin - Dependency injection
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.espresso.core)
            implementation(libs.koin.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.test)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        jvmTest.dependencies {
            // JDBC SQLite driver for JVM unit tests only (in-memory database)
            implementation(libs.sqldelight.driver.jdbc)
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
//    dynamicFeatures += setOf(":gemmaEmbedding")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true

             proguardFiles(
                 getDefaultProguardFile("proguard-android-optimize.txt"),
                 "proguard-rules.pro"
             )
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
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    // Testing dependencies
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.sqldelight.driver.jdbc) // JDBC driver for unit tests (JVM)
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

// SQLDelight Configuration
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

// ============================================================================
// Web Avatar Integration - Asset Bundling
// ============================================================================

/**
 * Build web-avatar with Vite (production bundle)
 *
 * This task:
 * 1. Runs `npm install` in src/web-avatar/ (if needed)
 * 2. Runs `npm run build` to create optimized dist/
 * 3. Outputs to: src/web-avatar/dist/
 */
/**
 * Install npm dependencies for web-avatar
 */
tasks.register<Exec>("installWebAvatarDeps") {
    group = "web-avatar"
    description = "Install npm dependencies for web-avatar"

    workingDir(file("../../src/web-avatar"))
    commandLine("npm", "install")

    onlyIf {
        !File(file("../../src/web-avatar"), "node_modules").exists()
    }
}

/**
 * Build web-avatar production bundle
 */
tasks.register<Exec>("buildWebAvatar") {
    group = "web-avatar"
    description = "Build web-avatar production bundle with Vite"

    dependsOn("installWebAvatarDeps")

    workingDir(file("../../src/web-avatar"))
    commandLine("npm", "run", "build:app")

    inputs.files(
        fileTree("../../src/web-avatar/src"),
        file("../../src/web-avatar/index.html"),
        file("../../src/web-avatar/package.json"),
        file("../../src/web-avatar/vite.config.app.ts")
    )
    outputs.dir("../../src/web-avatar/dist-app")
}

/**
 * Copy web-avatar dist to Android assets
 *
 * This task:
 * 1. Copies dist/ → composeApp/src/androidMain/assets/web-avatar/
 * 2. Includes index.html, JS, CSS, and bundled models
 * 3. Runs automatically before Android build
 */
tasks.register<Copy>("copyWebAvatarToAndroid") {
    group = "web-avatar"
    description = "Copy web-avatar dist to Android assets"

    dependsOn("buildWebAvatar")

    from("../../src/web-avatar/dist-app") {
        include("**/*")
    }
    into("src/androidMain/assets/web-avatar")

    doFirst {
        println("📋 Copying web-avatar to Android assets...")
    }

    doLast {
        println("✅ Web avatar bundled for Android")
    }
}

/**
 * Copy web-avatar dist to iOS resources
 *
 * This task:
 * 1. Copies dist/ → ../iosApp/iosApp/Resources/web-avatar/
 * 2. Xcode will include this in app bundle
 * 3. Manual step: Add folder to Xcode project (blue folder reference)
 */
tasks.register<Copy>("copyWebAvatarToIOS") {
    group = "web-avatar"
    description = "Copy web-avatar dist to iOS resources (requires Xcode setup)"

    dependsOn("buildWebAvatar")

    from("../../src/web-avatar/dist-app") {
        include("**/*")
    }
    into("../iosApp/iosApp/Resources/web-avatar")

    doFirst {
        println("📋 Copying web-avatar to iOS resources...")
    }

    doLast {
        println("✅ Web avatar copied for iOS")
        println("⚠️  MANUAL STEP: Add Resources/web-avatar/ to Xcode project as folder reference (blue folder)")
    }
}

/**
 * Bundle web-avatar for all platforms
 */
tasks.register("bundleWebAvatar") {
    group = "web-avatar"
    description = "Build and bundle web-avatar for Android and iOS"

    dependsOn("copyWebAvatarToAndroid", "copyWebAvatarToIOS")
}

/**
 * Auto-bundle web-avatar before Android build
 */
tasks.named("preBuild") {
    dependsOn("copyWebAvatarToAndroid")
}
