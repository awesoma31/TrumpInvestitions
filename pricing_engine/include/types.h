#ifndef TYPES_H
#define TYPES_H

#include <stddef.h>
#include <stdint.h>

#define PE_MAX_ID_LEN 64
#define PE_MAX_SYMBOL_LEN 32
#define PE_MAX_VENUE_LEN 32
#define PE_MAX_STEPS 10000

typedef enum {
    PE_TRADE_NONE = 0,
    PE_TRADE_BUY = 1,
    PE_TRADE_SELL = 2
} PeTradeSide;

typedef enum {
    PE_QUOTE_SNAPSHOT = 0,
    PE_QUOTE_UPDATE = 1
} PeQuoteType;

typedef struct {
    double move_mid_by;
    double spread;
    double bid_size;
    double ask_size;
    double last_size;
    PeTradeSide trade_side;
} PeScenarioStep;

typedef struct {
    char scenario_id[PE_MAX_ID_LEN];
    char venue[PE_MAX_VENUE_LEN];
    char symbol[PE_MAX_SYMBOL_LEN];
    uint64_t seed;
    uint64_t start_time_ns;
    uint64_t tick_interval_ms;
    double initial_mid_price;
    double initial_spread;
    double default_bid_size;
    double default_ask_size;
    double default_last_size;
    size_t step_count;
    PeScenarioStep steps[PE_MAX_STEPS];
} PeScenario;

typedef struct {
    uint64_t schema_version;
    uint64_t sequence;
    uint64_t event_time_ns;
    uint64_t engine_time_ns;
    char scenario_id[PE_MAX_ID_LEN];
    char venue[PE_MAX_VENUE_LEN];
    char symbol[PE_MAX_SYMBOL_LEN];
    PeQuoteType quote_type;
    double bid_price;
    double bid_size;
    double ask_price;
    double ask_size;
    double mid_price;
    double spread;
    double last_price;
    double last_size;
    PeTradeSide last_trade_side;
} PeQuoteEvent;

#endif
