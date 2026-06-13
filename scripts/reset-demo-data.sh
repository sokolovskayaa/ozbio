#!/usr/bin/env bash
# Сброс JSON-снимка (legacy / тесты). Основное хранилище — PostgreSQL: ./scripts/reset-demo-db.sh
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cp "$ROOT/data/schedule.json.example" "$ROOT/data/schedule.json"
echo "OK: data/schedule.json сброшен из schedule.json.example"
echo "Для PostgreSQL: docker compose up -d && ./scripts/reset-demo-db.sh"
