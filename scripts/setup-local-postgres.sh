#!/usr/bin/env bash
# Создаёт пользователя и БД ozbio в локальном PostgreSQL (порт 5432).
# Суперпользователь: обычно ваш логин macOS (не postgres). Скрипт пробует автоматически.
set -euo pipefail

PSQL_SUPERUSER="${PSQL_SUPERUSER:-}"
PSQL_HOST="${PSQL_HOST:-127.0.0.1}"
PSQL_PORT="${PSQL_PORT:-5432}"
PSQL_DATABASE="${PSQL_DATABASE:-postgres}"

if ! command -v psql >/dev/null 2>&1; then
  echo "ОШИБКА: нужен psql (PostgreSQL client)" >&2
  exit 1
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
  echo "ОШИБКА: не удалось подключиться к PostgreSQL на ${PSQL_HOST}:${PSQL_PORT}." >&2
  echo "  Убедитесь, что сервер запущен." >&2
  echo "  Пример: PSQL_SUPERUSER=$(whoami) $0" >&2
  echo "  Или Docker: docker compose up -d && mvn spring-boot:run -Dspring-boot.run.profiles=docker" >&2
  exit 1
fi

echo "Настройка PostgreSQL на ${PSQL_HOST}:${PSQL_PORT} (суперпользователь: $PSQL_SUPERUSER)"

if ! run_psql -tc "SELECT 1 FROM pg_roles WHERE rolname = 'ozbio'" | grep -q 1; then
  echo "Создаём пользователя ozbio..."
  run_psql -c "CREATE USER ozbio WITH PASSWORD 'ozbio';"
else
  echo "Пользователь ozbio уже существует."
  run_psql -c "ALTER USER ozbio WITH PASSWORD 'ozbio';"
fi

if ! run_psql -tc "SELECT 1 FROM pg_database WHERE datname = 'ozbio'" | grep -q 1; then
  echo "Создаём базу ozbio..."
  run_psql -c "CREATE DATABASE ozbio OWNER ozbio;"
else
  echo "База ozbio уже существует."
fi

run_psql -c "GRANT ALL PRIVILEGES ON DATABASE ozbio TO ozbio;"

echo ""
echo "OK. Запуск приложения:"
echo "  mvn spring-boot:run"
echo ""
echo "URL: jdbc:postgresql://${PSQL_HOST}:${PSQL_PORT}/ozbio  user=ozbio"
