# Данные планировщика

| Файл | Назначение |
|------|------------|
| `schedule.json.example` | Шаблон сброса (`./scripts/reset-demo-data.sh`): справочник, 6 станков, пустые заказы |
| `schedule.json` | Рабочий файл (в `.gitignore`), перезаписывается при `POST /orders` |

## POST `/orders` (тело запроса)

```json
{
  "parts": [
    {"partId": "вал-буровой", "quantity": 8},
    {"partId": "корпус-бура", "quantity": 8}
  ]
}
```

`orderId` можно указать вручную; иначе присвоится `З-ГГГГ-NNNN`.

## После планирования в `schedule.json`

- В `orders[].parts[]` — **`quantity`** и копия **`tasks`** из справочника.
- В `assignments[]` — **`unitIndex`** (номер штуки, с 0).

## Группы станков

В example только `groupId`, `name`, `setupMinutes` — **без** `workWindows`. Планирование круглосуточное.

Подробнее — [README.md](../README.md) и [docs/правила-планировщика.md](../docs/правила-планировщика.md).
