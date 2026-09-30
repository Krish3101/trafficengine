#!/usr/bin/env bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")/.."

# Stop ./scripts/start.sh first (Ctrl+C). This deletes the database and the build output.
docker compose down -v
rm -rf target/
echo "Reset done. ./scripts/start.sh starts again with an empty database."
