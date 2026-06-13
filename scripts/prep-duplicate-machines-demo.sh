#!/usr/bin/env bash
# Демо: несколько одинаковых станков (2× фреза, 2× токарка в группе ЧПУ).
# См. docs/проверка-дублирующих-станков.md
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE="${DEMO_URL:-http://localhost:8080}"
JQ="${JQ:-jq}"
TEMPLATE="$ROOT/data/schedule-duplicate-machines.example.json"
SCHEDULE="$ROOT/data/schedule.json"

# Количество штук — должно быть достаточно, чтобы заполнить оба станка одного типа
PART_QTY="${DEMO_PART_QTY:-12}"
ORDER_ID="${DEMO_ORDER_ID:-З-дубль-станки}"

section() { echo ""; echo "=== $1 ==="; }

require_jq() {
  if ! command -v "$JQ" >/dev/null 2>&1; then
    echo "ОШИБКА: нужен jq (brew install jq)" >&2
    exit 1
  fi
}

require_server() {
  if ! curl -sf "$BASE/schedule" >/dev/null; then
    echo "ОШИБКА: сервер не отвечает на $BASE" >&2
    echo "  1. $0          # сбросит data/schedule.json из шаблона с дублями" >&2
    echo "  2. mvn -q exec:java" >&2
    echo "  3. $0 --no-reset" >&2
    exit 1
  fi
}

reset_schedule() {
  if [[ ! -f "$TEMPLATE" ]]; then
    echo "ОШИБКА: нет шаблона $TEMPLATE" >&2
    exit 1
  fi
  cp "$TEMPLATE" "$SCHEDULE"
  echo "OK: $SCHEDULE ← schedule-duplicate-machines.example.json"
  echo "    Станки ЧПУ: ФРЕЗ-ЧПУ-01, ФРЕЗ-ЧПУ-02 (MILLING), ТОКАР-ЧПУ-02, ТОКАР-ЧПУ-03 (TURNING)"
}

verify_duplicate_machines_in_file() {
  local mills turns
  mills="$($JQ '[.machines[] | select(.groupId=="cnc" and (.capabilities|index("MILLING"))!=null) | .machineId] | length' "$SCHEDULE")"
  turns="$($JQ '[.machines[] | select(.groupId=="cnc" and (.capabilities|index("TURNING"))!=null) | .machineId] | length' "$SCHEDULE")"
  if [[ "$mills" -lt 2 || "$turns" -lt 2 ]]; then
    echo "ОШИБКА: в $SCHEDULE нужно ≥2 фрез и ≥2 токарей в группе cnc" >&2
    exit 1
  fi
}

post_demo_order() {
  local body resp code
  body="$($JQ -n --arg oid "$ORDER_ID" --argjson qty "$PART_QTY" '{
    orderId: $oid,
    parts: [
      {partId: "вал-буровой", quantity: $qty},
      {partId: "корпус-бура", quantity: $qty}
    ]
  }')"
  resp="$(mktemp)"
  code="$(curl -s -o "$resp" -w "%{http_code}" -X POST "$BASE/orders" \
    -H 'Content-Type: application/json; charset=utf-8' -d "$body")"
  if [[ "$code" != "201" ]]; then
    echo "POST /orders HTTP $code" >&2
    cat "$resp" >&2
    rm -f "$resp"
    exit 1
  fi
  $JQ '{orderId, readyAt, assignmentCount: (.assignmentsForOrder|length)}' <"$resp"
  rm -f "$resp"
}

