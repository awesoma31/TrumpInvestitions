#include "json_writer.h"

#include <stdio.h>

static const char *pe_quote_type_name(PeQuoteType value) {
    return value == PE_QUOTE_SNAPSHOT ? "snapshot" : "update";
}

static const char *pe_trade_side_name(PeTradeSide value) {
    switch (value) {
        case PE_TRADE_BUY:
            return "buy";
        case PE_TRADE_SELL:
            return "sell";
        case PE_TRADE_NONE:
        default:
            return "none";
    }
}

void pe_quote_event_write_json(FILE *out, const PeQuoteEvent *event) {
    fprintf(out,
            "{"
            "\"schema_version\":%llu,"
            "\"sequence\":%llu,"
            "\"event_type\":\"quote\","
            "\"quote_type\":\"%s\","
            "\"event_time_ns\":%llu,"
            "\"engine_time_ns\":%llu,"
            "\"scenario_id\":\"%s\","
            "\"venue\":\"%s\","
            "\"symbol\":\"%s\","
            "\"bid_price\":%.10f,"
            "\"bid_size\":%.10f,"
            "\"ask_price\":%.10f,"
            "\"ask_size\":%.10f,"
            "\"mid_price\":%.10f,"
            "\"spread\":%.10f,"
            "\"last_price\":%.10f,"
            "\"last_size\":%.10f,"
            "\"last_trade_side\":\"%s\""
            "}\n",
            (unsigned long long)event->schema_version,
            (unsigned long long)event->sequence,
            pe_quote_type_name(event->quote_type),
            (unsigned long long)event->event_time_ns,
            (unsigned long long)event->engine_time_ns,
            event->scenario_id,
            event->venue,
            event->symbol,
            event->bid_price,
            event->bid_size,
            event->ask_price,
            event->ask_size,
            event->mid_price,
            event->spread,
            event->last_price,
            event->last_size,
            pe_trade_side_name(event->last_trade_side));
}
