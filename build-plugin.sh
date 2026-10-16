#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================================"
echo "  Service Invocation Monitor - IDEA Plugin Packaging (Bash)"
echo "============================================================"

# Detect JBR in Gradle cache if available
JBR_PATH=$(find "$HOME/.gradle/caches" -type d -name "jbr" 2>/dev/null | head -n 1)
EXTRA_ARGS=()
if [ -n "$JBR_PATH" ]; then
    echo "[INFO] Detected IntelliJ Platform JBR toolchain: $JBR_PATH"
    EXTRA_ARGS+=("-Dorg.gradle.java.installations.paths=$JBR_PATH")
fi

echo "[EXEC] Running ./gradlew :monitor-plugin:packagePlugin -x test ${EXTRA_ARGS[*]}"
./gradlew :monitor-plugin:packagePlugin -x test "${EXTRA_ARGS[@]}"

echo "============================================================"
echo "  Packaging completed! Check dist/ directory for the zip artifact."
echo "============================================================"
