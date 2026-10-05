#!/usr/bin/env bash
set -e

cd "$(dirname "${BASH_SOURCE[0]}")/.."

export PORT="${PORT:-8080}"
export DB_PORT="${DB_PORT:-5433}"

echo "Starting PostgreSQL on port $DB_PORT..."
docker compose up -d --wait db

echo "Starting Traffic Rule Engine on http://localhost:$PORT"
echo "(stop with Ctrl+C; delete the database with: docker compose down -v)"
./mvnw spring-boot:run
