# pricing_engine

Минимальный pricing engine на C, который:
- загружает детерминированные тестовые сценарии из YAML
- генерирует события котировок
- выводит NDJSON в stdout

## Сборка

```bash
make
````

## Запуск

```bash
./pricing_engine --scenario examples/basic_btc.yaml
./pricing_engine --scenario examples/basic_btc.yaml --limit 2
```

## Формат YAML-сценария

```yaml
scenario_id: basic_btc_regression
venue: BINANCE
symbol: BTCUSDT
seed: 42
start_time_ns: 1713439200000000000
tick_interval_ms: 100
initial_mid_price: 65000.0
initial_spread: 0.50
default_bid_size: 1.20
default_ask_size: 1.00
default_last_size: 0.10
steps:
  - move_mid_by: 0.25
    bid_size: 1.10
    ask_size: 0.90
    last_size: 0.05
    trade_side: buy
```

Поддерживаемые поля верхнего уровня:

* `scenario_id`
* `venue`
* `symbol`
* `seed`
* `start_time_ns`
* `tick_interval_ms`
* `initial_mid_price`
* `initial_spread`
* `default_bid_size`
* `default_ask_size`
* `default_last_size`
* `steps`

Поддерживаемые поля шага:

* `move_mid_by`
* `spread`
* `bid_size`
* `ask_size`
* `last_size`
* `trade_side` (`buy`, `sell`, `none`)

## Выходные данные

Каждая строка — одно JSON-событие котировки.

---

## Интеграция с ClickHouse (локально)

Выход программы — NDJSON, который можно напрямую отправлять в ClickHouse через формат `JSONEachRow`.

### 1. Запуск ClickHouse в Docker

```bash
docker compose up -d
```

### 2. Инициализация схемы

```bash
cat db/init.sql | docker exec -i clickhouse-local clickhouse-client
```

### 3. Отправка данных в БД

Запуск пайплайна:

```bash
./pricing_engine --scenario examples/basic_btc.yaml | ./push_input_to_db.sh
```

---

## Примечания

* NDJSON полностью совместим с форматом ClickHouse `JSONEachRow`
* схема таблицы должна совпадать с JSON (`init.sql`)
* при удалении volume (`docker compose down -v`) таблицу нужно создать заново

