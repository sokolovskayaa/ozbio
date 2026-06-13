# ozbio — планировщик завода бурового оборудования (MVP)

Жадный планировщик с сохранением в **PostgreSQL** (Liquibase). Каждый `POST /orders` обновляет БД.

Планирование **круглосуточно** (24/7). **Не в MVP:** смены, закрытие смены, overlap пакетов, симуляция времени, API статуса станка.

## Запуск

### 1. PostgreSQL

**Вариант A — локальный PostgreSQL (порт 5432):**

```bash
./scripts/setup-local-postgres.sh
mvn spring-boot:run
```

Если ошибка `role "ozbio" does not exist` — сначала выполните `setup-local-postgres.sh`.

**Вариант B — Docker (порт 5433 на хосте):**

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

### 2. Приложение

```bash
cp .env.example .env   # при необходимости
mvn spring-boot:run
```

По умолчанию схема **`testing`** (демо-каталог при старте). Production — через env:

```bash
export APP_DB_SCHEMA=production
export LIQUIBASE_CONTEXTS=
mvn spring-boot:run
```

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `APP_DB_SCHEMA` | `testing` | PostgreSQL schema: `testing` или `production` |
| `LIQUIBASE_CONTEXTS` | `demo` | Пусто на prod — без автосида |
| `PG_HOST` / `PG_PORT` / `PG_DATABASE` | localhost / 5432 / ozbio | Подключение |
| `SPRING_DATASOURCE_URL` | — | Полный JDBC URL (перекрывает host/port/schema) |

Файл `.env` не коммитится — см. `.env.example`.

Приложение читает состояние из PostgreSQL при каждом запросе (`GET /schedule`, `POST /orders`). Каталог (станки, детали) — только из БД (Liquibase). После планирования в БД сохраняются новый заказ, назначения и обновлённые доступности станков; каталог не перезаписывается.

**Сброс демо** (очистить заказы, восстановить каталог):

```bash
./scripts/reset-demo-db.sh
# перезапустите приложение
```

Файл `data/schedule.json.example` — эталон для тестов и экспорта; приложение читает **БД**.

Предупреждения `System::load` / `enable-native-access` на Java 22+ — не ошибка; в `pom.xml` уже добавлен `--enable-native-access=ALL-UNNAMED` для `spring-boot:run`.

## Справочник деталей

Таблицы `part_definition`, `part_task`. Демо-данные — `src/main/resources/db/seed/demo-catalog.sql` (не на prod).

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

Подробно: [docs/правила-планировщика.md](docs/правила-планировщика.md).

## API

| Метод | URL | Описание |
|--------|-----|----------|
| POST | `/orders` | Добавить заказ |
| GET | `/schedule` | JSON |
| GET | `/schedule?format=html` | HTML в браузере |
| GET | `/schedule.html` | HTML (скачивание) |

## Демонстрация

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=docker
./scripts/reset-demo-db.sh
./scripts/prep-director-demo.sh
open 'http://localhost:8080/schedule?format=html'
```

Сценарий встречи: [docs/согласование-с-директором.md](docs/согласование-с-директором.md).

## Liquibase

- Master: `src/main/resources/db/changelog/db.changelog-master.yaml`
- Схемы PG: `testing`, `production` (`000a-create-schemas.sql`)
- DDL (в каждой схеме): `changes/001-initial-schema.sql`
- Демо-каталог (только context `demo`, схема `testing`): `db/seed/demo-catalog.sql`

| `APP_DB_SCHEMA` | `LIQUIBASE_CONTEXTS` | Каталог |
|-----------------|----------------------|---------|
| `testing` | `demo` (default) | автосид при старте |
| `production` | пусто | только DDL; каталог — ETL / свой SQL |

Ручная загрузка демо (только `testing`):

```bash
./scripts/seed-demo-catalog.sh
./scripts/seed-demo-catalog.sh --docker
```

## Тесты

```bash
mvn test
```

Unit-тесты используют `JsonScheduleRepository` (временный файл). Матрица: [docs/тестирование.md](docs/тестирование.md).

## Структура (основное)

```
scheduler/
  store/jdbc/JdbcScheduleRepository.java
  store/ScheduleRepository.java
  engine/planning/GreedyScheduler.java
  api/http/ScheduleController.java
```
