#!/usr/bin/env bash
# Сброс демо-данных в PostgreSQL (пересоздаёт каталог, очищает заказы).
# Требуется: docker compose up -d
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SQL="$ROOT/src/main/resources/db/changelog/changes/002-seed-demo-catalog.sql"

if ! docker compose -f "$ROOT/docker-compose.yml" ps --status running postgres 2>/dev/null | grep -q postgres; then
  echo "Запустите PostgreSQL: docker compose up -d" >&2
  exit 1
fi

docker compose -f "$ROOT/docker-compose.yml" exec -T postgres psql -U ozbio -d ozbio <<'SQL'
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

docker compose -f "$ROOT/docker-compose.yml" exec -T postgres psql -U ozbio -d ozbio -f - <"$SQL"

echo "OK: PostgreSQL сброшен к демо-каталогу (без заказов)."
echo "Перезапустите приложение: mvn spring-boot:run"
