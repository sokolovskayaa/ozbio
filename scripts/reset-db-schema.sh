#!/usr/bin/env bash
# Пересоздаёт схему testing/production (нужен суперпользователь PostgreSQL).
# После: mvn spring-boot:run — Liquibase накатит DDL и сиды заново.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PSQL_SUPERUSER="${PSQL_SUPERUSER:-}"
PSQL_HOST="${PSQL_HOST:-127.0.0.1}"
PSQL_PORT="${PSQL_PORT:-5432}"
PSQL_DATABASE="${PSQL_DATABASE:-postgres}"
SCHEMA="${1:-${APP_DB_SCHEMA:-testing}}"

if [[ "$SCHEMA" != "testing" && "$SCHEMA" != "production" ]]; then
  echo "ОШИБКА: схема должна быть testing или production" >&2
  exit 1
fi

if [[ "$SCHEMA" == "production" ]]; then
  echo "ВНИМАНИЕ: пересоздаётся схема production." >&2
fi

run_psql() {
  psql -h "$PSQL_HOST" -p "$PSQL_PORT" -U "$PSQL_SUPERUSER" -d "$PSQL_DATABASE" -v ON_ERROR_STOP=1 "$@"
}

detect_superuser() {
  local candidate
  for candidate in "$PSQL_SUPERUSER" postgres "$(whoami)"; do
    if [[ -z "$candidate" ]]; then
      continue
    fi
    if psql -h "$PSQL_HOST" -p "$PSQL_PORT" -U "$candidate" -d "$PSQL_DATABASE" -tc "SELECT 1" >/dev/null 2>&1; then
      PSQL_SUPERUSER="$candidate"
      return 0
    fi
  done
  return 1
}

if ! detect_superuser; then
  echo "ОШИБКА: нужен суперпользователь. Пример: PSQL_SUPERUSER=$(whoami) $0 $SCHEMA" >&2
  exit 1
fi

run_psql -d ozbio <<SQL
DROP SCHEMA IF EXISTS ${SCHEMA} CASCADE;
CREATE SCHEMA ${SCHEMA} AUTHORIZATION ozbio;
GRANT ALL ON SCHEMA ${SCHEMA} TO ozbio;
ALTER DEFAULT PRIVILEGES IN SCHEMA ${SCHEMA} GRANT ALL ON TABLES TO ozbio;
SQL

echo "OK: схема ${SCHEMA} пересоздана (owner: ozbio). Запустите: mvn spring-boot:run"
