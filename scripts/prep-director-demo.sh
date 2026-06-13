#!/usr/bin/env bash
# Проверка готовности к встрече с директором (см. docs/согласование-с-директором.md).
set -euo pipefail

BASE="${DEMO_URL:-http://localhost:8080}"
JQ="${JQ:-jq}"

echo "=== Подготовка к согласованию с директором ==="
echo ""

if ! curl -sf "$BASE/schedule" >/dev/null; then
  echo "ОШИБКА: сервер не отвечает на $BASE" >&2
  echo "  1. ./scripts/reset-demo-data.sh  (сервер остановлен)" >&2
  echo "  2. mvn -q exec:java" >&2
  echo "  3. снова: $0" >&2
  exit 1
fi

TMP="$(mktemp)"
curl -sf "$BASE/schedule" >"$TMP"

CLOCK="$($JQ -r '.clock.currentTime // empty' "$TMP")"
ORDERS="$($JQ '(.orders // []) | length' "$TMP")"
ENABLED="$($JQ -r '.clock.simulationEnabled // false' "$TMP")"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Сервер: OK ($BASE)"
echo "Симуляция: $ENABLED"
echo "Текущее время: ${CLOCK:-—}"
echo "Заказов в плане (API): $ORDERS"
if [[ "$ORDERS" != "0" ]]; then
  echo "  id: $($JQ -r '[.orders[].orderId] | join(", ")' "$TMP")"
fi

DISK_ORDERS=""
if [[ -f "$ROOT/data/schedule.json" ]]; then
  DISK_ORDERS="$($JQ '(.orders // []) | length' "$ROOT/data/schedule.json")"
  echo "Заказов в data/schedule.json: $DISK_ORDERS"
fi
echo ""

FAIL=0
if [[ "$ENABLED" != "true" ]]; then
  echo "ПРЕДУПРЕЖДЕНИЕ: simulationClock выключен — сценарии со временем могут отличаться." >&2
fi

if [[ "$ORDERS" != "0" ]]; then
  echo "ПРЕДУПРЕЖДЕНИЕ: в API $ORDERS заказ(ов) (для «Живого цеха» нужен 0 после reset)." >&2
  if [[ -n "$DISK_ORDERS" && "$DISK_ORDERS" == "0" ]]; then
    echo "  В файле заказов 0 — сервер держит старые данные в памяти." >&2
    echo "  Остановите сервер → ./scripts/reset-demo-data.sh → снова mvn -q exec:java" >&2
  else
    echo "  Сброс: остановите сервер → ./scripts/reset-demo-data.sh → запуск сервера" >&2
  fi
  FAIL=1
fi

if [[ -n "$CLOCK" && "$CLOCK" != "2026-05-22T08:00:00Z" && "$ORDERS" == "0" ]]; then
  echo "ПРЕДУПРЕЖДЕНИЕ: время не 08:00 — для чистого старта сделайте reset." >&2
  FAIL=1
fi

rm -f "$TMP"

echo "Откройте в браузере:"
echo "  $BASE/schedule?format=html"
echo ""
echo "Документ встречи: docs/согласование-с-директором.md"
echo ""

if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi

echo "Готово к встрече (пустое демо 08:00)."
