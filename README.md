# ozbio

Каркас Spring Boot API с Swagger UI и PostgreSQL (Liquibase).

## Запуск

### PostgreSQL

**Локальный Postgres (5432):**

```bash
./scripts/setup-local-postgres.sh
```

**Docker (5433 на хосте):**

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

### Приложение

```bash
cp .env.example .env   # опционально, export вручную
mvn spring-boot:run
```

При старте Liquibase накатывает changelog из `src/main/resources/db/changelog/` (пока без changeset'ов).

Переменные: `PG_HOST`, `PG_PORT`, `PG_DATABASE`, `APP_DB_SCHEMA`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` — см. [`.env.example`](.env.example).

## Swagger

| URL | Описание |
|-----|----------|
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/api-docs | OpenAPI JSON |

## API

| Метод | URL | Описание |
|-------|-----|----------|
| POST | `/orders` | Создать заказ (stub) |

## Liquibase

- Master: [`src/main/resources/db/changelog/db.changelog-master.yaml`](src/main/resources/db/changelog/db.changelog-master.yaml)
- Таблицы: [`src/main/resources/db/changelog/tables/`](src/main/resources/db/changelog/tables/) — по одному файлу на сущность

### Схема (заказы)

| Таблица | Описание |
|---------|----------|
| `orders` | Заказ: `id`, `status` (enum), `created_at` |
| `detail` | Справочник деталей: `id`, `name` |
| `tool` | Справочник инструментов: `id`, `name`, `assemble_duration` |
| `order_detail` | Строки заказа: `(order_id, detail_id)`, `count` |
| `order_tool` | Инструменты заказа: `(order_id, tool_id)`, `count` |

`order_status`: `CREATED`, `PLANNED`, `COMPLETED`, `CANCELLED`

## Тесты

```bash
mvn test
```

Unit-тесты без PostgreSQL (Liquibase и DataSource отключены в test profile).
