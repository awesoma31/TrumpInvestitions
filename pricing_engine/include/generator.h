#ifndef GENERATOR_H
#define GENERATOR_H

#include "rng.h"
#include "types.h"

typedef struct {
    PeScenario scenario;
    PeRng rng;
    size_t next_step_index;
    uint64_t next_sequence;
    uint64_t next_event_time_ns;
    double current_mid_price;
} PeGenerator;

void pe_generator_init(PeGenerator *gen, const PeScenario *scenario);
int pe_generator_next(PeGenerator *gen, PeQuoteEvent *out_event);

#endif
