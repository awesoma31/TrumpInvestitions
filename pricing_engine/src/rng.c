#include "rng.h"

void pe_rng_init(PeRng *rng, uint64_t seed) {
    if (seed == 0) {
        seed = 0x9E3779B97F4A7C15ULL;
    }
    rng->state = seed;
}

uint64_t pe_rng_next_u64(PeRng *rng) {
    uint64_t x = rng->state;
    x ^= x >> 12;
    x ^= x << 25;
    x ^= x >> 27;
    rng->state = x;
    return x * 0x2545F4914F6CDD1DULL;
}

double pe_rng_next_unit(PeRng *rng) {
    const uint64_t value = pe_rng_next_u64(rng) >> 11;
    return (double)value / 9007199254740992.0;
}
