# trading-service

Микросервис для создания и исполнения торговых заявок. Принимает рыночные ордера (BUY/SELL), синхронно исполняет их, сохраняет в PostgreSQL и уведомляет portfolio-service через Kafka.

## Архитектура

```
HTTP-клиент ──► Handler ──► OrderService ──► Repository ──► PostgreSQL
                                  │
                                  └──► KafkaProducer ──► trading-events
```

Три слоя:

- **api** — HTTP-обработчики, CORS, middleware для X-User-Id
- **app** — бизнес-логика: проверки, исполнение ордера, публикация событий
- **repository** — работа с PostgreSQL (все SQL-запросы здесь)

Внешние зависимости через интерфейсы:

- **MarketDataClient** — получение текущей цены и объёма рынка
- **PortfolioClient** — проверка баланса (BUY) и количества акций (SELL)

## HTTP API

Все эндпоинты под префиксом `/api/v1`. Бизнес-маршруты требуют заголовок `X-User-Id: <int64>`. Системные эндпоинты открыты без авторизации.

| Метод | Путь | Описание |
|---|---|---|
| POST | `/orders` | Создать рыночный ордер (BUY/SELL) |
| GET | `/orders` | Список ордеров (`?status=`, `?symbol=`, `?side=`, `?limit=`, `?offset=`) |
| GET | `/orders/{orderId}` | Ордер по ID |
| POST | `/orders/{orderId}/cancel` | Отменить ордер (только в статусе NEW) |
| GET | `/trades` | История исполненных сделок (`?symbol=`, `?side=`, `?limit=`, `?offset=`) |
| GET | `/trades/{tradeId}` | Сделка по ID |
| GET | `/system/health` | Liveness probe |
| GET | `/system/ready` | Readiness probe |

### Тело запроса POST /orders

```json
{
  "symbol": "AAPL",
  "side": "BUY",
  "type": "MARKET",
  "quantity": 10
}
```

## Жизненный цикл ордера

```
POST /orders
    │
    ├── 1. Сохранить ордер (статус NEW)
    ├── 2. Получить цену и объём (MarketDataClient)
    ├── 3. Проверить объём рынка → нет объёма: REJECTED
    ├── 4. BUY: проверить баланс (PortfolioClient) → мало денег: REJECTED
    │   SELL: проверить позицию (PortfolioClient) → нет акций: REJECTED
    ├── 5. Исполнить: FillOrder → статус FILLED
    └── 6. Kafka: ORDER_FILLED + TRADE_EXECUTED → portfolio-service обновляет портфель
```

Ордер исполняется синхронно — ответ на POST уже содержит финальный статус (`FILLED` или `REJECTED`). Kafka-события публикуются в горутине асинхронно.

## Kafka

При каждом изменении статуса ордера в топик `trading-events` публикуется событие:

| Статус ордера | Событие(я) |
|---|---|
| `FILLED` | `ORDER_FILLED` + `TRADE_EXECUTED` |
| `REJECTED` | `ORDER_REJECTED` |
| `CANCELLED` | `ORDER_CANCELLED` |

`TRADE_EXECUTED` содержит данные сделки (цена, сумма, tradeId) и используется portfolio-service для обновления позиций.

## База данных

Одна таблица `orders` (миграция: `migrations/001_create_orders_table.up.sql`). Хранит и ордера, и сделки в одной записи — поля сделки (`trade_id`, `trade_price`, `trade_gross_amount`, `trade_executed_at`) заполняются при статусе `FILLED`.

| Поле | Описание |
|---|---|
| `id` | UUID ордера |
| `user_id` | ID пользователя |
| `symbol` | Тикер (AAPL, TSLA, ...) |
| `side` | BUY / SELL |
| `order_type` | MARKET (единственный поддерживаемый тип) |
| `quantity` | Количество акций |
| `status` | NEW / FILLED / REJECTED / CANCELLED |
| `trade_id` | UUID сделки (заполняется при FILLED) |
| `trade_price` | Цена исполнения |
| `trade_gross_amount` | Сумма сделки |

## Заглушки (MVP)

Оба внешних клиента замоканы:

- **MarketClientMock** — всегда возвращает цену `$0` и объём `1 000 000` (все ордера проходят проверку объёма)
- **PortfolioClientMock** — всегда возвращает баланс `$10 000 000` и `10 000` акций (проверки баланса/позиции всегда проходят)

В production сюда должны подключиться HTTP-клиенты к Market Data Service и portfolio-service.

## Конфигурация

| Переменная | По умолчанию | Описание |
|---|---|---|
| `SERVER_PORT` | `8080` | Порт HTTP-сервера |
| `DATABASE_URL` | `postgres://trading:trading@localhost:5432/trading?sslmode=disable` | DSN PostgreSQL |
| `KAFKA_BROKER` | `localhost:9092` | Брокер Kafka |
| `KAFKA_TOPIC` | `trading-events` | Топик для публикации событий |

## Запуск

Из корня репозитория (запускает postgres, kafka и оба сервиса):

```bash
docker compose up --build trading-service portfolio-service postgres kafka
```

Сервис будет доступен на `http://localhost:8082`.

Миграция БД выполняется автоматически через `entrypoint.sh` при каждом старте контейнера.

## Тестирование

### Юнит-тесты

```bash
cd trading-service
go test ./...
```

### Интеграционные тесты

В корне репозитория находится скрипт `test_portfolio_trading.py`, который тестирует trading-service и portfolio-service совместно, включая сквозной сценарий через Kafka.

```bash
pip install requests
python test_portfolio_trading.py
```

Что проверяется:

| Группа | Сценарии |
|---|---|
| Health | `/system/health`, `/system/ready` |
| Валидация | Отсутствующий/невалидный `X-User-Id` |
| BUY ордер | Создание, статус FILLED, filledQuantity |
| SELL ордер | Создание, статус FILLED |
| Получение ордера | По ID, несуществующий → 404 |
| Отмена | Попытка отменить FILLED ордер → 409 |
| Списки | Ордера и сделки с фильтрами и пагинацией |
| **E2E** | BUY → Kafka → позиция появилась в portfolio-service |

Параметры запуска:

```bash
python test_portfolio_trading.py \
  --portfolio-url http://localhost:8081/api/v1 \
  --trading-url   http://localhost:8082/api/v1 \
  --kafka-wait    3
```
