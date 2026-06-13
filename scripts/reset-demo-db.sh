#!/usr/bin/env bash
# Сброс демо-данных в схеме APP_DB_SCHEMA (по умолчанию testing).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/db-env.sh
source "$ROOT/scripts/lib/db-env.sh"

if [[ "${APP_DB_SCHEMA}" == "production" ]]; then
  echo "ОШИБКА: reset-demo-db не для production. Используйте APP_DB_SCHEMA=testing" >&2
  exit 1
fi

clear_catalog_sql() {
  cat <<SQL
SET search_path TO ${APP_DB_SCHEMA};
DELETE FROM assignment;
DELETE FROM order_part_task;
DELETE FROM order_part;
DELETE FROM schedule_order;
DELETE FROM part_task;
DELETE FROM part_definition;
DELETE FROM machine_capability;
DELETE FROM machine;
DELETE FROM machine_group;
DELETE FROM factory_state;
SQL
}

if [[ "${1:-}" == "--docker" ]] || docker compose -f "$ROOT/docker-compose.yml" ps --status running postgres 2>/dev/null | grep -q postgres; then
  if ! docker compose -f "$ROOT/docker-compose.yml" ps --status running postgres 2>/dev/null | grep -q postgres; then
    echo "Запустите PostgreSQL: docker compose up -d" >&2
    exit 1
  fi
  clear_catalog_sql | docker compose -f "$ROOT/docker-compose.yml" exec -T postgres psql -U ozbio -d ozbio -v ON_ERROR_STOP=1
  "$ROOT/scripts/seed-demo-catalog.sh" --docker
else
  if ! command -v psql >/dev/null 2>&1; then
    echo "ОШИБКА: нужен psql" >&2
    exit 1
  fi
  clear_catalog_sql | psql_exec
  "$ROOT/scripts/seed-demo-catalog.sh"
fi

echo "OK: схема ${APP_DB_SCHEMA} сброшена к демо-каталогу (без заказов)."
