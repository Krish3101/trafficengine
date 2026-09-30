#!/usr/bin/env bash
set -e

# Resolve project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_ROOT"

echo "Starting PostgreSQL..."
docker compose up -d --wait db

echo "Starting Traffic Engine..."
echo "App will be available at http://localhost:8080"
echo ""

./mvnw spring-boot:run
