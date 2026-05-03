# pricing_engine (Kernel Driver)

`pricing_engine` is a Linux character device driver that generates synthetic market data (quotes and trades) directly in kernel space and exposes them via `/dev/pricing_engine`.

Each `read()` call returns a stream of JSON-formatted quote events (NDJSON), which can be directly ingested into systems like ClickHouse.

---

## Overview

The driver simulates a simple market using a random walk model:

- mid price evolves randomly
- bid/ask are derived from spread
- last trade is generated within bid/ask
- sizes and trade side are randomized

Output format is compatible with:

```text
ClickHouse FORMAT JSONEachRow
````

---

## Output Example

```json
{"schema_version":1,"sequence":1,"event_type":"quote","quote_type":"update","event_time_ns":...,"engine_time_ns":...,"scenario_id":"kernel_random_walk","venue":"KERNEL_SIM","symbol":"BTCUSDT","bid_price":64999.75,"bid_size":100.00,"ask_price":65000.25,"ask_size":100.00,"mid_price":65000.00,"spread":0.50,"last_price":65000.10,"last_size":50.00,"last_trade_side":"buy"}
```

---

## Build

Requires Linux kernel headers.

```bash
make
```

---

## Load Driver

```bash
sudo insmod pricing_engine.ko
```

With parameters:

```bash
sudo insmod pricing_engine.ko \
  start_price_cents=6500000 \
  spread_cents=50 \
  max_move_cents=25 \
  default_size_units=100 \
  max_last_move_cents=10
```

---

## Read Data

```bash
head -n 10 /dev/pricing_engine
```

Or continuous stream:

```bash
cat /dev/pricing_engine
```

---

## Unload Driver

```bash
sudo rmmod pricing_engine
```

---

## ClickHouse Integration

Example ingestion:

```bash
head -n 1000 /dev/pricing_engine | curl -sS \
  'http://localhost:8123/?query=INSERT%20INTO%20quotes%20FORMAT%20JSONEachRow' \
  --data-binary @-
```

Or use batch ingestion script.

---

## Test

Build test utility:

```bash
make test-reader
```

Run:

```bash
./test_reader
```

Validates:

* bid ≤ ask
* mid within spread
* correct spread
* sequence monotonicity

---

## Notes

* The driver is intended for educational and testing purposes
* Business logic in kernel space is not recommended for production systems
* Prefer user-space services for real pricing engines
