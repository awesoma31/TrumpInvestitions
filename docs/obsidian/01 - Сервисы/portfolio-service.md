# portfolio-service

#service #go #kafka #postgresql

| Параметр | Значение |
|---|---|
| Язык | Go 1.25 |
| HTTP Router | Gorilla Mux |
| Порт | **8083** (env: `HTTP_PORT`) |
| База данных | [[PostgreSQL]] (portfolio DB) |
| Messaging | [[Kafka]] consumer (`trading-events`) |
| Паттерн | Handler → Service → Repository |

## Роль в системе

Управляет финансовым состоянием пользователя:
- Хранит денежный баланс и позиции по инструментам
- Считает P&L: реализованный (при продаже) и нереализованный (current price из market-data)
- Потребляет Kafka-события от [[trading-service]], обновляет позиции и баланс после каждой сделки
- Депозит / вывод средств

## API

Полная документация: [[Portfolio API]]

Все эндпоинты требуют заголовок `X-User-Id` (int64).

| Метод | Путь | Описание | Статусы |
|---|---|---|---|
| GET | `/api/v1/portfolio` | Полный снапшот (баланс + позиции + P&L) | 200 |
| GET | `/api/v1/positions` | Список позиций (`?symbol=AAPL`) | 200 |
| GET | `/api/v1/positions/{symbol}` | Позиция по символу | 200/404 |
| GET | `/api/v1/pnl` | Сводка P&L (realized/unrealized/total) | 200 |
| GET | `/api/v1/balance/cash` | Денежный баланс | 200 |
| POST | `/api/v1/balance/deposit` | Пополнение `{amount: "100.00"}` | 200 |
| POST | `/api/v1/balance/withdraw` | Вывод средств | 200/422 |
| GET | `/api/v1/assets/{symbol}/quantity` | Количество актива в позиции | 200 |
| GET | `/api/v1/orders` | История ордеров (`?status&symbol&limit&offset`) | 200 |
| GET | `/api/v1/trades` | История сделок (`?symbol&side&limit&offset`) | 200 |
| GET | `/api/v1/system/health` | — | 200 |
| GET | `/api/v1/system/ready` | Проверяет PostgreSQL | 200/503 |

## Kafka-консьюмер

- **Топик:** `trading-events`
- **Consumer Group:** `portfolio-service`
- **StartOffset:** FirstOffset (читает с начала при первом старте)
- **MaxWait:** 500ms

| Тип события | Действие |
|---|---|
| `ORDER_FILLED` | Обновляет статус ордера + avgFillPrice |
| `ORDER_REJECTED` | Обновляет статус ордера + rejection reason |
| `ORDER_CANCELLED` | Статус → CANCELLED |
| `TRADE_EXECUTED` | Вставляет trade, обновляет позицию, корректирует баланс |

## Бизнес-логика позиций и P&L

**BUY-сделка:**
```
new_avg_price = (old_qty × old_avg + buy_qty × buy_price) / (old_qty + buy_qty)
cash -= gross_amount
```

**SELL-сделка:**
```
realized_pnl += (sell_price - avg_price) × sell_qty
cash += gross_amount
```

**Unrealized P&L** (на каждый запрос): `(current_price - avg_price) × quantity`  
Текущая цена — `GET /quotes/{symbol}` у market-data-service. При недоступности — fallback на avg_price.

## Схема базы данных

```sql
portfolios (
  user_id      BIGINT PRIMARY KEY,
  cash_balance NUMERIC,
  updated_at   TIMESTAMP
)

positions (
  id           BIGINT PRIMARY KEY,
  user_id      BIGINT,
  symbol       VARCHAR,
  quantity     INT,
  avg_price    NUMERIC,
  realized_pnl NUMERIC,
  updated_at   TIMESTAMP,
  UNIQUE (user_id, symbol)    -- для ON CONFLICT upsert
)

orders (
  id               UUID PRIMARY KEY,
  user_id          BIGINT,
  symbol           VARCHAR,
  side             VARCHAR,   -- BUY | SELL
  quantity         INT,
  status           VARCHAR,   -- NEW | FILLED | REJECTED | CANCELLED
  avg_fill_price   NUMERIC,
  rejection_reason VARCHAR,
  created_at       TIMESTAMP,
  updated_at       TIMESTAMP
)

trades (
  id           UUID PRIMARY KEY,
  order_id     UUID,
  user_id      BIGINT,
  symbol       VARCHAR,
  side         VARCHAR,
  quantity     INT,
  price        NUMERIC,
  gross_amount NUMERIC,
  fee_amount   NUMERIC,
  executed_at  TIMESTAMP
)
```

Дополнительные индексы (добавлены для производительности):
- `idx_orders_trade_id ON orders(trade_id) WHERE trade_id IS NOT NULL`
- `idx_orders_created_at ON orders(created_at)`

## Переменные окружения

| Переменная | Дефолт | |
|---|---|---|
| `HTTP_PORT` | 8080 | В compose: 8083 |
| `DATABASE_URL` | `postgres://postgres:postgres@localhost:5432/portfolio?sslmode=disable` | |
| `MARKET_DATA_URL` | `http://market-data-service:8081/api/v1` | |
| `KAFKA_BROKERS` | `localhost:9092` | |
| `KAFKA_TOPIC` | `trading-events` | |
| `KAFKA_GROUP_ID` | `portfolio-service` | |
| `OTEL_EXPORTER_ENDPOINT` | — | jaeger:4317 |

## Связанные страницы

- [[Portfolio API]] — полные эндпоинты
- [[Kafka trading-events]] — формат событий
- [[PostgreSQL]] — схема БД
- [[Создание ордера]] — как portfolio обновляется после сделки
