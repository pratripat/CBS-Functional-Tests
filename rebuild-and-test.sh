#!/usr/bin/env bash
# Full build + test orchestration script for the CBS Functional Tests platform.
set -euo pipefail

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
info()    { echo -e "${GREEN}==>${NC} $*"; }
warning() { echo -e "${YELLOW}[!]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*"; }

# Mobile tests are ON by default — set SKIP_MOBILE=true to skip
SKIP_MOBILE="${SKIP_MOBILE:-false}"
SKIP_API="${SKIP_API:-false}"
APK_PATH=""

# ── Step 1: Build service JARs ────────────────────────────────────────────────
info "Step 1/5: Building microservice JARs..."
mvn clean package -DskipTests \
    -pl account-service,funds-transfer-service,transaction-history-service,channel-service \
    -am -q
info "Service JARs built successfully"

# ── Step 2: Build Docker images ───────────────────────────────────────────────
info "Step 2/5: Building Docker images (no-cache)..."
docker build --no-cache -t banking/account-service:latest             ./account-service
docker build --no-cache -t banking/funds-transfer-service:latest      ./funds-transfer-service
docker build --no-cache -t banking/transaction-history-service:latest ./transaction-history-service
docker build --no-cache -t banking/channel-service:latest             ./channel-service
info "Docker images built successfully"

# ── Step 3: Build Android APK ─────────────────────────────────────────────────
if [ "$SKIP_MOBILE" = "true" ]; then
    warning "Step 3/5: Skipping Android APK build (SKIP_MOBILE=true)"
else
    info "Step 3/5: Building Android APK..."

    # Load SDKMAN — temporarily disable strict unbound-var check
    export SDKMAN_DIR="$HOME/.sdkman"
    set +u
    [[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"
    set -u

    export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

    if [ -d "banking-mobile-app" ]; then
        MOBILE_APP_DIR="banking-mobile-app"
    elif [ -d "../banking-mobile-app" ]; then
        MOBILE_APP_DIR="../banking-mobile-app"
    else
        error "banking-mobile-app not found inside or alongside this repo."
        exit 1
    fi

    info "Found mobile app at: $(realpath "$MOBILE_APP_DIR")"
    cd "$MOBILE_APP_DIR"

    echo "Current dir: $(pwd)"
    echo "gradlew: $(ls gradlew 2>/dev/null || echo NOT FOUND)"
    echo "ANDROID_HOME: $ANDROID_HOME"

    ./gradlew assembleDebug \
        -PBASE_URL="http://host.docker.internal:8080" \
        --no-daemon -q

    APK_PATH="$(pwd)/app/build/outputs/apk/debug/app-debug.apk"
    cd - > /dev/null

    if [ ! -f "$APK_PATH" ]; then
        error "APK not found at $APK_PATH"
        exit 1
    fi

    APK_SIZE=$(du -sh "$APK_PATH" | cut -f1)
    info "Android APK built: $APK_PATH ($APK_SIZE)"
fi

# ── Step 4: Run API functional tests ──────────────────────────────────────────
if [ "$SKIP_API" = "true" ]; then
    warning "Step 4/5: Skipping API functional tests (SKIP_API=true)"
else
    info "Step 4/5: Running API functional tests..."
    mvn verify -pl banking-functional-tests -am -B
    info "API functional tests complete"
fi

# ── Step 5: Run mobile functional tests ───────────────────────────────────────
if [ "$SKIP_MOBILE" = "true" ]; then
    warning "Step 5/5: Skipping mobile functional tests (SKIP_MOBILE=true)"
    warning "To run mobile tests: SKIP_MOBILE=false ./rebuild-and-test.sh"
else
    info "Step 5/5: Running mobile functional tests..."
    warning "Android emulator boot takes 5-10 min on first run — this is normal"

    mvn verify -pl mobile-functional-tests -am -B \
        -Dapk.path="$APK_PATH"

    info "Mobile functional tests complete"
fi

echo ""
info "All done! ✓"
echo ""
echo "  Run everything:          ./rebuild-and-test.sh"
echo "  Run API tests only:      SKIP_MOBILE=true ./rebuild-and-test.sh"
echo "  Run mobile tests only:   SKIP_API=true ./rebuild-and-test.sh"