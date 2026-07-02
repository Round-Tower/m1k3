#!/bin/bash

# M1K3 - WebView Avatar Integration Verification Script
# Checks that all Phase 1 components are in place

# Change to project root
cd "$(dirname "$0")/.."

echo "🔍 M1K3 WebView Avatar Integration - Verification"
echo "=================================================="
echo "📁 Working directory: $(pwd)"
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Counters
PASS=0
FAIL=0
WARN=0

check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}✗${NC} $2 (missing: $1)"
        FAIL=$((FAIL + 1))
    fi
}

check_dir() {
    if [ -d "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
        PASS=$((PASS + 1))
    else
        echo -e "${YELLOW}⚠${NC} $2 (missing: $1)"
        WARN=$((WARN + 1))
    fi
}

check_gradle_task() {
    if grep -q "tasks.register.*\"$1\"" "$2"; then
        echo -e "${GREEN}✓${NC} Gradle task: $1"
        PASS=$((PASS + 1))
    else
        echo -e "${RED}✗${NC} Gradle task: $1 (not found in $2)"
        FAIL=$((FAIL + 1))
    fi
}

echo "📂 Checking Core Files..."
check_file "app/composeApp/src/commonMain/kotlin/app/m1k3/ai/assistant/avatar/webview/AvatarWebViewScreen.kt" "Common WebView declaration"
check_file "app/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/avatar/webview/AvatarWebViewScreen.android.kt" "Android WebView implementation"
check_file "app/composeApp/src/iosMain/kotlin/app/m1k3/ai/assistant/avatar/webview/AvatarWebViewScreen.ios.kt" "iOS WKWebView implementation"
check_file "app/composeApp/src/androidMain/kotlin/app/m1k3/ai/assistant/avatar/webview/AvatarWebViewDemo.kt" "Android demo screen"

echo ""
echo "📦 Checking Build System..."
check_file "src/web-avatar/vite.config.app.ts" "Vite app config"
check_gradle_task "buildWebAvatar" "app/composeApp/build.gradle.kts"
check_gradle_task "copyWebAvatarToAndroid" "app/composeApp/build.gradle.kts"
check_gradle_task "copyWebAvatarToIOS" "app/composeApp/build.gradle.kts"
check_gradle_task "bundleWebAvatar" "app/composeApp/build.gradle.kts"

echo ""
echo "🌐 Checking Web Assets..."
check_dir "src/web-avatar/dist-app" "Web avatar built (dist-app/)"
check_dir "app/composeApp/src/androidMain/assets/web-avatar" "Android assets bundled"
check_file "app/composeApp/src/androidMain/assets/web-avatar/index.html" "WebView entry point (index.html)"

echo ""
echo "📚 Checking Documentation..."
check_file "app/docs/WEBVIEW_AVATAR_INTEGRATION.md" "Integration documentation"

echo ""
echo "=================================================="
echo -e "Results: ${GREEN}${PASS} passed${NC}, ${RED}${FAIL} failed${NC}, ${YELLOW}${WARN} warnings${NC}"
echo ""

if [ $FAIL -eq 0 ]; then
    echo -e "${GREEN}✅ Phase 1 WebView integration is complete!${NC}"
    echo ""
    echo "Next steps:"
    echo "1. Build and install: cd app && ./gradlew :composeApp:installDebug"
    echo "2. Add AvatarWebViewDemoScreen to NavHost"
    echo "3. Navigate to 'avatar-webview-demo' route"
    echo "4. Test emotion buttons and shader effects"
    echo ""
    echo "See app/docs/WEBVIEW_AVATAR_INTEGRATION.md for details."
    exit 0
else
    echo -e "${RED}❌ Phase 1 integration incomplete. Fix errors above.${NC}"
    exit 1
fi
