# ozbio — планировщик завода бурового оборудования (MVP)

Жадный планировщик с сохранением в **`data/schedule.json`**. Старт JVM подхватывает файл; каждый `addOrder` и смена времени/станка перезаписывают его.

## Запуск

```bash
mvn -q exec:java
```

При первом запуске без файла создаётся `data/schedule.json` из шаблона.

Перекрытие пакетов (overlap) **по умолчанию выключено**. Включение: в JSON `"scheduling": { "overlapBatches": true }` или при запуске `mvn -q exec:java -Dscheduler.overlapBatches=true` (свойство JVM перекрывает JSON).

**Сброс демо-данных** (фиксированное время 08:00, пустая очередь заказов):

```bash
./scripts/reset-demo-data.sh
# затем перезапустите сервер
```

## Справочник деталей (при старте)

В `data/schedule.json` → `partDefinitions`: для каждого типа детали — **priority** и **цепочка задач**.

| partId | Приоритет | Технология (демо) |
|--------|-----------|-------------------|
| `корпус-бура` | 10 | черновая фрезеровка → расточивание → чистовая фрезеровка |
| `вал-буровой` | 8 | черновая/чистовая токарка → шлифование |
| `гидроблок` | 5 | фрезерование плоскостей → сверление гидроканалов |
| `муфта-зажимная` | 3 | токарка муфты → сварка MIG |
| `ниппель-соединительный` | 4 | сборка уплотнительного узла |

Станки: `ФРЕЗ-ЧПУ-01`, `ТОКАР-ЧПУ-02`, `РАСТОЧ-03`, `ШЛИФ-04`, `СВАРКА-05`, `СБОРКА-06`.

Идентификаторы заказов, деталей, операций и станков в демо — **на русском** (UTF-8). В JSON поле `requiredCapability` остаётся техническим кодом (`MILLING`, `TURNING` …); в HTML и отчётах показывается по-русски.

```json
"partDefinitions": {
  "вал-буровой": {
    "priority": 8,
    "tasks": [
      {"taskId": "черновая-токарка", "sequence": 0, "duration": "PT70M", "requiredCapability": "TURNING"},
      {"taskId": "чистовая-токарка", "sequence": 1, "duration": "PT45M", "requiredCapability": "TURNING"},
      {"taskId": "шлифование-сегментов", "sequence": 2, "duration": "PT50M", "requiredCapability": "GRINDING"}
    ]
  }
}
```

**Заказ** — позиции `parts` (тип детали + количество; кириллица допустима). Поле `orderId` опционально: если не передать, сервер присвоит номер `З-ГГГГ-NNNN` (год — по часовому поясу завода, номер — следующий за существующими в этом году):

```json
{
  "orderId": "З-2026-0142",
  "parts": [
    {"partId": "вал-буровой", "quantity": 8},
    {"partId": "корпус-бура", "quantity": 8}
  ]
}
```

- `quantity` можно опустить — по умолчанию 1.
- `createdAt` заказа = **текущее время** планировщика (симуляция или системные часы).
- Задачи подставляются из справочника автоматически.

### Правила планирования

| Правило | Поведение |
|---------|-----------|
| Очередь заказов | FIFO по `createdAt` |
| Приоритет детали | Только **внутри** заказа (из `partDefinitions.priority`) |
| Штуки одного типа | **Пакетно по операциям:** все штуки проходят операцию 1, затем все — операцию 2…; внутри операции штуки подряд (`unitIndex` 0 → 1 → 2…) |
| Разные типы в заказе | Могут идти **параллельно** на разных станках, если не сдвигается `partReadyAt` более приоритетных деталей |
| Между заказами | Новый заказ не должен увеличивать `readyAt` более ранних заказов |
| Между заказами (пока нет) | Склейка одинаковых деталей без переналадки не выполняется |
| Перекрытие пакетов | **По умолчанию выкл.** Классический пакет. При `scheduling.overlapBatches: true` — overlap по §5 |

### Сохранение в `data/schedule.json`

Шаблон сброса: `data/schedule.json.example` (пустые `orders` / `assignments`).

В файле после `addOrder` у заказа в `parts` сохраняются `partId`, **`quantity`** и копия цепочки `tasks`. У каждого `assignment` — **`unitIndex`** (0-based; переналадка с тем же индексом штуки):

