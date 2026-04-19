#define _POSIX_C_SOURCE 200809L
#include "generator.h"

#include <string.h>
#include <time.h>

static uint64_t pe_now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ULL + (uint64_t)ts.tv_nsec;
}

void pe_generator_init(PeGenerator *gen, const PeScenario *scenario) {
    memset(gen, 0, sizeof(*gen));
    gen->scenario = *scenario;
    pe_rng_init(&gen->rng, scenario->seed);
    gen->next_step_index = 0;
    gen->next_sequence = 1;
    gen->next_event_time_ns = scenario->start_time_ns;
    gen->current_mid_price = scenario->initial_mid_price;
}

int pe_generator_next(PeGenerator *gen, PeQuoteEvent *out_event) {
    if (gen->next_step_index >= gen->scenario.step_count) {
        return 0;
    }

    const PeScenarioStep *step = &gen->scenario.steps[gen->next_step_index];
    double effective_spread = step->spread > 0.0 ? step->spread : gen->scenario.initial_spread;
    double bid_price;
    double ask_price;
    double last_price;

    gen->current_mid_price += step->move_mid_by;
    if (gen->current_mid_price <= 0.0) {
        gen->current_mid_price = 0.00000001;
    }
    if (effective_spread <= 0.0) {
        effective_spread = 0.00000001;
    }

    bid_price = gen->current_mid_price - effective_spread / 2.0;
    ask_price = gen->current_mid_price + effective_spread / 2.0;

    switch (step->trade_side) {
        case PE_TRADE_BUY:
            last_price = ask_price;
            break;
        case PE_TRADE_SELL:
            last_price = bid_price;
            break;
        case PE_TRADE_NONE:
        default:
            last_price = gen->current_mid_price;
            break;
    }

    memset(out_event, 0, sizeof(*out_event));
    out_event->schema_version = 1;
    out_event->sequence = gen->next_sequence;
    out_event->event_time_ns = gen->next_event_time_ns;
    out_event->engine_time_ns = pe_now_ns();
    out_event->quote_type = (gen->next_step_index == 0) ? PE_QUOTE_SNAPSHOT : PE_QUOTE_UPDATE;
    out_event->bid_price = bid_price;
    out_event->bid_size = step->bid_size;
    out_event->ask_price = ask_price;
    out_event->ask_size = step->ask_size;
    out_event->mid_price = gen->current_mid_price;
    out_event->spread = effective_spread;
    out_event->last_price = last_price;
    out_event->last_size = step->last_size;
    out_event->last_trade_side = step->trade_side;
    memcpy(out_event->scenario_id, gen->scenario.scenario_id, sizeof(out_event->scenario_id));
    memcpy(out_event->venue, gen->scenario.venue, sizeof(out_event->venue));
    memcpy(out_event->symbol, gen->scenario.symbol, sizeof(out_event->symbol));

    gen->next_sequence += 1;
    gen->next_step_index += 1;
    gen->next_event_time_ns += gen->scenario.tick_interval_ms * 1000000ULL;
    return 1;
}
