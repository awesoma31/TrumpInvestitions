# Market Data Service

Go-сервис для агрегации рыночных данных из ClickHouse по спецификации [`swagger/market-data-service.yml`](../swagger/market-data-service.yml).

## Что умеет

- искать инструменты по `symbol`
- отдавать текущие котировки по одному или нескольким тикерам
- строить историю свечей по `last_price`
- собирать агрегированный `bid/ask` стакан из последних записей
- проверять `health` и `readiness`

## Важные допущения

- В текущей схеме ClickHouse нет отдельного справочника инструментов, поэтому `name`, `currency`, `lotSize` и `active` формируются на стороне сервиса.
- В таблице `quotes` нет полного depth-of-market стакана, поэтому `/order-book/{symbol}` агрегирует уровни из последних `bid/ask` значений по инструменту.

## Запуск (все команды из корня репозитория)

### Первый запуск

```bash
# 1. Поднять все сервисы
docker compose up -d --build

# 2. Создать схему ClickHouse (таблица quotes)
db/clickhouse/init_clickhouse.sh

# 3. Собрать pricing_engine
make -C pricing_engine

# 4. Загрузить котировки (выбрать один или несколько сценариев)
./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml | ./pricing_engine/push_input_to_db.sh
./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/aapl_1000.yaml   | ./pricing_engine/push_input_to_db.sh
```

### Повторный запуск / перезагрузка данных

```bash
docker compose up -d --build

# Сначала init (идемпотентен — CREATE TABLE IF NOT EXISTS),
# затем reset (TRUNCATE) — такой порядок безопасен при пересоздании volumes
db/clickhouse/init_clickhouse.sh
scripts/reset-clickhouse.sh

make -C pricing_engine
./pricing_engine/pricing_engine --scenario pricing_engine/examples/generated/btcusdt_1000.yaml | ./pricing_engine/push_input_to_db.sh
```

> **Важно:** `scripts/reset-clickhouse.sh` делает `TRUNCATE TABLE quotes` и требует, чтобы таблица уже существовала. Всегда запускай его **после** `init_clickhouse.sh`.

### Доступные сценарии

| Файл | Символ | Шагов |
|---|---|---|
| `generated/btcusdt_1000.yaml` | BTCUSDT | 1000 |
| `generated/ethusdt_1000.yaml` | ETHUSDT | 1000 |
| `generated/aapl_1000.yaml` | AAPL | 1000 |
| `generated/msft_1000.yaml` | MSFT | 1000 |
| `generated/tsla_1000.yaml` | TSLA | 1000 |
| `basic_btc.yaml` | BTCUSDT | 5 (минимальный регрессионный) |

Можно загрузить несколько сценариев подряд — они добавляются в таблицу, не перезаписывают друг друга.

## Smoke test вместе с pricing_engine

Есть готовый скрипт, который делает всё автоматически:

```bash
./scripts/smoke-test-market-data.sh
```

Он поднимает `clickhouse` и `market-data-service`, инициализирует схему, очищает таблицу, прогоняет `basic_btc.yaml` и вызывает все endpoint'ы.

Можно передать другой сценарий первым аргументом:

```bash
./scripts/smoke-test-market-data.sh pricing_engine/examples/generated/btcusdt_1000.yaml
```

Пропустить очистку таблицы:

```bash
RESET_DB=0 ./scripts/smoke-test-market-data.sh
```

## Переменные окружения

- `HTTP_PORT` - порт HTTP-сервера, по умолчанию `8080`
- `HTTP_BASE_PATH` - базовый префикс API, по умолчанию `/api/v1`
- `CLICKHOUSE_URL` - URL ClickHouse HTTP interface, по умолчанию `http://localhost:8123`
- `CLICKHOUSE_DATABASE` - база ClickHouse, по умолчанию `default`
- `CLICKHOUSE_USER` - пользователь ClickHouse
- `CLICKHOUSE_PASSWORD` - пароль ClickHouse
- `CLICKHOUSE_QUERY_TIMEOUT` - таймаут запросов к ClickHouse, по умолчанию `5s`
- `ORDER_BOOK_SAMPLE_SIZE` - сколько последних записей брать для агрегации стакана, по умолчанию `500`

## Основные endpoint'ы

- `GET /api/v1/instruments`
- `GET /api/v1/instruments/{symbol}`
- `GET /api/v1/quotes?symbols=BTCUSDT`
- `GET /api/v1/quotes/{symbol}`
- `GET /api/v1/history/candles?symbol=BTCUSDT&from=2026-04-19T00:00:00Z&to=2026-04-19T01:00:00Z&interval=1m`
- `GET /api/v1/order-book/{symbol}`
- `GET /api/v1/system/health`
- `GET /api/v1/system/ready`
