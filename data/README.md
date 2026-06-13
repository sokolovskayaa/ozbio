# Данные планировщика

| Файл | Назначение |
|------|------------|
| `schedule.json.example` | Шаблон для сброса (`./scripts/reset-demo-data.sh`): справочник, станки, пустые заказы |
| `schedule.json` | Рабочий файл (в `.gitignore`), перезаписывается при `addOrder` и смене времени |

## POST `/orders` (тело запроса)

```json
{
  "parts": [
    {"partId": "вал-буровой", "quantity": 8},
    {"partId": "корпус-бура", "quantity": 8}
  ]
}
```

`orderId` можно указать вручную (как в `./scripts/demo.sh`); иначе присвоится автоматически.

## После планирования в `schedule.json`

- В `orders[].parts[]` — поле **`quantity`** и копия **`tasks`** из справочника.
- В `assignments[]` — поле **`unitIndex`** (номер штуки, с 0).

Подробнее — раздел «Сохранение» в [README.md](../README.md).

## Закрытие смены

- `lastClosedShiftEndByGroup` — конец последней закрытой смены по каждой группе станков (пустой объект после сброса демо).
- Сценарий `./scripts/demo.sh`: сдвиг времени до 18:00 → `GET /shifts/context` → `POST /shifts/close` с `machineTaskCounts`.