verify_load_split() {
  local tmp fail=0
  tmp="$(mktemp)"
  curl -sf "$BASE/schedule" >"$tmp"

  section "Проверка: черновая токарка (2 токарных станка)"
  $JQ --arg oid "$ORDER_ID" --argjson qty "$PART_QTY" '
    def rough($p;$t):
      [(.orders // [])[] | select(.orderId==$oid) | (.parts // [])[] | select(.partId==$p)
        | (.assignments // [])[] | select(.taskId==$t and .status!="CANCELLED")];
    (rough("вал-буровой";"черновая-токарка") | group_by(.machineId) | map({
      machineId: .[0].machineId, units: (map(.unitIndex)|unique|length)
    })) as $byMachine |
    ($byMachine | map(.units) | add // 0) as $total |
    ($byMachine | length) as $machinesUsed |
    {
      machinesUsed: $machinesUsed,
      totalUnits: $total,
      perMachine: $byMachine,
      ok: ($total >= $qty and $machinesUsed >= 2)
    }
  ' "$tmp"
  if ! $JQ -e --arg oid "$ORDER_ID" --argjson qty "$PART_QTY" '
    def rough($p;$t):
      [(.orders // [])[] | select(.orderId==$oid) | (.parts // [])[] | select(.partId==$p)
        | (.assignments // [])[] | select(.taskId==$t and .status!="CANCELLED")];
    (rough("вал-буровой";"черновая-токарка") | map(.unitIndex)|unique|length) as $total |
    (rough("вал-буровой";"черновая-токарка") | map(.machineId)|unique|length) as $machines |
    ($total >= $qty and $machines >= 2)
  ' "$tmp" >/dev/null; then
    echo "ПРЕДУПРЕЖДЕНИЕ: черновая токарка не размазана по двум станкам (см. выше)." >&2
    fail=1
  fi

  section "Проверка: черновая фрезеровка (2 фрезерных станка)"
  $JQ --arg oid "$ORDER_ID" --argjson qty "$PART_QTY" '
    def rough($p;$t):
      [(.orders // [])[] | select(.orderId==$oid) | (.parts // [])[] | select(.partId==$p)
        | (.assignments // [])[] | select(.taskId==$t and .status!="CANCELLED")];
    (rough("корпус-бура";"черновая-фрезеровка") | group_by(.machineId) | map({
      machineId: .[0].machineId, units: (map(.unitIndex)|unique|length)
    })) as $byMachine |
    ($byMachine | map(.units) | add // 0) as $total |
    ($byMachine | length) as $machinesUsed |
    {
      machinesUsed: $machinesUsed,
      totalUnits: $total,
      perMachine: $byMachine,
      ok: ($total >= $qty and $machinesUsed >= 2)
    }
  ' "$tmp"
  if ! $JQ -e --arg oid "$ORDER_ID" --argjson qty "$PART_QTY" '
    def rough($p;$t):
      [(.orders // [])[] | select(.orderId==$oid) | (.parts // [])[] | select(.partId==$p)
        | (.assignments // [])[] | select(.taskId==$t and .status!="CANCELLED")];
    (rough("корпус-бура";"черновая-фрезеровка") | map(.unitIndex)|unique|length) as $total |
    (rough("корпус-бура";"черновая-фрезеровка") | map(.machineId)|unique|length) as $machines |
    ($total >= $qty and $machines >= 2)
  ' "$tmp" >/dev/null; then
    echo "ПРЕДУПРЕЖДЕНИЕ: черновая фрезеровка не размазана по двум станкам." >&2
    fail=1
  fi

  section "Парк станков (API)"
  $JQ '[.machines[] | select(.groupId=="cnc") | {machineId, status, availableAt}]' "$tmp"

  rm -f "$tmp"
  return "$fail"
}

NO_RESET=0
for arg in "$@"; do
  case "$arg" in
    --no-reset) NO_RESET=1 ;;
    -h|--help)
      echo "Использование: $0 [--no-reset]"
      echo "  Сброс: data/schedule-duplicate-machines.example.json → schedule.json"
      echo "  Заказ: $ORDER_ID, по $PART_QTY шт. вал + корпус (переменные DEMO_ORDER_ID, DEMO_PART_QTY)"
      exit 0
      ;;
  esac
done

require_jq

echo "=== Демо: дублирующие станки (2× MILLING, 2× TURNING) ==="

if [[ "$NO_RESET" -eq 0 ]]; then
  section "1. Сброс data/schedule.json"
  reset_schedule
  verify_duplicate_machines_in_file
  if curl -sf "$BASE/schedule" >/dev/null 2>&1; then
    echo ""
    echo "ВНИМАНИЕ: сервер уже запущен — он держит старый парк в памяти."
    echo "  Остановите JVM → снова: mvn -q exec:java"
    echo "  Затем: $0 --no-reset"
    echo ""
    exit 0
  fi
  echo "Сервер не запущен — после mvn -q exec:java выполните: $0 --no-reset"
  exit 0
fi

require_server

MACHINE_COUNT="$($JQ '[.machines[]|select(.groupId=="cnc")]|length' < <(curl -sf "$BASE/schedule"))"
if [[ "$MACHINE_COUNT" -lt 4 ]]; then
  echo "ОШИБКА: в API только $MACHINE_COUNT станков ЧПУ (нужно 4). Перезапустите сервер после сброса." >&2
  exit 1
fi

section "2. Заказ $ORDER_ID (${PART_QTY} шт. вал + корпус)"
post_demo_order

FAIL=0
verify_load_split || FAIL=$?

section "3. Открыть в браузере"
echo "  $BASE/schedule?format=html"
echo ""
echo "Инструкция: docs/проверка-дублирующих-станков.md"
echo ""

if [[ "$FAIL" -ne 0 ]]; then
  exit 1
fi
echo "Автопроверка пройдена: нагрузка ушла на оба станка каждого типа."
