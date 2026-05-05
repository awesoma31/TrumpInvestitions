# pricing_engine (драйвер ядра)

`pricing_engine` — это символьный драйвер Linux, который генерирует синтетические рыночные данные (котировки и сделки) в пространстве ядра и предоставляет их через `/dev/pricing_engine`.

Каждый вызов `read()` возвращает поток JSON-событий (NDJSON), пригодный для прямой загрузки в ClickHouse.

---

## Общее описание

Драйвер моделирует рынок с помощью случайного блуждания:

- цена (`mid`) случайно изменяется
- bid/ask вычисляются через спред
- последняя сделка (`last_price`) находится внутри bid/ask
- объёмы и направление сделки случайны

Формат вывода совместим с:

```text
ClickHouse FORMAT JSONEachRow
````

---

## Пример вывода

```json
{"schema_version":1,"sequence":1,"event_type":"quote","quote_type":"update","event_time_ns":...,"engine_time_ns":...,"scenario_id":"kernel_random_walk","venue":"KERNEL_SIM","symbol":"BTCUSDT","bid_price":64999.75,"bid_size":100.00,"ask_price":65000.25,"ask_size":100.00,"mid_price":65000.00,"spread":0.50,"last_price":65000.10,"last_size":50.00,"last_trade_side":"buy"}
```

---

## Сборка

Требуются заголовки ядра Linux.

```bash
make
```

---

## Загрузка драйвера

```bash
sudo insmod pricing_engine.ko
```

С параметрами:

```bash
sudo insmod pricing_engine.ko \
  start_price_cents=6500000 \
  spread_cents=50 \
  max_move_cents=25 \
  default_size_units=100 \
  max_last_move_cents=10
```

---

## Чтение данных

```bash
head -n 10 /dev/pricing_engine
```

Поток:

```bash
cat /dev/pricing_engine
```

---

## Выгрузка драйвера

```bash
sudo rmmod pricing_engine
```

---

## Интеграция с ClickHouse

Пример вставки:

```bash
head -n 1000 /dev/pricing_engine | curl -sS \
  'http://localhost:8123/?query=INSERT%20INTO%20quotes%20FORMAT%20JSONEachRow' \
  --data-binary @-
```

Или использовать скрипт пакетной загрузки.

---

## Тестирование

Сборка теста:

```bash
make test-reader
```

Запуск:

```bash
./test_reader
```

Проверяет:

* bid ≤ ask
* mid внутри диапазона
* корректный спред
* монотонность sequence

---

## Примечания

* Драйвер предназначен для обучения и тестирования
* Бизнес-логика в ядре не рекомендуется для продакшена
* В реальных системах pricing engine реализуется в user-space
