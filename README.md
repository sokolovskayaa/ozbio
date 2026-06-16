# ozbio

Монорепозиторий: **backend** (Spring Boot API) + **frontend** (UI, позже).  
Docker Compose в корне поднимает весь бэкенд одной командой.

## Структура

```
ozbio/
├── docker-compose.yml    # postgres + api
├── README.md
├── backend/              # Java API — см. backend/README.md
└── frontend/             # UI (позже)
```

## Запуск всего бэкенда (Docker)

Требуется Docker. На macOS с Colima используйте **`docker-compose`** (с дефисом).

Из **корня** репозитория:

```bash
cd /path/to/ozbio
docker-compose up -d --build
```

Поднимаются:
- **postgres** — БД `ozbio`, на хосте порт **5433**
- **api** — Spring Boot, порт **8080**

Проверка:

| Сервис | URL |
|--------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON | http://localhost:8080/api-docs |

Логи API:

```bash
docker-compose logs -f api
```

Остановка:

```bash
docker-compose down
```

Сброс БД (удалить volume и накатить миграции заново):

```bash
docker-compose down -v
docker-compose up -d --build
```

Пересборка только API после изменений в коде:

```bash
docker-compose up -d --build api
```

### Если не стартует

| Проблема | Решение |
|----------|---------|
| `relation already exists` / ошибки Liquibase | `docker-compose down -v` и поднять заново |
| Порт 8080 занят | освободить порт или изменить mapping в `docker-compose.yml` |
| `docker compose` не найден | использовать `docker-compose` |
| API падает до готовности БД | подождать healthcheck postgres; `docker-compose ps` |

## Разработка

| Часть | Документация |
|-------|--------------|
| Backend (IDE, БД, локальный запуск) | [backend/README.md](backend/README.md) |
| Frontend | (позже) |
