#!/bin/bash
set -e

# Configuration
AAB_PATH="composeApp/build/outputs/bundle/debug/composeApp-debug.aab"
APKS_PATH="composeApp/build/outputs/bundle/debug/composeApp-debug.apks"
BUNDLETOOL_VERSION="1.17.1"
BUNDLETOOL_JAR="bundletool-all-${BUNDLETOOL_VERSION}.jar"

echo "📦 Installing Android App Bundle to connected device..."

# Download bundletool if not present
if [ ! -f "$BUNDLETOOL_JAR" ]; then
    echo "⬇️  Downloading bundletool ${BUNDLETOOL_VERSION}..."
    curl -L -o "$BUNDLETOOL_JAR" \
        "https://github.com/google/bundletool/releases/download/${BUNDLETOOL_VERSION}/${BUNDLETOOL_JAR}"
fi

# Check if AAB exists
if [ ! -f "$AAB_PATH" ]; then
    echo "❌ Bundle not found at: $AAB_PATH"
    echo "Run ./build_bundle.sh first"
    exit 1
fi

# Generate APKs from bundle
echo "🔨 Generating APKs from bundle..."
java -jar "$BUNDLETOOL_JAR" build-apks \
    --bundle="$AAB_PATH" \
    --output="$APKS_PATH" \
    --local-testing \
    --overwrite

# Install to connected device
echo "📲 Installing to device..."
java -jar "$BUNDLETOOL_JAR" install-apks \
    --apks="$APKS_PATH"

echo ""
echo "✅ Installation complete!"
echo ""
echo "Bundle size comparison:"
ls -lh "$AAB_PATH" | awk '{print "  AAB: " $5}'
ls -lh composeApp/build/outputs/apk/debug/composeApp-debug.apk 2>/dev/null | awk '{print "  APK: " $5}' || echo "  APK: (not built)"
