# Данные планировщика

Состояние планировщика хранится в PostgreSQL. Схема и демо-каталог — Liquibase (`src/main/resources/db/changelog/`).

| Команда | Назначение |
|---------|------------|
| `docker compose up -d` | Локальный PostgreSQL |
| `./scripts/reset-demo-db.sh` | Сброс заказов + демо-каталог |
| `mvn spring-boot:run` | Применить миграции при старте |

Подробнее — [README.md](../README.md).
