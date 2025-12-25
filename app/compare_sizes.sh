#!/bin/bash
set -e

echo "📊 Comparing APK vs AAB sizes..."
echo ""

APK_PATH="composeApp/build/outputs/apk/debug/composeApp-debug.apk"
AAB_PATH="composeApp/build/outputs/bundle/debug/composeApp-debug.aab"
APKS_PATH="composeApp/build/outputs/bundle/debug/composeApp-debug.apks"

# Build both if needed
if [ ! -f "$APK_PATH" ]; then
    echo "🔨 Building APK..."
    ./gradlew :composeApp:assembleDebug
fi

if [ ! -f "$AAB_PATH" ]; then
    echo "🔨 Building AAB..."
    ./gradlew :composeApp:bundleDebug
fi

# Get sizes
APK_SIZE=$(stat -f%z "$APK_PATH" 2>/dev/null || stat -c%s "$APK_PATH")
AAB_SIZE=$(stat -f%z "$AAB_PATH" 2>/dev/null || stat -c%s "$AAB_PATH")

# Calculate savings
SAVINGS=$((APK_SIZE - AAB_SIZE))
PERCENT=$(awk "BEGIN {printf \"%.1f\", ($SAVINGS / $APK_SIZE) * 100}")

# Format sizes
APK_MB=$(awk "BEGIN {printf \"%.2f\", $APK_SIZE / 1048576}")
AAB_MB=$(awk "BEGIN {printf \"%.2f\", $AAB_SIZE / 1048576}")
SAVINGS_MB=$(awk "BEGIN {printf \"%.2f\", $SAVINGS / 1048576}")

echo "📦 Build Outputs:"
echo "  Universal APK: ${APK_MB} MB"
echo "  App Bundle:    ${AAB_MB} MB"
echo ""
echo "💾 Size Savings:"
echo "  Absolute: ${SAVINGS_MB} MB"
echo "  Percentage: ${PERCENT}%"
echo ""
echo "Note: Actual device downloads will be smaller due to split APKs"
echo "      (only downloads resources for specific device config)"
