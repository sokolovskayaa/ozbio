# ozbio — планировщик завода бурового оборудования (MVP)

Жадный планировщик с сохранением в **`data/schedule.json`**. Старт подхватывает файл; каждый `POST /orders` перезаписывает его.

Планирование **круглосуточно** (24/7). **Не в MVP:** смены, закрытие смены, overlap пакетов, симуляция времени, API статуса станка.

## Запуск

```bash
mvn spring-boot:run
```

Порт **8080** (`application.properties`). Без файла создаётся `data/schedule.json` из шаблона.

**Сброс демо** (сервер остановлен):

```bash
./scripts/reset-demo-data.sh
```

## Справочник деталей

В `data/schedule.json` → `partDefinitions`: **priority** и цепочка **tasks**.

| partId | Приоритет | Технология (демо) |
|--------|-----------|-------------------|
| `корпус-бура` | 10 | фрезеровка → расточка → чистовая фрезеровка |
| `вал-буровой` | 8 | черновая/чистовая токарка → шлифование |
| `гидроблок` | 5 | фрезерование → сверление |
| `муфта-зажимная` | 3 | токарка → сварка |
| `ниппель-соединительный` | 4 | сборка |

Станки: `ФРЕЗ-ЧПУ-01`, `ТОКАР-ЧПУ-02`, `РАСТОЧ-03`, `ШЛИФ-04`, `СВАРКА-05`, `СБОРКА-06`.

### Заказ

```json
{
  "orderId": "З-2026-0142",
  "parts": [
    {"partId": "вал-буровой", "quantity": 8},
    {"partId": "корпус-бура", "quantity": 8}
  ]
}
```

- `orderId` опционально → автономер `З-ГГГГ-NNNN`.
- `quantity` по умолчанию 1.
- `createdAt` = системное время.

### Правила планирования

| Правило | Поведение |
|---------|-----------|
| Очередь заказов | FIFO по `createdAt` |
| Приоритет детали | Внутри заказа (`partDefinitions.priority`) |
| Штуки | Пакет по операциям: все штуки op.0, затем все op.1… |
| Параллельность | Разные детали на разных станках, если не ухудшается более приоритетная деталь |
| Между заказами | Новый заказ не увеличивает `readyAt` ранних |
| Переналадка | Перед первой операцией и при смене `taskId` на станке |
| Смены / overlap | **Не поддерживаются** в MVP |

Подробно: [docs/правила-планировщика.md](docs/правила-планировщика.md).

## API

| Метод | URL | Описание |
|--------|-----|----------|
| POST | `/orders` | Добавить заказ |
| GET | `/schedule` | JSON |
| GET | `/schedule?format=html` | HTML в браузере |
| GET | `/schedule.html` | HTML (скачивание) |

## HTML-интерфейс

- Thymeleaf: `templates/schedule.html`
- CSS/JS: `static/css/schedule.css`, `static/js/schedule-actions.js`, `static/js/schedule-filters.js`
- Форма заказа, Gantt по станкам, фильтры, пресеты масштаба (День / Неделя…)

Открыть: http://localhost:8080/schedule?format=html

## Демонстрация

**Согласование с директором:** [docs/согласование-с-директором.md](docs/согласование-с-директором.md)

```bash
./scripts/reset-demo-data.sh
mvn spring-boot:run
./scripts/prep-director-demo.sh
open 'http://localhost:8080/schedule?format=html'
```

Добавить заказ через curl:

```bash
curl -s -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json; charset=utf-8' \
  -d '{"orderId":"З-2026-0142","parts":[{"partId":"вал-буровой","quantity":8},{"partId":"корпус-бура","quantity":8}]}'
```

Скрипт `./scripts/demo.sh` сохраняет HTML; для интерактивной демо используйте браузер.

## Группы станков и переналадка

В `machineGroups`: `setupMinutes` по группе (`cnc` 30, `heavy` 45, `finish` 20). Окна смен (`workWindows`) **не используются**.

## Тесты

```bash
mvn test
```

Матрица: [docs/тестирование.md](docs/тестирование.md).

## Структура (основное)

```
scheduler/
  api/http/ScheduleController.java
  api/view/SchedulePageRenderer.java, ScheduleHtmlRenderer.java
  engine/planning/GreedyScheduler.java
  engine/metrics/OrderProgress.java, TaskReadiness.java
  store/core/ScheduleStore.java, DemoFactoryCatalog.java, ScheduleSnapshotMapper.java
```
