# auth-gateway

Kotlin + Ktor сервис для аутентификации пользователей и единой входной точки в публичный API. Реализует регистрацию, логин, refresh/logout, проверку JWT и проксирование запросов к внутренним сервисам по спецификации [`swagger/auth-gateway.yml`](../swagger/auth-gateway.yml).

## Архитектура

```
HTTP-клиент --> Auth Gateway --> PostgreSQL
                    |
                    +--> market-data-service
                    +--> trading-service
                    +--> portfolio-service
```

Основные части сервиса:

- **Ktor application** - HTTP-роуты, CORS, обработка ошибок, JWT-аутентификация.
- **AuthRepository** - работа с пользователями и refresh token в PostgreSQL.
- **TokenService** - выпуск access token и refresh token.
- **UpstreamGateway** - проксирование запросов во внутренние сервисы.

## HTTP API

Все эндпоинты находятся под префиксом `/api/v1`.

| Метод | Путь | Описание |
|---|---|---|
| POST | `/auth/register` | Зарегистрировать пользователя и выдать пару токенов |
| POST | `/auth/login` | Войти по username/email и паролю |
| POST | `/auth/refresh` | Обновить access token по refresh token |
| POST | `/auth/logout` | Отозвать refresh token |
| GET | `/system/health` | Liveness probe |
| GET | `/system/ready` | Readiness probe: PostgreSQL и upstream-сервисы |

### Защищенные маршруты

Gateway проверяет `Authorization: Bearer <accessToken>`, достает `user_id` из JWT и передает его во внутренний сервис через заголовок `X-User-Id`.

| Внешний маршрут | Внутренний сервис |
|---|---|
| `/api/v1/market/**` | `MARKET_SERVICE_URL` |
| `/api/v1/orders/**` | `ORDER_SERVICE_URL` |
| `/api/v1/trades/**` | `ORDER_SERVICE_URL` |
| `/api/v1/portfolio/**` | `PORTFOLIO_SERVICE_URL` |

Маршруты `/api/v1/market/**` публичные и не требуют JWT. Остальные бизнес-маршруты требуют валидный access token.

## Тело запросов

### POST /auth/register

```json
{
  "username": "investor_01",
  "email": "investor01@example.com",
  "password": "StrongPass123!"
}
```

### POST /auth/login

```json
{
  "login": "investor_01",
  "password": "StrongPass123!"
}
```

В поле `login` можно передать username или email.

### POST /auth/refresh и POST /auth/logout

```json
{
  "refreshToken": "refresh-token"
}
```

## База данных

Сервис сам создает нужные таблицы при старте, если их еще нет:

| Таблица | Назначение |
|---|---|
| `auth_users` | Пользователи, email, username, bcrypt-хеш пароля |
| `auth_refresh_tokens` | Хеши refresh token, срок жизни и отметка отзыва |

Пароли хранятся только в виде bcrypt-хеша. Refresh token в базе хранится в хешированном виде.

## Конфигурация

| Переменная | По умолчанию | Описание |
|---|---|---|
| `PORT` | `8080` | Порт HTTP-сервера внутри контейнера/процесса |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5434/auth_gateway` | JDBC URL PostgreSQL |
| `DATABASE_USER` | `auth` | Пользователь PostgreSQL |
| `DATABASE_PASSWORD` | `auth` | Пароль PostgreSQL |
| `JWT_ISSUER` | `trump-investitions-auth-gateway` | Issuer для access token |
| `JWT_AUDIENCE` | `trump-investitions-clients` | Audience для access token |
| `JWT_REALM` | `trump-investitions` | Realm для Ktor JWT auth |
| `JWT_SECRET` | `change-me-in-production` | HMAC secret для подписи JWT |
| `ACCESS_TOKEN_TTL_SECONDS` | `900` | Время жизни access token |
| `REFRESH_TOKEN_TTL_SECONDS` | `2592000` | Время жизни refresh token |
| `MARKET_SERVICE_URL` | `http://localhost:8083/api/v1` | URL market-data-service |
| `ORDER_SERVICE_URL` | `http://localhost:8082/api/v1` | URL trading-service |
| `PORTFOLIO_SERVICE_URL` | `http://localhost:8081/api/v1` | URL portfolio-service |

В `docker-compose.yml` gateway слушает `8080` внутри контейнера, но опубликован наружу на `http://localhost:8084`.

## Запуск

Из корня репозитория:

```bash
docker compose up --build auth-gateway auth-postgres
```

Сервис будет доступен на `http://localhost:8084/api/v1`.

Проверка:

```bash
curl http://localhost:8084/api/v1/system/health
```

Для проверки проксирования ко всем backend-сервисам можно поднять весь стенд:

```bash
docker compose up --build
```

## Локальный запуск без Docker

Нужен JDK 21 и доступный PostgreSQL с базой `auth_gateway`.

```bash
cd auth-gateway
gradle run
```

При таком запуске сервис по умолчанию доступен на `http://localhost:8080/api/v1`. Если PostgreSQL слушает другой порт, передай `DATABASE_URL`, `DATABASE_USER` и `DATABASE_PASSWORD` через переменные окружения.

## Тестирование

Юнит- и компонентные тесты:

```bash
cd auth-gateway
gradle test
```

Что проверяется:

| Группа | Сценарии |
|---|---|
| Auth | регистрация, логин, refresh, logout |
| JWT | выпуск access token, claims `user_id` и `username` |
| Валидация | некорректный JSON, неправильные credentials |
| System | `/system/health`, `/system/ready` |
| Gateway | 401 без токена, проксирование с `X-User-Id`, публичный market route |

## Swagger UI

После запуска `swagger-ui`:

```bash
docker compose up -d swagger-ui
```

Интерфейс доступен на `http://localhost:8090`, спецификация auth gateway - на `http://localhost:8090/swagger/auth-gateway.yml`.
