#!/usr/bin/env bash
# Проверка готовности к встрече с директором (см. docs/согласование-с-директором.md).
set -euo pipefail

BASE="${DEMO_URL:-http://localhost:8080}"
JQ="${JQ:-jq}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "=== Подготовка к согласованию с директором ==="
echo ""

if ! curl -sf "$BASE/schedule" >/dev/null; then
  echo "ОШИБКА: сервер не отвечает на $BASE" >&2
  echo "  1. PostgreSQL:" >&2
  echo "     ./scripts/setup-local-postgres.sh   # локальный Postgres" >&2
  echo "     или docker compose up -d            # Docker на порту 5433" >&2
  echo "  2. mvn spring-boot:run  (для Docker: -Dspring-boot.run.profiles=docker)" >&2
  echo "  3. снова: $0" >&2
  exit 1
fi

TMP="$(mktemp)"
curl -sf "$BASE/schedule" >"$TMP"

CLOCK="$($JQ -r '.clock.currentTime // empty' "$TMP")"
FACTORY="$($JQ -r '.factoryStartedAt // empty' "$TMP")"
ORDERS="$($JQ '(.orders // []) | length' "$TMP")"

echo "Сервер: OK ($BASE)"
echo "Сейчас (системное время): ${CLOCK:-—}"
echo "Запуск завода в плане:    ${FACTORY:-—}"
echo "Заказов в плане (API): $ORDERS"
if [[ "$ORDERS" != "0" ]]; then
  echo "  id: $($JQ -r '[.orders[].orderId] | join(", ")' "$TMP")"
fi

echo ""

FAIL=0

if [[ "$ORDERS" != "0" ]]; then
  echo "ПРЕДУПРЕЖДЕНИЕ: в API $ORDERS заказ(ов) — для «живого» сценария нужен 0 после reset." >&2
  echo "  Сброс: ./scripts/reset-demo-db.sh → перезапуск сервера" >&2
  FAIL=1
fi

EXPECTED_FACTORY="2026-05-22T08:00:00Z"
if [[ -n "$FACTORY" && "$FACTORY" != "$EXPECTED_FACTORY" ]]; then
  echo "ПРЕДУПРЕЖДЕНИЕ: factoryStartedAt=$FACTORY (ожидался $EXPECTED_FACTORY после reset)." >&2
  FAIL=1
fi

rm -f "$TMP"

echo "Откройте в браузере:"
echo "  $BASE/schedule?format=html"
echo ""
echo "Документ встречи: docs/согласование-с-директором.md"
echo "Полный демо-прогон: ./scripts/demo.sh"
echo ""

if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi

echo "Готово к встрече (пустое демо, factoryStartedAt 08:00)."
