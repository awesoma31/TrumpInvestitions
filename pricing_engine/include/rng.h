#ifndef RNG_H
#define RNG_H

#include <stdint.h>

typedef struct {
    uint64_t state;
} PeRng;

void pe_rng_init(PeRng *rng, uint64_t seed);
uint64_t pe_rng_next_u64(PeRng *rng);
double pe_rng_next_unit(PeRng *rng);

#endif
