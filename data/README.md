# Данные планировщика

## PostgreSQL (основное)

Приложение сохраняет план в PostgreSQL. Схема и демо-каталог — Liquibase (`src/main/resources/db/changelog/`).

| Команда | Назначение |
|---------|------------|
| `docker compose up -d` | Локальный PostgreSQL |
| `./scripts/reset-demo-db.sh` | Сброс заказов + демо-каталог |
| `mvn spring-boot:run` | Применить миграции при старте |

## JSON (legacy / тесты)

| Файл | Назначение |
|------|------------|
| `schedule.json.example` | Эталон для unit-тестов и экспорта |
| `schedule.json` | Не используется приложением (можно сбросить через `reset-demo-data.sh`) |

Подробнее — [README.md](../README.md).
