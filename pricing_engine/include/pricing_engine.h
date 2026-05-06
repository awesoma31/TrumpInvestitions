#ifndef PRICING_ENGINE_H
#define PRICING_ENGINE_H

#include <linux/fs.h>
#include <linux/types.h>

#define PE_DEVICE_NAME  "pricing_engine"
#define PE_BUFFER_LIMIT 4096
/* max bytes a single JSON quote line can occupy (measured + margin) */
#define PE_MAX_LINE_SIZE 512

#define PE_NUM_SYMBOLS 5
#define PE_VENUE       "KERNEL_SIM"
#define PE_SCENARIO_ID "kernel_random_walk"

struct pe_symbol_state {
	const char   *symbol;
	s64           mid_price_cents;
	u64           sequence;
	unsigned long initial_price_cents;
};

extern struct pe_symbol_state pe_symbols[PE_NUM_SYMBOLS];
extern int pe_current_symbol_idx;
extern unsigned long spread_cents;
extern unsigned long max_move_cents;
extern unsigned long default_size_units;
extern unsigned long max_last_move_cents;

/* Historical backfill parameters */
extern unsigned long history_hours;
extern unsigned long history_qph;

/* Maximum pre-computed history entries (history_hours × history_qph) */
#define PE_MAX_HISTORY 8760  /* 365 d × 24 h × 1 qph */

int pe_device_register(void);
void pe_device_unregister(void);

void pe_generator_init(void);
void pe_generator_free(void);
size_t pe_generator_write_quote(char *buffer, size_t buffer_size);

extern const struct file_operations pe_fops;

#endif
