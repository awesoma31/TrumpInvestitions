#include <linux/init.h>
#include <linux/kernel.h>
#include <linux/module.h>

#include "pricing_engine.h"

MODULE_LICENSE("GPL");
MODULE_AUTHOR("pricing_engine");
MODULE_DESCRIPTION("Character driver that generates quote events on read");
MODULE_VERSION("1.0");

unsigned long start_price_cents = 6500000;
unsigned long spread_cents = 50;
unsigned long max_move_cents = 25;
unsigned long default_size_units = 100;
unsigned long max_last_move_cents = 10;
unsigned long history_hours = 24;
unsigned long history_qph = 20;

module_param(start_price_cents, ulong, 0444);
MODULE_PARM_DESC(start_price_cents, "Initial mid price in cents");

module_param(spread_cents, ulong, 0444);
MODULE_PARM_DESC(spread_cents, "Bid/ask spread in cents");

module_param(max_move_cents, ulong, 0444);
MODULE_PARM_DESC(max_move_cents,
                 "Maximum random mid price move per quote in cents");

module_param(default_size_units, ulong, 0444);
MODULE_PARM_DESC(default_size_units, "Default quote size units");

module_param(max_last_move_cents, ulong, 0444);
MODULE_PARM_DESC(max_last_move_cents,
                 "Maximum random last price move from mid price in cents");

module_param(history_hours, ulong, 0444);
MODULE_PARM_DESC(history_hours,
                 "Hours of synthetic history to prepend on load (0 = disabled)");

module_param(history_qph, ulong, 0444);
MODULE_PARM_DESC(history_qph,
                 "Quotes per symbol per hour during history backfill");

static int __init pricing_engine_init(void) {
  int ret;

  pe_generator_init();

  ret = pe_device_register();
  if (ret) {
    pr_err("pricing_engine: failed to register device\n");
    return ret;
  }

  pr_info("pricing_engine: loaded /dev/%s\n", PE_DEVICE_NAME);
  return 0;
}

static void __exit pricing_engine_exit(void) {
  pe_device_unregister();
  pe_generator_free();
  pr_info("pricing_engine: unloaded\n");
}

module_init(pricing_engine_init);
module_exit(pricing_engine_exit);
