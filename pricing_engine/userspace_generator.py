#!/usr/bin/env python3
"""
Userspace replacement for the pricing_engine kernel module.
Generates synthetic market data (random walk) and pushes to ClickHouse.

On startup performs a 24-hour historical backfill so price charts
have enough data points immediately after launch.

Usage:
    python3 userspace_generator.py
    CLICKHOUSE_URL=http://localhost:8123 python3 userspace_generator.py
"""

import json
import os
import random
import time
import urllib.request
import urllib.error
from urllib.parse import urlencode

CLICKHOUSE_URL    = os.environ.get("CLICKHOUSE_URL",    "http://localhost:8123")
CLICKHOUSE_USER   = os.environ.get("CLICKHOUSE_USER",   "market_data")
CLICKHOUSE_PASSWORD = os.environ.get("CLICKHOUSE_PASSWORD", "market_data_password")
TABLE             = os.environ.get("TABLE",             "quotes")
BATCH_SIZE        = int(os.environ.get("BATCH_SIZE",    "100"))
SLEEP_SECONDS     = float(os.environ.get("SLEEP_SECONDS", "0.1"))
# How many hours of history to backfill on startup (0 to skip)
HISTORY_HOURS     = int(os.environ.get("HISTORY_HOURS", "24"))
# Quotes per symbol per historical hour
HISTORY_QUOTES_PER_HOUR = int(os.environ.get("HISTORY_QUOTES_PER_HOUR", "20"))

SYMBOLS = {
    "AAPL":    {"price": 19500.0,    "spread": 0.05},
    "MSFT":    {"price": 41000.0,    "spread": 0.05},
    "TSLA":    {"price": 17500.0,    "spread": 0.10},
    "BTCUSDT": {"price": 6500000.0,  "spread": 50.0},
    "ETHUSDT": {"price": 350000.0,   "spread": 5.0},
}

state = {sym: {"mid": info["price"], "seq": 0} for sym, info in SYMBOLS.items()}


def _make_quote(symbol: str, timestamp_ns: int) -> dict:
    info = SYMBOLS[symbol]
    s = state[symbol]

    max_move = info["spread"] * 0.5
    s["mid"] += random.uniform(-max_move, max_move)
    s["mid"] = max(s["mid"], info["spread"] * 2)
    s["seq"] += 1

    half = info["spread"] / 2
    bid  = round(s["mid"] - half, 2)
    ask  = round(s["mid"] + half, 2)
    last = round(random.uniform(bid, ask), 2)

    return {
        "schema_version": 1,
        "sequence":        s["seq"],
        "event_type":      "quote",
        "quote_type":      "update",
        "event_time_ns":   timestamp_ns,
        "engine_time_ns":  timestamp_ns,
        "scenario_id":     "userspace_random_walk",
        "venue":           "USERSPACE_SIM",
        "symbol":          symbol,
        "bid_price":       bid,
        "bid_size":        round(random.uniform(10, 200), 2),
        "ask_price":       ask,
        "ask_size":        round(random.uniform(10, 200), 2),
        "mid_price":       round(s["mid"], 2),
        "spread":          info["spread"],
        "last_price":      last,
        "last_size":       round(random.uniform(1, 100), 2),
        "last_trade_side": random.choice(["buy", "sell"]),
    }


def generate_quote(symbol: str) -> dict:
    return _make_quote(symbol, time.time_ns())


def push_to_clickhouse(rows: list) -> bool:
    ndjson = "\n".join(json.dumps(r) for r in rows).encode()
    params = urlencode({
        "query":    f"INSERT INTO {TABLE} FORMAT JSONEachRow",
        "user":     CLICKHOUSE_USER,
        "password": CLICKHOUSE_PASSWORD,
    })
    url = f"{CLICKHOUSE_URL}/?{params}"
    req = urllib.request.Request(url, data=ndjson, method="POST")
    req.add_header("Content-Type", "application/x-ndjson")
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            return resp.status == 200
    except urllib.error.URLError as e:
        print(f"ClickHouse error: {e}")
        return False


def backfill_history():
    """Insert synthetic quotes spread evenly over the past HISTORY_HOURS hours."""
    if HISTORY_HOURS <= 0:
        return

    now_ns    = time.time_ns()
    ns_per_h  = 3_600 * 1_000_000_000
    symbols   = list(SYMBOLS.keys())

    print(f"Backfilling {HISTORY_HOURS}h of history "
          f"({HISTORY_QUOTES_PER_HOUR} quotes/symbol/hour)...")

    batch: list = []
    for h in range(HISTORY_HOURS, 0, -1):          # oldest → newest
        hour_start_ns = now_ns - h * ns_per_h
        step_ns = ns_per_h // HISTORY_QUOTES_PER_HOUR
        for i in range(HISTORY_QUOTES_PER_HOUR):
            ts = hour_start_ns + i * step_ns
            for sym in symbols:
                batch.append(_make_quote(sym, ts))

            if len(batch) >= 500:
                push_to_clickhouse(batch)
                batch.clear()

    if batch:
        push_to_clickhouse(batch)

    total = HISTORY_HOURS * HISTORY_QUOTES_PER_HOUR * len(symbols)
    print(f"Backfill done: ~{total} rows inserted.")


def main():
    symbols = list(SYMBOLS.keys())
    print(f"Starting userspace pricing engine → {CLICKHOUSE_URL}/{TABLE}")
    print(f"Symbols: {', '.join(symbols)}, batch={BATCH_SIZE}, sleep={SLEEP_SECONDS}s")

    backfill_history()

    print("Starting live generation...")
    while True:
        batch = [generate_quote(sym)
                 for sym in symbols
                 for _ in range(max(1, BATCH_SIZE // len(symbols)))]
        if not push_to_clickhouse(batch):
            print("Insert failed, retrying...")
            time.sleep(1)
            continue
        time.sleep(SLEEP_SECONDS)


if __name__ == "__main__":
    main()
