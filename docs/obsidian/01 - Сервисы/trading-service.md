# trading-service

#service #go #kafka #postgresql

| Параметр | Значение |
|---|---|
| Язык | Go 1.25 |
| HTTP Router | chi/v5 |
| Порт | **8082** (env: `SERVER_PORT`) |
| База данных | [[PostgreSQL]] (trading DB, sqlx) |
| Messaging | [[Kafka]] producer (`trading-events`) |
| Паттерн | Handler → Service → Repository + External clients |

## Роль в системе

Сервис исполнения торговых ордеров:
- Принимает рыночные ордера на покупку/продажу
- **Синхронно** исполняет ордер в рамках одного HTTP-запроса (нет matching engine)
- Параллельно запрашивает стакан (market-data) и баланс/позицию (portfolio)
- Публикует события в Kafka → [[portfolio-service]] обновляет портфель

## Жизненный цикл ордера

```mermaid
stateDiagram-v2
    [*] --> NEW : POST /orders
    NEW --> FILLED : объём ОК + средства ОК
    NEW --> REJECTED : INSUFFICIENT_FUNDS / INSUFFICIENT_ASSETS / INSUFFICIENT_MARKET_VOLUME
    NEW --> CANCELLED : POST /orders/{id}/cancel
    FILLED --> [*]
    REJECTED --> [*]
    CANCELLED --> [*]
```

## API

Полная документация: [[Trading API]]

| Метод | Путь | Описание | Статусы |
|---|---|---|---|
| POST | `/api/v1/orders` | Создать ордер | 201/400/422 |
| GET | `/api/v1/orders` | Список (`?limit&offset&status&symbol&side`) | 200/400 |
| GET | `/api/v1/orders/{orderId}` | Детали ордера | 200/404 |
| POST | `/api/v1/orders/{orderId}/cancel` | Отменить (только из NEW) | 200/404/409 |
| GET | `/api/v1/trades` | Список сделок | 200/400 |
| GET | `/api/v1/trades/{tradeId}` | Детали сделки | 200/404 |
| GET | `/api/v1/system/health` | — | 200 |
| GET | `/api/v1/system/ready` | Проверяет PostgreSQL | 200/503 |

Заголовок `X-User-Id` **обязателен** (проставляет [[auth-gateway]]).

```json
// POST /orders
{ "symbol": "AAPL", "side": "BUY", "type": "MARKET", "quantity": 10 }
```

**Коды отказа 422:** `INSUFFICIENT_FUNDS`, `INSUFFICIENT_ASSETS`, `INSUFFICIENT_MARKET_VOLUME`

## Логика исполнения ордера

1. INSERT order со статусом NEW
2. **Параллельно** (goroutines):
   - `GET /order-book/{symbol}?depth=20` → best ask/bid + доступный объём
   - `GET /balance/cash` (BUY) или `GET /assets/{symbol}/quantity` (SELL)
3. Валидация объёма, баланса/позиции
4. Если ОК: UPDATE статус=FILLED, генерируем trade_id
5. Async: публикуем `ORDER_FILLED` + `TRADE_EXECUTED` в Kafka

## Зависимости

```mermaid
graph LR
    AG["auth-gateway"] -->|proxy| TS["trading-service :8082"]
    TS -->|GET /order-book| MDS["market-data-service"]
    TS -->|GET /balance/cash\nGET /assets/{sym}/qty| PS["portfolio-service"]
    TS -->|publish events| KAFKA["Kafka\ntrading-events"]
    TS --> PG[("PostgreSQL\ntrading DB")]
    TS -.->|traces| J["Jaeger"]
```

## Схема базы данных

**Одна таблица `orders`** (сделки — строки где `trade_id IS NOT NULL`):

```sql
orders (
  id               UUID PRIMARY KEY,
  user_id          BIGINT NOT NULL,
  symbol           VARCHAR(32) NOT NULL,
  side             VARCHAR(4),        -- BUY | SELL
  order_type       VARCHAR(10),       -- MARKET (единственный тип)
  quantity         INT,
  status           VARCHAR(20),       -- NEW | FILLED | REJECTED | CANCELLED
  filled_quantity  INT,
  avg_fill_price   VARCHAR(50),
  rejection_reason VARCHAR(100),
  created_at       TIMESTAMPTZ,
  updated_at       TIMESTAMPTZ,
  filled_at        TIMESTAMPTZ,
  cancelled_at     TIMESTAMPTZ,
  trade_id         UUID UNIQUE,       -- заполняется при FILLED
  trade_price      VARCHAR(50),
  trade_gross_amount VARCHAR(50),
  trade_fee_amount   VARCHAR(50),
  trade_executed_at  TIMESTAMPTZ
)
```

Индексы: `(user_id)`, `(user_id, status)`, `(user_id) WHERE trade_id IS NOT NULL`, `(trade_id) WHERE trade_id IS NOT NULL`

## Kafka (producer only)

- **Топик:** `trading-events` (env: `KAFKA_TOPIC`)
- **Ключ сообщения:** `order_id`
- **События:** `ORDER_FILLED`, `ORDER_REJECTED`, `ORDER_CANCELLED`, `TRADE_EXECUTED`

## Переменные окружения

| Переменная | Дефолт | |
|---|---|---|
| `SERVER_PORT` | 8080 | В compose: 8082 |
| `DATABASE_URL` | `postgres://trading:trading@localhost:5432/trading?sslmode=disable` | |
| `KAFKA_BROKER` | `localhost:9092` | |
| `KAFKA_TOPIC` | `trading-events` | |
| `MARKET_DATA_URL` | `http://market-data-service:8081/api/v1` | |
| `PORTFOLIO_URL` | `http://portfolio-service:8080` | |
| `OTEL_EXPORTER_ENDPOINT` | — | jaeger:4317 |

## Связанные страницы

- [[Trading API]] — полные эндпоинты
- [[Kafka trading-events]] — формат публикуемых событий
- [[Создание ордера]] — полный flow
- [[portfolio-service]] — кто потребляет события