```json
"orders": [{
  "orderId": "З-2026-0142",
  "createdAt": "2026-05-22T08:00:00Z",
  "priority": 381516447,
  "parts": [
    {"partId": "корпус-бура", "quantity": 8, "tasks": ["..."]}
  ]
}],
"assignments": [{
  "orderId": "З-2026-0142",
  "partId": "корпус-бура",
  "unitIndex": 0,
  "taskId": "черновая-фрезеровка",
  "sequence": 0,
  "machineId": "ФРЕЗ-ЧПУ-01",
  "plannedStart": "...",
  "plannedEnd": "...",
  "status": "PLANNED",
  "actualStart": null,
  "actualEnd": null
}],
"machineBlocks": [
  {
    "machineId": "ФРЕЗ-ЧПУ-01",
    "from": "2026-05-22T18:00:00Z",
    "to": "2026-05-22T20:00:00Z",
    "reason": "простой"
  }
]
```

Старые файлы без `quantity` / `unitIndex` при загрузке получают `quantity: 1` и `unitIndex: 0`. Без `priority` приоритет восстанавливается из `createdAt`. Без `status` — `PLANNED`.

### Закрытие смены

`GET /shifts/context` — незакрытые смены, `closeRows` (все станки всех групп), агрегаты «в плане» по `(machineId, taskId)`.

`POST /shifts/close` — в HTML рекомендуется закрытие **всех** смен одним запросом:

```json
{
  "closeAllPendingShifts": true,
  "shiftEnd": "2026-05-22T17:00:00Z",
  "machineTaskCounts": [
    { "groupId": "cnc", "machineId": "ТОКАР-ЧПУ-02", "taskId": "черновая-токарка", "completedCount": 7 }
  ],
  "idleBlocks": []
}
```

Для каждой строки `closeRows` обязателен `completedCount`. Переплан — **один раз** после всех фактов. Отменённые операции в отчёте не показываются. Подробнее: [docs/правила-планировщика.md](docs/правила-планировщика.md) §9.

## API

| Метод | URL | Описание |
|--------|-----|----------|
| POST | `/orders` | Добавить заказ |
| GET | `/schedule` | JSON |
| GET | `/schedule.html` | Скачать HTML |
| GET | `/schedule?format=html` | HTML в браузере |
| PUT | `/time` | Сдвинуть симулированное «сейчас» |
| PATCH | `/machines/{id}` | Статус станка (DOWN, MAINTENANCE, SETUP) |
| PUT | `/machine-groups/{id}` | Смены группы и время переналадки (минуты) |
| GET | `/shifts/context` | Контекст закрытия смены (просроченные, агрегаты по станкам) |
| POST | `/shifts/close` | Закрытие смены: count по типам операций или поштучные факты, простои, переплан |

## Демонстрация для директора

**Согласование с директором (ручные сценарии, лист ОК/не ОК):** [docs/согласование-с-директором.md](docs/согласование-с-директором.md). Перед встречей: `./scripts/prep-director-demo.sh`.

### Вариант A: один скрипт

В одном терминале:

```bash
mvn -q exec:java
```

В другом:

```bash
chmod +x scripts/*.sh
./scripts/reset-demo-data.sh    # один раз, пока сервер остановлен
mvn -q exec:java                # терминал 1
./scripts/demo.sh               # терминал 2 → schedule-demo.html
open schedule-demo.html
```

Сценарий: З-2026-0142 (**8×** вал + **8×** корпус, см. `DEMO_PART_QTY` в `demo.sh`) → время 10:00 → З-2026-0148 (муфта) → время 18:00 → **незакрытая смена ЧПУ** в HTML. Закрытие смены с планом — вручную в разделе «Закрытие смены».

Файл `schedule-demo.html` в корне — **артефакт** `./scripts/demo.sh` (не коммитить; перегенерируется).

Сброс из demo: `RESET_DEMO=1 ./scripts/demo.sh` (нужен перезапуск сервера после).

### Вариант B: по шагам (curl)

