#!/usr/bin/env bash
# Создаёт пользователя и БД ozbio в локальном PostgreSQL (порт 5432).
set -euo pipefail

PGHOST="${PGHOST:-127.0.0.1}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-$(whoami)}"
DB_USER="${DB_USER:-ozbio}"
DB_PASS="${DB_PASS:-ozbio}"
DB_NAME="${DB_NAME:-ozbio}"

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres -v ON_ERROR_STOP=1 <<SQL
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '${DB_USER}') THEN
    CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASS}';
  END IF;
END
\$\$;

SELECT format('CREATE DATABASE %I OWNER %I', '${DB_NAME}', '${DB_USER}')
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '${DB_NAME}') \\gexec

GRANT ALL PRIVILEGES ON DATABASE ${DB_NAME} TO ${DB_USER};
SQL

echo "OK: PostgreSQL ${PGHOST}:${PGPORT}, database=${DB_NAME}, user=${DB_USER}"
