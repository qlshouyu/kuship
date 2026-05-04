#!/usr/bin/env bash
# Run JUnit 5 tests inside a GraalVM native image (harden-native-tests).
#
# Counterpart to scripts/native-build.sh: where that script produces a binary,
# this one runs the test suite under `mvn -Pnative,native-test test` so we can
# detect missing reflection / resource hints before they hit production.
#
# Prerequisites:
#   - GraalVM 21 community in PATH (`native-image --version` must succeed)
#   - macOS:  sdk install java 21.0.2-graalce && sdk use java 21.0.2-graalce
#   - Linux:  https://graalvm.org/downloads, place 'native-image' on PATH
#
# Usage:
#   bash scripts/native-test.sh                  # run native tests, print summary + hint diagnostics
#   bash scripts/native-test.sh --quick          # run only NativeSmokeTest + NativeTestRuntimeHintsRegistrarTest
#
# Exit code mirrors the underlying mvn invocation. The script prints a final
# `[SUMMARY] passed=X failed=Y skipped=Z` line plus `[HINT-MISSING] <fqcn>` rows
# whenever it sees ClassNotFoundException / NoSuchMethodException / MissingResourceException
# in the surefire output.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS="${KUSHIP_MVN_SETTINGS:-/tmp/kuship-mvn-settings.xml}"

# 1. Verify GraalVM.
if ! command -v native-image >/dev/null 2>&1; then
    echo "ERROR: GraalVM 21 community not found, install via 'sdk install java 21.0.2-graalce'" >&2
    echo "       (Linux: download from https://graalvm.org and add 'native-image' to PATH)" >&2
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n1 | awk -F\" '{print $2}')
NI_VERSION=$(native-image --version 2>&1 | head -n1)
echo "[NATIVE-TEST] Java: $JAVA_VERSION"
echo "[NATIVE-TEST] native-image: $NI_VERSION"

# 2. Choose Maven settings file (aliyun mirror, falls back to user default).
MVN_ARGS=("-B" "-Pnative,native-test")
if [ -f "$SETTINGS" ]; then
    echo "[NATIVE-TEST] Using maven settings: $SETTINGS"
    MVN_ARGS+=("-s" "$SETTINGS")
else
    echo "[NATIVE-TEST] settings file $SETTINGS missing, falling back to user default"
fi

if [ "${1:-}" = "--quick" ]; then
    MVN_ARGS+=("-Dtest=NativeSmokeTest,NativeTestRuntimeHintsRegistrarTest")
fi

# 3. Run mvn test, tee output for grep below.
LOG_FILE=$(mktemp -t kuship-native-test.XXXXXX.log)
trap 'rm -f "$LOG_FILE"' EXIT

cd "$REPO_ROOT/kuship-console"
echo "[NATIVE-TEST] Running: mvn ${MVN_ARGS[*]} test"
set +e
mvn "${MVN_ARGS[@]}" test 2>&1 | tee "$LOG_FILE"
MVN_EXIT=${PIPESTATUS[0]}
set -e

# 4. Parse surefire summary line. Surefire writes lines like:
#       [INFO] Tests run: 102, Failures: 0, Errors: 0, Skipped: 0
# Pick the *last* such line (totals come at the end of each module).
SUMMARY_LINE=$(grep -E "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+" "$LOG_FILE" | tail -n1 || true)
if [ -n "$SUMMARY_LINE" ]; then
    PASSED=$(echo "$SUMMARY_LINE" | sed -E 's/.*Tests run: ([0-9]+).*/\1/')
    FAILS=$(echo "$SUMMARY_LINE" | sed -E 's/.*Failures: ([0-9]+).*/\1/')
    ERRS=$(echo "$SUMMARY_LINE" | sed -E 's/.*Errors: ([0-9]+).*/\1/')
    SKIPPED=$(echo "$SUMMARY_LINE" | sed -E 's/.*Skipped: ([0-9]+).*/\1/')
    FAILED=$((FAILS + ERRS))
    PASSED=$((PASSED - FAILED - SKIPPED))
    echo "[SUMMARY] passed=$PASSED failed=$FAILED skipped=$SKIPPED"
else
    echo "[SUMMARY] passed=0 failed=0 skipped=0  (no surefire summary detected)"
fi

# 5. Diagnose missing native hints.
#    Examples we care about:
#       java.lang.ClassNotFoundException: cn.kuship.console.modules.foo.dto.Bar
#       java.lang.NoSuchMethodException: cn.kuship.console...Foo.bar()
#       java.util.MissingResourceException: Can't find bundle for base name X
HINT_LINES=$(grep -hE "ClassNotFoundException|NoSuchMethodException|MissingResourceException" "$LOG_FILE" \
    | sed -E 's/.*(ClassNotFoundException|NoSuchMethodException): ([^ ]+).*/\2/; s/.*MissingResourceException.*name (\S+).*/\1/' \
    | sort -u || true)
if [ -n "$HINT_LINES" ]; then
    echo
    echo "[NATIVE-TEST] Detected reflection / resource gaps. Add these to NativeTestRuntimeHintsRegistrar:"
    while IFS= read -r line; do
        [ -n "$line" ] && echo "[HINT-MISSING] $line"
    done <<< "$HINT_LINES"
fi

# 6. Propagate mvn exit code to the caller (CI relies on this for status).
exit "$MVN_EXIT"
