# pricing_engine

Minimal C pricing engine that:
- loads deterministic test scenarios from YAML
- generates quote events
- prints NDJSON to stdout

## Build

```bash
make
```

## Run

```bash
./pricing_engine --scenario examples/basic_btc.yaml
./pricing_engine --scenario examples/basic_btc.yaml --limit 2
```

## YAML scenario format

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

Supported top-level fields:
- `scenario_id`
- `venue`
- `symbol`
- `seed`
- `start_time_ns`
- `tick_interval_ms`
- `initial_mid_price`
- `initial_spread`
- `default_bid_size`
- `default_ask_size`
- `default_last_size`
- `steps`

Supported step fields:
- `move_mid_by`
- `spread`
- `bid_size`
- `ask_size`
- `last_size`
- `trade_side` (`buy`, `sell`, `none`)

## Output

Each line is one JSON quote event.
