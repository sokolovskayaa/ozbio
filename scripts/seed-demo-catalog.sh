#!/usr/bin/env bash
# Загрузка демо-каталога (станки, детали) в схему APP_DB_SCHEMA (по умолчанию testing).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SQL="$ROOT/src/main/resources/db/seed/demo-catalog.sql"
# shellcheck source=lib/db-env.sh
source "$ROOT/scripts/lib/db-env.sh"

if [[ "${APP_DB_SCHEMA}" == "production" ]]; then
  echo "ОШИБКА: демо-каталог не загружается в production. Используйте APP_DB_SCHEMA=testing" >&2
  exit 1
fi

if [[ "${1:-}" == "--docker" ]]; then
  if ! docker compose -f "$ROOT/docker-compose.yml" ps --status running postgres 2>/dev/null | grep -q postgres; then
    echo "Запустите PostgreSQL: docker compose up -d" >&2
    exit 1
  fi
  {
    echo "SET search_path TO ${APP_DB_SCHEMA};"
    cat "$SQL"
  } | docker compose -f "$ROOT/docker-compose.yml" exec -T postgres psql -U ozbio -d ozbio -v ON_ERROR_STOP=1
  echo "OK: демо-каталог загружен (Docker, схема ${APP_DB_SCHEMA})."
  exit 0
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "ОШИБКА: нужен psql" >&2
  exit 1
fi

{
  echo "SET search_path TO ${APP_DB_SCHEMA};"
  cat "$SQL"
} | psql_exec
echo "OK: демо-каталог загружен (${PSQL_HOST}:${PSQL_PORT}/${PSQL_DB}, схема ${APP_DB_SCHEMA})."
