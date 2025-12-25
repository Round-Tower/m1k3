# Android App Bundle (AAB) Build Guide

## Overview
間 AI uses Android App Bundles as the default build format for optimized distribution.

## Benefits
- **15-20% smaller downloads** on average (vs universal APK)
- **Device-specific optimization** (only downloads needed resources)
- **Dynamic feature delivery** (gemmaEmbedding module on-demand)
- **Google Play requirement** for apps >150 MB

## Quick Start

### Build Bundle
```bash
./build_bundle.sh
```

Output: `composeApp/build/outputs/bundle/debug/composeApp-debug.aab`

### Install Locally
```bash
./install_bundle.sh
```

This downloads bundletool and installs the bundle to a connected device.

### Compare Sizes
```bash
./compare_sizes.sh
```

Shows APK vs AAB size comparison.

## Manual Commands

### Build Bundle
```bash
./gradlew :composeApp:bundleDebug
./gradlew :composeApp:bundleRelease
```

### Install with bundletool
```bash
# Download bundletool
curl -L -o bundletool.jar \
  https://github.com/google/bundletool/releases/download/1.17.1/bundletool-all-1.17.1.jar

# Generate device-specific APKs
java -jar bundletool.jar build-apks \
  --bundle=composeApp/build/outputs/bundle/debug/composeApp-debug.aab \
  --output=app.apks \
  --local-testing

# Install to connected device
java -jar bundletool.jar install-apks --apks=app.apks
```

## Dynamic Feature Module

### gemmaEmbedding Module
- **Type:** On-demand dynamic feature
- **Title:** "Advanced Semantic Search"
- **Size:** ~180 MB (Gemma 300M ONNX model)
- **Delivery:** Downloaded when user requests advanced embeddings

### Testing Dynamic Feature
```bash
# Install with local testing flag
java -jar bundletool.jar build-apks \
  --bundle=app.aab \
  --output=app.apks \
  --local-testing

# Use Play Core API in app to request module
SplitInstallManager.startInstall(request)
```

## Configuration

### Bundle Splits
Configured in `composeApp/build.gradle.kts`:

- **ABI splits:** Enabled (arm64-v8a, armeabi-v7a, x86, x86_64)
- **Density splits:** Enabled (hdpi, xhdpi, xxhdpi, xxxhdpi)
- **Language splits:** Disabled (all languages in base)

### Properties
Configured in `gradle.properties`:

- `android.bundle.enableUncompressedNativeLibs=true`: Faster installs on Android 6.0+
- `android.enableR8.fullMode=true`: More aggressive code shrinking

## Troubleshooting

### Error: "Title for module is missing"
**Solution:** Dynamic feature module titles must be in base module's `strings.xml`

```xml
<!-- composeApp/src/androidMain/res/values/strings.xml -->
<string name="gemma_embedding_title">Advanced Semantic Search</string>
```

### Error: "Device not found"
**Solution:** Connect device via ADB and enable USB debugging

```bash
adb devices  # Check device is connected
```

### Bundle too large
**Solution:** Enable more aggressive splits and R8 optimization

```kotlin
bundle {
    language.enableSplit = true  # Enable language splits
}
buildTypes {
    release {
        isMinifyEnabled = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
}
```

## Size Comparison

### Current Build (Debug)
- **Universal APK:** Variable (device-dependent)
- **App Bundle:** Optimized per device configuration
- **User download:** Only needed resources (architecture, density, language)

### Production (Release + Optimization)
- **With R8 + ProGuard:** Additional 30-40% reduction
- **With language splits:** Additional 10-15% reduction
- **Expected final size:** <200 MB (per project requirements)

## References
- [Android App Bundles Official Guide](https://developer.android.com/guide/app-bundle)
- [bundletool Documentation](https://developer.android.com/tools/bundletool)
- [Dynamic Feature Delivery](https://developer.android.com/guide/app-bundle/dynamic-delivery)
