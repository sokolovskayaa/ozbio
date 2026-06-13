#!/usr/bin/env bash
# Сброс data/schedule.json к демо-состоянию (08:00, пустые заказы, справочник деталей).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cp "$ROOT/data/schedule.json.example" "$ROOT/data/schedule.json"
echo "OK: data/schedule.json сброшен из schedule.json.example"
echo "Заказы добавляйте через POST /orders с полем parts: [{\"partId\":\"...\", \"quantity\": N}]"
