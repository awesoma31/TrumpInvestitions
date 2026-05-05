# portfolio-service

Микросервис для управления инвестиционным портфелем пользователя. Хранит кэш-баланс, открытые позиции, историю ордеров и сделок. Реагирует на торговые события из Kafka и предоставляет REST API для чтения состояния портфеля.

## Архитектура

```
HTTP-клиент ──► Handler ──► Service ──► Repository ──► PostgreSQL
                                ▲
Kafka (trading-events) ─────────┘
```

Три слоя:

- **handler** — HTTP-обработчики, парсинг запросов, запись ответов
- **service** — бизнес-логика: расчёт PnL, обновление позиций, обработка событий
- **repository** — работа с PostgreSQL (все SQL-запросы здесь)

## HTTP API

Все эндпоинты под префиксом `/api/v1`. Пользователь идентифицируется через заголовок `X-User-Id: <int64>`.

| Метод  | Путь                     | Описание                                      |
|--------|--------------------------|-----------------------------------------------|
| GET    | `/portfolio`             | Полный портфель: баланс, позиции, суммарный PnL |
| GET    | `/positions`             | Список позиций (`?symbol=` для фильтрации)    |
| GET    | `/positions/{symbol}`    | Позиция по тикеру                             |
| GET    | `/pnl`                   | Только PnL (realized + unrealized)            |
| POST   | `/balance/deposit`       | Пополнить кэш-баланс                          |
| POST   | `/balance/withdraw`      | Снять деньги (вернёт 422 при нехватке средств) |
| GET    | `/orders`                | История ордеров (`?status=`, `?symbol=`, `?limit=`, `?offset=`) |
| GET    | `/trades`                | История сделок (`?symbol=`, `?side=`, `?limit=`, `?offset=`)   |
| GET    | `/system/health`         | Liveness probe                                |
| GET    | `/system/ready`          | Readiness probe (проверяет связь с Postgres)  |

## Kafka

Консьюмер читает топик `trading-events` и обрабатывает четыре типа событий:

| eventType          | Действие                                                        |
|--------------------|-----------------------------------------------------------------|
| `TRADE_EXECUTED`   | Сохраняет трейд, пересчитывает позицию и средневзвешенную цену |
| `ORDER_FILLED`     | Обновляет статус ордера → `FILLED`, записывает avg fill price  |
| `ORDER_REJECTED`   | Обновляет статус → `REJECTED`, сохраняет причину отказа        |
| `ORDER_CANCELLED`  | Обновляет статус → `CANCELLED`                                  |

Ошибки обработки логируются и не останавливают консьюмер.

## Расчёт PnL и позиций

**BUY:** средняя цена пересчитывается по формуле взвешенной средней:
```
newAvgPrice = (oldAvgPrice * oldQty + fillPrice * newQty) / (oldQty + newQty)
```

**SELL:** фиксируется реализованный PnL:
```
realizedPnl += (sellPrice - avgPrice) * qty
```

**Unrealized PnL** считается на лету при каждом запросе:
```
unrealizedPnl = (currentPrice - avgPrice) * qty
```

> Текущая цена запрашивается через интерфейс `PriceProvider`. В текущем рантайме используется HTTP-интеграция с `market-data-service`.

## База данных

Четыре таблицы (миграция: `migrations/001_init.sql`):

| Таблица      | Назначение                                              |
|--------------|---------------------------------------------------------|
| `portfolios` | Кэш-баланс пользователя, PK = `user_id`                |
| `positions`  | Открытые позиции, уникальный индекс по `(user_id, symbol)` |
| `orders`     | История ордеров, статусы: `NEW / FILLED / REJECTED / CANCELLED` |
| `trades`     | Исполненные сделки, ссылаются на ордер через `order_id` |

Портфель создаётся автоматически при первом обращении (`GetOrCreatePortfolio`).

## Запуск

Требуется: **Docker** и **Docker Compose**.

Из корня репозитория:

```bash
docker compose up --build portfolio-service postgres kafka
```

Сервис поднимется на `http://localhost:8082`. Postgres и Kafka стартуют автоматически как зависимости.

Чтобы остановить и удалить контейнеры:

```bash
docker compose down
```

## Тестирование

В папке сервиса есть скрипт интеграционных тестов `test_endpoints.py`. Он проверяет все эндпоинты — ~30 сценариев.

```bash
pip install requests
python portfolio-service/test_endpoints.py --base-url http://localhost:8082/api/v1
```

Пример вывода:

```
── System ──
  ✓ GET /system/health → 200
  ✓   status=UP
  ✓   service=portfolio-service

── Balance: Deposit ──
  ✓ POST /balance/deposit → 200
  ✓   balance=10000.00
  ...

========================================
  Passed: 44
  Failed: 0
  Total:  44
========================================
```

Юнит-тесты сервисного и handler слоёв:

```bash
cd portfolio-service
go test ./...
```

## Конфигурация

Все параметры передаются через переменные окружения:

| Переменная      | По умолчанию                                                      | Описание              |
|-----------------|-------------------------------------------------------------------|-----------------------|
| `HTTP_PORT`     | `8080`                                                            | Порт HTTP-сервера     |
| `DATABASE_URL`  | `postgres://postgres:postgres@localhost:5432/portfolio?sslmode=disable` | DSN PostgreSQL   |
| `KAFKA_BROKERS` | `localhost:9092`                                                  | Брокеры Kafka         |
| `KAFKA_TOPIC`   | `trading-events`                                                  | Топик для чтения      |
| `KAFKA_GROUP_ID`| `portfolio-service`                                               | Consumer group ID     |
