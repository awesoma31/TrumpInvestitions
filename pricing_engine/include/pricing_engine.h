#ifndef PRICING_ENGINE_H
#define PRICING_ENGINE_H

#include <linux/fs.h>
#include <linux/types.h>

#define PE_DEVICE_NAME "pricing_engine"
#define PE_BUFFER_LIMIT 4096

#define PE_SYMBOL "BTCUSDT"
#define PE_VENUE "KERNEL_SIM"
#define PE_SCENARIO_ID "kernel_random_walk"

extern unsigned long start_price_cents;
extern unsigned long spread_cents;
extern unsigned long max_move_cents;
extern unsigned long default_size_units;
extern unsigned long max_last_move_cents;

int pe_device_register(void);
void pe_device_unregister(void);

void pe_generator_init(void);
size_t pe_generator_write_quote(char *buffer, size_t buffer_size);

extern const struct file_operations pe_fops;

#endif
