#!/usr/bin/env bash
# Демонстрация MVP: заказы + HTML. Сервер: mvn spring-boot:run
set -euo pipefail

BASE="${DEMO_URL:-http://localhost:8080}"
JQ="${JQ:-jq}"
DEMO_PART_QTY="${DEMO_PART_QTY:-8}"

section() { echo ""; echo "=== $1 ==="; }

require_server() {
  if ! curl -sf "$BASE/schedule" >/dev/null; then
    echo "Сервер не отвечает на $BASE — запустите mvn spring-boot:run и повторите." >&2
    exit 1
  fi
}

api_json() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local filter="$4"
  local tmp
  tmp="$(mktemp)"
  local code
  if [[ -n "$body" ]]; then
    code="$(curl -s -o "$tmp" -w "%{http_code}" -X "$method" "$url" \
      -H 'Content-Type: application/json; charset=utf-8' -d "$body")"
  else
    code="$(curl -s -o "$tmp" -w "%{http_code}" -X "$method" "$url")"
  fi
  if [[ ! -s "$tmp" ]]; then
    echo "Пустой ответ API ($method $url), HTTP $code" >&2
    rm -f "$tmp"
    exit 1
  fi
  $JQ "$filter" <"$tmp"
  local jq_exit=$?
  if [[ $jq_exit -ne 0 ]]; then
    echo "--- тело ответа (HTTP $code) ---" >&2
    cat "$tmp" >&2
    rm -f "$tmp"
    exit $jq_exit
  fi
  if $JQ -e '.error' >/dev/null 2>&1 <"$tmp"; then
    echo "Подсказка: сбросьте демо — RESET_DEMO=1 $0 (остановите сервер, сброс, запуск сервера, снова $0)" >&2
  fi
  rm -f "$tmp"
}

http_ok() {
  [[ "$1" == "200" || "$1" == "201" ]]
}

if [[ "${RESET_DEMO:-0}" == "1" ]]; then
  section "0. Сброс data/schedule.json"
  SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
  "$SCRIPT_DIR/reset-demo-data.sh"
  echo "Перезапустите сервер, затем снова запустите demo.sh"
  exit 0
fi

require_server

