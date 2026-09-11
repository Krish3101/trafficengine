#!/usr/bin/env bash

# Resolve project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "Stopping any running instance of Traffic Engine..."
pkill -f "trafficengine" 2>/dev/null || true

# If port 8080 is still in use by another java process, stop it
PID=$(lsof -ti :8080 2>/dev/null || true)
if [ -n "$PID" ]; then
  echo "Terminating process on port 8080 (PID: $PID)..."
  kill -9 "$PID" 2>/dev/null || true
fi

echo "Cleaning build artifacts and temporary files..."
if [ -x "./mvnw" ]; then
  ./mvnw clean -q 2>/dev/null || rm -rf target/
else
  rm -rf target/
fi

rm -rf target/
rm -f .DS_Store
rm -f *.log

echo ""
echo "Reset complete. All processes stopped, build artifacts cleared, and in-memory state reset."
echo "You can now start the application afresh with ./scripts/start.sh"
