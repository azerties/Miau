#!/usr/bin/env bash
set -euo pipefail

LOG="${CI_BUILD_LOG:-build.log}"

print_failure_digest() {
  echo "::error::Gradle build failed (exit $1)."
  echo ""
  echo "--- Failure digest (no stack traces) ---"
  grep -E '^FAILURE:|^> Task :[^ ]+ FAILED|^BUILD FAILED|What went wrong:|Execution failed for task|^\* Try:|format violations:|Run .gradlew|spotlessJavaCheck|had format violations' "$LOG" || true
  if grep -q 'format violations' "$LOG" 2>/dev/null; then
    echo ""
    echo "--- Spotless diff (if any) ---"
    awk '/The following files had format violations:/{f=1} f{print} /^Run .gradlew/{exit}' "$LOG" | head -80
  fi
  echo ""
  echo "--- Last tasks (filtered) ---"
  grep -E '^> Task |^BUILD SUCCESSFUL|^BUILD FAILED$|warning: .*\.java:|error: .*\.java:' "$LOG" \
    | grep -v 'IllegalArgumentException' \
    | tail -20
  echo ""
  echo "Full log (incl. Mixin AP noise): artifact gradle-build-log / $LOG"
}

{
  echo "=== spotlessApply (before compile) ==="
  ./gradlew spotlessApply --no-daemon
  echo "=== build ==="
  ./gradlew build --no-daemon
} >"$LOG" 2>&1 || {
  code=$?
  print_failure_digest "$code"
  exit "$code"
}

echo "Build OK. Task summary:"
grep -E '^> Task |BUILD SUCCESSFUL' "$LOG" | tail -25