verify_demo_order_0142() {
  local tmp
  tmp="$(mktemp)"
  curl -sf -o "$tmp" "$BASE/schedule" || {
    echo "Не удалось GET /schedule для проверки" >&2
    rm -f "$tmp"
    return 1
  }
  if ! $JQ -e --arg oid "З-2026-0142" --arg pid "вал-буровой" --arg tid "черновая-токарка" --argjson qty "$DEMO_PART_QTY" '
    def rough($o;$p;$t):
      [(.orders // [])[] | select(.orderId==$o) | (.parts // [])[] | select(.partId==$p)
        | (.assignments // [])[] | select(.taskId==$t)];
    (rough($oid;$pid;$tid) | map(.unitIndex) | unique | length) as $units |
    (rough($oid;$pid;$tid) | map(.plannedStart) | min) as $start |
    (rough($oid;$pid;$tid) | map(.plannedEnd) | max) as $end |
    if $units < $qty then
      error("черновая-токарка: запланировано только \($units) из \($qty) шт. — сбросьте демо (RESET_DEMO=1) и перезапустите сервер")
    elif (($end | fromdateiso8601) - ($start | fromdateiso8601)) < (5 * 3600) then
      error("черновая-токарка \($qty) шт.: \($start) — \($end) короче 5 ч — неполный план")
    else
      {ok:true, units:$units, roughStart:$start, roughEnd:$end}
    end
  ' "$tmp" >/dev/null; then
    echo "--- ответ /schedule ---" >&2
    cat "$tmp" >&2
    rm -f "$tmp"
    return 1
  fi
  rm -f "$tmp"
  echo "Проверка З-2026-0142: черновая токарка — ${DEMO_PART_QTY} шт., интервал >= 5 ч."
}

section "1. Заказ З-2026-0142: вал буровой + корпус бура (по ${DEMO_PART_QTY} шт.)"
echo "Ожидание: пакетно по операциям; корпус (приоритет 10) и вал могут идти параллельно на разных станках."
RESP_0142="$(mktemp)"
ORDER_0142_BODY="$(jq -n --argjson qty "$DEMO_PART_QTY" '{
  orderId: "З-2026-0142",
  parts: [
    {partId: "вал-буровой", quantity: $qty},
    {partId: "корпус-бура", quantity: $qty}
  ]
}')"
CODE_0142="$(curl -s -o "$RESP_0142" -w "%{http_code}" -X POST "$BASE/orders" \
  -H 'Content-Type: application/json; charset=utf-8' -d "$ORDER_0142_BODY")"
READY_0142_BEFORE="$($JQ -r '.readyAt // empty' <"$RESP_0142")"
if http_ok "$CODE_0142"; then
  $JQ '{
    orderId,
    readyAt,
    roughUnits: [(.assignmentsForOrder // [])[] | select(.partId=="вал-буровой" and .taskId=="черновая-токарка") | .unitIndex] | unique | length
  }' <"$RESP_0142"
elif $JQ -e '.error' <"$RESP_0142" >/dev/null 2>&1; then
  echo "POST: $($JQ -r .error <"$RESP_0142")"
  echo "Используется уже сохранённый план — проверяем GET /schedule…"
  SCHEDULE_TMP="$(mktemp)"
  curl -sf "$BASE/schedule" >"$SCHEDULE_TMP"
  READY_0142_BEFORE="$($JQ -r --arg oid "З-2026-0142" '.orders[] | select(.orderId==$oid) | .readyAt' "$SCHEDULE_TMP")"
  rm -f "$SCHEDULE_TMP"
else
  echo "POST /orders HTTP $CODE_0142" >&2
  cat "$RESP_0142" >&2
  rm -f "$RESP_0142"
  exit 1
fi
rm -f "$RESP_0142"
verify_demo_order_0142 || exit 1

section "2. Текущее расписание (JSON)"
api_json GET "$BASE/schedule" '' '
  {
    clock,
    factoryStartedAt,
    machines: [(.machines // [])[] | {machineId, status, availableAt}],
    orders: [(.orders // [])[] | {
      orderId, readyAt,
      parts: [(.parts // [])[] | {partId, quantity, priority, slackMinutes}]
    }]
  }
'

section "3. Заказ З-2026-0148: зажимная муфта (второй в очереди)"
RESP_0148="$(mktemp)"
CODE_0148="$(curl -s -o "$RESP_0148" -w "%{http_code}" -X POST "$BASE/orders" \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d '{"orderId":"З-2026-0148","parts":[{"partId":"муфта-зажимная","quantity":1}]}')"
if http_ok "$CODE_0148"; then
  $JQ '{orderId, readyAt}' <"$RESP_0148"
else
  echo "POST /orders HTTP $CODE_0148" >&2
  cat "$RESP_0148" >&2
  rm -f "$RESP_0148"
  exit 1
fi
rm -f "$RESP_0148"

section "4. Проверка очереди: readyAt З-2026-0142 не ухудшился"
SCHEDULE_TMP="$(mktemp)"
curl -sf "$BASE/schedule" >"$SCHEDULE_TMP"
READY_0142_AFTER="$($JQ -r --arg oid "З-2026-0142" '.orders[] | select(.orderId==$oid) | .readyAt' "$SCHEDULE_TMP")"
ORDER_COUNT="$($JQ '(.orders // []) | length' "$SCHEDULE_TMP")"
echo "Заказов: $ORDER_COUNT"
echo "readyAt 0142 до второго заказа: ${READY_0142_BEFORE:-—}"
echo "readyAt 0142 после:            ${READY_0142_AFTER:-—}"
if [[ -n "$READY_0142_BEFORE" && -n "$READY_0142_AFTER" ]]; then
  if [[ "$READY_0142_AFTER" > "$READY_0142_BEFORE" ]]; then
    echo "ПРЕДУПРЕЖДЕНИЕ: readyAt первого заказа увеличился после второго." >&2
    rm -f "$SCHEDULE_TMP"
    exit 1
  fi
  echo "OK: первый заказ не отодвинут."
fi
rm -f "$SCHEDULE_TMP"

section "5. HTML-отчёт"
OUT="${1:-schedule-demo.html}"
curl -sf -o "$OUT" "$BASE/schedule.html?download=false"
echo "Сохранено: $OUT"
echo "В браузере: $BASE/schedule?format=html"
echo "Сценарий встречи: docs/согласование-с-директором.md"

section "Готово"