```bash
./scripts/reset-demo-data.sh
# перезапуск сервера

# 1. Первый заказ: две детали, разный приоритет
curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"З-2026-0142","parts":[{"partId":"вал-буровой","quantity":8},{"partId":"корпус-бура","quantity":8}]}' | jq .

# 2. Посмотреть план
curl -s http://localhost:8080/schedule | jq '{clock, orders: [.orders[]|{orderId,readyAt,parts:[.parts[]|{partId,quantity,priority,slackMinutes}]}]}'

# 3. «Прошло 2 часа»
curl -s -X PUT http://localhost:8080/time \
  -H 'Content-Type: application/json' \
  -d '{"currentTime":"2026-05-22T10:00:00Z"}' | jq .

# 4. Контекст закрытия смены (агрегаты по станкам)
curl -s http://localhost:8080/shifts/context | jq '{stale,pendingShiftCount,activeShift}'

# 5. Второй заказ — createdAt = 10:00 автоматически
curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -d '{"orderId":"З-2026-0148","parts":[{"partId":"муфта-зажимная","quantity":1}]}' | jq .

# 6. Конец дня — незакрытые смены
curl -s -X PUT http://localhost:8080/time \
  -H 'Content-Type: application/json' \
  -d '{"currentTime":"2026-05-22T18:00:00Z"}' | jq .
curl -s http://localhost:8080/shifts/context | jq '{stale,pendingShiftCount,activeShift:.activeShift|{groupName,shiftStart,shiftEnd}}'

# 7. HTML: закрытие смены, расписание по станкам
open 'http://localhost:8080/schedule?format=html'
```

**Что показать на HTML:** раздел **«Закрытие смены»** (баннер, если смены просрочены; таблица «в плане / сделано») → заказы → **«Расписание по станкам»** → справочник. На станках: пресеты **Смена / День / 3 дня / …** и подписи времени под шкалой.

### Вариант C: поломка станка

```bash
# ТОКАР-ЧПУ-02 — единственный токарный; без него вал не запланировать
curl -s -X PATCH 'http://localhost:8080/machines/%D0%A2%D0%9E%D0%9A%D0%90%D0%A0-%D0%A7%D0%9F%D0%A3-02' \
  -H 'Content-Type: application/json' \
  -d '{"status":"DOWN"}'

curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d '{"orderId":"З-2026-0150","parts":[{"partId":"вал-буровой"}]}'
# → ошибка: нет станка для TURNING
```

## Симуляция времени — как отключить (прод)

В `data/schedule.json`: `"simulationClock": { "enabled": false }`  
В `Main` — `SystemCurrentTimeProvider` вместо `StoreCurrentTimeProvider`.

## Группы станков, смены и переналадка

В `data/schedule.json` → `machineGroups`: для каждой группы — **окна работы** и **статическое время переналадки** (`setupMinutes`, задаётся до запуска сервера).

| groupId | Тип | setupMinutes (среднее) |
|---------|-----|------------------------|
| `cnc` | ЧПУ (фрезерный, токарный) | 30 |
| `heavy` | Расточка, шлифование | 45 |
| `finish` | Сварка, сборка | 20 |

Станок ссылается на группу через `groupId`. Планировщик ставит операции только в смену (часовой пояс: Москва). Переналадка учитывается **перед первой операцией на станке** и **при каждой смене типа операции** (`taskId`) на этом станке (оранжевый отрезок `переналадка` в HTML).

Пример обновления смен:

Пример в `schedule.json`:

```json
"machineGroups": [
  {
    "groupId": "cnc",
    "name": "ЧПУ (фрезерный и токарный)",
    "setupMinutes": 30,
    "workWindows": [{"dayOfWeek": "MONDAY", "start": "08:00", "end": "20:00"}]
  }
]
```

Изменить смены или переналадку после старта (опционально):

```bash
curl -s -X PUT http://localhost:8080/machine-groups/cnc \
  -H 'Content-Type: application/json' \
  -d '{
    "setupMinutes": 30,
    "workWindows": [
      {"dayOfWeek": "MONDAY", "start": "08:00", "end": "20:00"},
      {"dayOfWeek": "FRIDAY", "start": "08:00", "end": "18:00"}
    ]
  }'
```

Демо-группы: `cnc` (ЧПУ), `heavy` (расточка/шлиф), `finish` (сварка/сборка).

## Статусы станка

| Статус | Смысл |
|--------|--------|
| IDLE / BUSY | Из расписания и `currentTime` (в HTML: Свободен / Занят) |
| DOWN / MAINTENANCE / SETUP | Вручную; новые задачи не назначаются (в HTML по-русски) |

## Тесты

```bash
mvn -q test
```

Матрица сценариев и ручные демо: **[docs/тестирование.md](docs/тестирование.md)**.

Дублирующие станки (2× фреза, 2× токар):

```bash
./scripts/prep-duplicate-machines-demo.sh   # сброс + перезапуск сервера
./scripts/prep-duplicate-machines-demo.sh --no-reset
```
