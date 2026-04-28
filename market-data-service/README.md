# Market Data Service

Go-сервис для агрегации рыночных данных из ClickHouse по спецификации [`swagger/market-data-service.yml`](/Users/kirill/coding/TrumpInvestitions/swagger/market-data-service.yml).

## Что умеет

- искать инструменты по `symbol`
- отдавать текущие котировки по одному или нескольким тикерам
- строить историю свечей по `last_price`
- собирать агрегированный `bid/ask` стакан из последних записей
- проверять `health` и `readiness`

## Важные допущения

- В текущей схеме ClickHouse нет отдельного справочника инструментов, поэтому `name`, `currency`, `lotSize` и `active` формируются на стороне сервиса.
- В таблице `quotes` нет полного depth-of-market стакана, поэтому `/order-book/{symbol}` агрегирует уровни из последних `bid/ask` значений по инструменту.

## Запуск

1. Поднять ClickHouse:

```bash
docker compose up -d
./db/clickhouse/init_clickhouse.sh
```

Или сразу поднять ClickHouse вместе с API:

```bash
docker compose up -d --build
./db/clickhouse/init_clickhouse.sh
```

2. Загрузить тестовые котировки:

```bash
./pricing_engine --scenario pricing_engine/examples/basic_btc.yaml | ./pricing_engine/push_input_to_db.sh
```

3. Запустить сервис:

```bash
cd market-data-service
go run ./cmd/market-data-service
```

По умолчанию сервис поднимается на `http://localhost:8080/api/v1`.

## Smoke test вместе с pricing_engine

Есть готовый скрипт:

```bash
./scripts/smoke-test-market-data.sh
```

Он делает следующее:

- поднимает `clickhouse` и `market-data-service` через `docker compose`
- инициализирует таблицу `quotes`
- по умолчанию очищает `quotes` через `TRUNCATE TABLE`
- собирает `pricing_engine`
- прогоняет сценарий `pricing_engine/examples/basic_btc.yaml`
- вызывает основные endpoint'ы из swagger и печатает ответы

Если нужен другой сценарий:

```bash
./scripts/smoke-test-market-data.sh pricing_engine/examples/basic_btc.yaml
```

Если не хочешь очищать таблицу перед тестом:

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
