# ClickHouse

#infrastructure #database #clickhouse #timeseries

| Параметр | Значение |
|---|---|
| Docker image | `clickhouse/clickhouse-server` |
| HTTP порт | 8123 |
| TCP порт | 9000 |
| База данных | `default` |
| Пользователь | `market_data` |

## Роль в системе

Хранилище временных рядов рыночных котировок. Выбран за:
- Высокую скорость вставки (батчи от [[pricing_engine]])
- Эффективное сжатие числовых данных
- Быстрые аналитические запросы по диапазону времени

## Таблицы

### quotes (основная)

Движок: `MergeTree`  
Ключ сортировки: `(symbol, event_time_ns, sequence)`

| Колонка | Тип | Описание |
|---|---|---|
| `symbol` | `String` | Тикер (AAPL, BTC/USDT) |
| `event_time_ns` | `UInt64` | Время события в наносекундах |
| `sequence` | `UInt64` | Монотонный порядковый номер |
| `bid_price` | `Decimal(38,8)` | Лучший бид |
| `bid_size` | `Decimal(38,8)` | Объём бида |
| `ask_price` | `Decimal(38,8)` | Лучший аск |
| `ask_size` | `Decimal(38,8)` | Объём аска |
| `last_price` | `Decimal(38,8)` | Цена последней сделки |
| `last_size` | `Decimal(38,8)` | Объём последней сделки |
| `last_trade_side` | `String` | BUY / SELL |
| `mid_price` | `Decimal(38,8)` | (bid + ask) / 2 |
| `spread` | `Decimal(38,8)` | ask - bid |
| `scenario_id` | `String` | ID сценария pricing_engine |
| `venue` | `String` | Площадка (NASDAQ, CRYPTO) |
| `event_type` | `String` | QUOTE / TRADE |
| `quote_type` | `String` | NBBO / BBO |

> [!info] Масштаб
> Таблица рассчитана на хранение 5 млрд+ строк с эффективным сжатием через кодеки Delta + LZ4.

## Типичные запросы

```sql
-- Последняя котировка по символу
SELECT bid_price, ask_price, last_price
FROM quotes
WHERE symbol = 'AAPL'
ORDER BY event_time_ns DESC
LIMIT 1;

-- Свечи (OHLCV) за период
SELECT
  toStartOfHour(fromUnixTimestamp64Nano(event_time_ns)) AS ts,
  argMin(last_price, event_time_ns) AS open,
  max(last_price) AS high,
  min(last_price) AS low,
  argMax(last_price, event_time_ns) AS close,
  sum(last_size) AS volume
FROM quotes
WHERE symbol = 'AAPL'
  AND event_time_ns >= toUnixTimestamp64Nano(toDateTime('2026-01-01'))
  AND event_time_ns < toUnixTimestamp64Nano(toDateTime('2026-02-01'))
GROUP BY ts
ORDER BY ts;

-- Стакан (последние уровни bid/ask)
SELECT bid_price, bid_size, ask_price, ask_size
FROM quotes
WHERE symbol = 'AAPL'
ORDER BY event_time_ns DESC
LIMIT 10;
```

## Подключение

```bash
# HTTP API (браузер / curl)
http://localhost:8123/?query=SELECT+1

# clickhouse-client
clickhouse-client --host localhost --port 9000 \
  --user market_data --password market_pass \
  --database default
```

## Связанные страницы

- [[pricing_engine]] — записывает данные
- [[market-data-service]] — читает данные
- [[Market Data API]] — API поверх ClickHouse
