# ozbio — backend

Spring Boot API, PostgreSQL, Liquibase.

## База данных

API не запустится без PostgreSQL. Варианты:

### Вариант 1: Postgres в Docker (рекомендуется)

Из **корня** монорепозитория (`ozbio/`, на уровень выше `backend/`):

```bash
cd ..
docker-compose up -d postgres
```

Параметры подключения с хоста (для IDEA и `mvn`):

| Параметр | Значение |
|----------|----------|
| Host | `127.0.0.1` |
| Port | `5433` |
| Database | `ozbio` |
| User | `ozbio` |
| Password | `ozbio` |

Проверка, что контейнер работает:

```bash
docker-compose ps
docker-compose logs postgres
```

Остановить только БД:

```bash
docker-compose stop postgres
```

Сбросить данные:

```bash
docker-compose down -v
docker-compose up -d postgres
```

При запуске API на хосте используйте Spring-профиль **`docker`** — он задаёт `127.0.0.1:5433` (см. `application-docker.properties`).

### Вариант 2: Локальный PostgreSQL (без Docker)

Нужен установленный PostgreSQL на порту **5432**.

```bash
./scripts/setup-local-postgres.sh
```

Скрипт создаёт пользователя и БД `ozbio`. Подключение: `127.0.0.1:5432`, user/password `ozbio`.

Переменные окружения — [`.env.example`](.env.example).

Запуск API **без** профиля `docker` (дефолтный `application.properties`).

---

## Запуск API

### IntelliJ IDEA

Откройте папку **`backend/`**: File → Open → backend.

1. Maven → Reload All Maven Projects  
2. SDK: Java 21  
3. Сначала поднимите БД (см. выше)  
4. Run → **OzbioApplication**  
   - с Postgres в Docker: Active profiles = **`docker`**  
   - с локальным Postgres: профиль не нужен  

### Терминал

С Postgres из docker-compose:

```bash
# из корня репозитория
docker-compose up -d postgres

# из backend/
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

С локальным Postgres:

```bash
mvn spring-boot:run
```

Swagger: http://localhost:8080/swagger-ui.html

---

## Тесты

Тесты не требуют PostgreSQL (отключены в test profile):

```bash
mvn test
```
