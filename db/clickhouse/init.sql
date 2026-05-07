CREATE TABLE IF NOT EXISTS quotes
(
    schema_version UInt8,
    sequence UInt64,

    event_type LowCardinality(String),
    quote_type LowCardinality(String),

    event_time_ns UInt64,
    engine_time_ns UInt64,

    scenario_id LowCardinality(String),
    venue LowCardinality(String),
    symbol LowCardinality(String),

    bid_price Float64,
    bid_size Float64,
    ask_price Float64,
    ask_size Float64,

    mid_price Float64,
    spread Float64,

    last_price Float64,
    last_size Float64,
    last_trade_side LowCardinality(String)
)
ENGINE = MergeTree
ORDER BY (symbol, event_time_ns, sequence)
TTL toDateTime(intDiv(event_time_ns, 1000000000)) + INTERVAL 24 HOUR;

-- Apply TTL to existing table (idempotent)
ALTER TABLE quotes MODIFY TTL toDateTime(intDiv(event_time_ns, 1000000000)) + INTERVAL 24 HOUR;

-- ── quotes_latest ─────────────────────────────────────────────────────────────
-- Latest quote per symbol (ReplacingMergeTree deduplicates by event_time_ns).
-- Used by market-data-service for O(1) quote and order-book lookups instead of
-- expensive full-table argMax scans.
CREATE TABLE IF NOT EXISTS quotes_latest
(
    symbol          LowCardinality(String),
    event_time_ns   UInt64,
    sequence        UInt64,
    bid_price       Float64,
    ask_price       Float64,
    last_price      Float64,
    bid_size        Float64,
    ask_size        Float64,
    last_size       Float64,
    mid_price       Float64,
    spread          Float64,
    last_trade_side LowCardinality(String)
)
ENGINE = ReplacingMergeTree(event_time_ns)
ORDER BY symbol;

CREATE MATERIALIZED VIEW IF NOT EXISTS quotes_latest_mv
TO quotes_latest AS
SELECT
    symbol, event_time_ns, sequence,
    bid_price, ask_price, last_price,
    bid_size, ask_size, last_size,
    mid_price, spread, last_trade_side
FROM quotes;

-- ── candles_1m ────────────────────────────────────────────────────────────────
-- Pre-aggregated 1-minute candles (AggregatingMergeTree).
-- market-data-service re-buckets these states for 5m/15m/1h/1d intervals using
-- Merge combinators — no raw-quotes scan needed for candle history.
CREATE TABLE IF NOT EXISTS candles_1m
(
    symbol    LowCardinality(String),
    bucket_ns UInt64,
    open      AggregateFunction(argMin, Float64, UInt64),
    high      AggregateFunction(max,    Float64),
    low       AggregateFunction(min,    Float64),
    close     AggregateFunction(argMax, Float64, UInt64),
    volume    AggregateFunction(sum,    Float64)
)
ENGINE = AggregatingMergeTree()
ORDER BY (symbol, bucket_ns);

CREATE MATERIALIZED VIEW IF NOT EXISTS candles_1m_mv
TO candles_1m AS
SELECT
    symbol,
    intDiv(event_time_ns, 60000000000) * 60000000000 AS bucket_ns,
    argMinState(last_price, event_time_ns) AS open,
    maxState(last_price)                   AS high,
    minState(last_price)                   AS low,
    argMaxState(last_price, event_time_ns) AS close,
    sumState(last_size)                    AS volume
FROM quotes
GROUP BY symbol, bucket_ns;
