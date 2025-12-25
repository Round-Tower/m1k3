#!/bin/bash
set -e

echo "🔧 Building Android App Bundle (AAB)..."

# Clean previous builds
./gradlew clean

# Build debug bundle
echo "📦 Building debug bundle..."
./gradlew :composeApp:bundleDebug

# Build release bundle (if signing configured)
# echo "📦 Building release bundle..."
# ./gradlew :composeApp:bundleRelease

# Show output location
echo ""
echo "✅ Bundle build complete!"
echo ""
echo "Debug AAB location:"
echo "  app/composeApp/build/outputs/bundle/debug/composeApp-debug.aab"
echo ""
echo "To install locally, use bundletool:"
echo "  ./install_bundle.sh"
