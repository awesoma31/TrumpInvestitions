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
ORDER BY (symbol, event_time_ns, sequence);
