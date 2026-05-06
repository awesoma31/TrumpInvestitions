#include <linux/kernel.h>
#include <linux/ktime.h>
#include <linux/mutex.h>
#include <linux/random.h>
#include <linux/slab.h>

#include "pricing_engine.h"

static DEFINE_MUTEX(generator_lock);

struct pe_symbol_state pe_symbols[PE_NUM_SYMBOLS] = {
	{"BTCUSDT", 0, 0, 6500000},
	{"AAPL",    0, 0,   17500},
	{"ETHUSDT", 0, 0,  350000},
	{"MSFT",    0, 0,   40000},
	{"TSLA",    0, 0,   17000},
};

int pe_current_symbol_idx = 0;

/* Historical backfill state */
static u64 *history_ts;    /* kmalloc'd array of pre-computed timestamps */
static int  history_total; /* total entries pre-generated                */
static int  history_idx;   /* next entry to serve; == history_total → live */

static s64 random_signed_delta(unsigned long max_abs_value) {
  u32 rnd;
  u32 range;

  if (max_abs_value == 0)
    return 0;

  range = (u32)(max_abs_value * 2 + 1);
  rnd = get_random_u32();

  return (s64)(rnd % range) - (s64)max_abs_value;
}

static unsigned long random_size_units(void) {
  u32 rnd;
  unsigned long min_size;
  unsigned long max_extra;

  min_size = default_size_units > 1 ? default_size_units / 2 : 1;
  max_extra = default_size_units > 1 ? default_size_units : 1;

  rnd = get_random_u32();

  return min_size + (rnd % max_extra);
}

static const char *random_trade_side(void) {
  return (get_random_u32() & 1) ? "buy" : "sell";
}

static void format_money(char *buffer, size_t size, s64 cents) {
  s64 whole;
  s64 fraction;

  whole = cents / 100;
  fraction = cents % 100;

  if (fraction < 0)
    fraction = -fraction;

  scnprintf(buffer, size, "%lld.%02lld", whole, fraction);
}

static void format_size(char *buffer, size_t size, unsigned long units) {
  scnprintf(buffer, size, "%lu.00", units);
}

void pe_generator_free(void) {
  mutex_lock(&generator_lock);
  kfree(history_ts);
  history_ts    = NULL;
  history_total = 0;
  history_idx   = 0;
  mutex_unlock(&generator_lock);
}

void pe_generator_init(void) {
  u64 now_ns;
  u64 ns_per_hour;
  u64 step_ns;
  unsigned long h, i;
  int cap, idx;

  mutex_lock(&generator_lock);

  for (i = 0; i < PE_NUM_SYMBOLS; i++) {
    pe_symbols[i].mid_price_cents = (s64)pe_symbols[i].initial_price_cents;
    pe_symbols[i].sequence = 0;
  }
  pe_current_symbol_idx = 0;

  /* Free previous allocation if re-initialised */
  kfree(history_ts);
  history_ts    = NULL;
  history_total = 0;
  history_idx   = 0;

  if (history_hours == 0 || history_qph == 0)
    goto unlock;

  cap = (int)(history_hours * history_qph);
  if (cap > PE_MAX_HISTORY)
    cap = PE_MAX_HISTORY;

  history_ts = kmalloc_array(cap, sizeof(u64), GFP_KERNEL);
  if (!history_ts) {
    pr_warn("pricing_engine: failed to allocate history buffer, skipping backfill\n");
    goto unlock;
  }

  now_ns      = ktime_get_real_ns();
  ns_per_hour = 3600ULL * NSEC_PER_SEC;
  step_ns     = ns_per_hour / history_qph;
  idx         = 0;

  /* oldest hour first → newest, so ClickHouse receives rows in order */
  for (h = history_hours; h >= 1 && idx < cap; h--) {
    u64 hour_start = now_ns - h * ns_per_hour;
    for (i = 0; i < history_qph && idx < cap; i++, idx++)
      history_ts[idx] = hour_start + i * step_ns;
  }

  history_total = idx;
  pr_info("pricing_engine: backfill ready — %d quotes (%lu h × %lu qph)\n",
          history_total, history_hours, history_qph);

unlock:
  mutex_unlock(&generator_lock);
}

size_t pe_generator_write_quote(char *buffer, size_t buffer_size) {
  const char *symbol;
  u64 event_sequence;
  u64 event_time_ns;
  u64 engine_time_ns;

  s64 mid_cents;
  s64 bid_cents;
  s64 ask_cents;
  s64 last_cents;
  s64 half_spread;

  unsigned long bid_size_units;
  unsigned long ask_size_units;
  unsigned long last_size_units;

  const char *last_trade_side;

  char bid_price[32];
  char ask_price[32];
  char mid_price[32];
  char spread[32];
  char last_price[32];

  char bid_size[32];
  char ask_size[32];
  char last_size[32];

  size_t written;

  mutex_lock(&generator_lock);

  {
    struct pe_symbol_state *sym = &pe_symbols[pe_current_symbol_idx];
    pe_current_symbol_idx = (pe_current_symbol_idx + 1) % PE_NUM_SYMBOLS;

    sym->mid_price_cents += random_signed_delta(max_move_cents);
    if (sym->mid_price_cents < 1)
      sym->mid_price_cents = 1;

    half_spread = (s64)spread_cents / 2;
    bid_cents   = sym->mid_price_cents - half_spread;
    ask_cents   = sym->mid_price_cents + ((s64)spread_cents - half_spread);

    last_cents = sym->mid_price_cents + random_signed_delta(max_last_move_cents);
    if (last_cents < bid_cents)
      last_cents = bid_cents;
    if (last_cents > ask_cents)
      last_cents = ask_cents;

    bid_size_units  = random_size_units();
    ask_size_units  = random_size_units();
    last_size_units = random_size_units();

    last_trade_side = random_trade_side();

    sym->sequence++;
    event_sequence = sym->sequence;
    mid_cents      = sym->mid_price_cents;
    symbol         = sym->symbol;
  }

  if (history_idx < history_total) {
    event_time_ns  = history_ts[history_idx];
    engine_time_ns = history_ts[history_idx];
    history_idx++;

    /* Free buffer once all history has been served */
    if (history_idx == history_total) {
      kfree(history_ts);
      history_ts    = NULL;
      history_total = 0;
      pr_info("pricing_engine: backfill complete, switching to live timestamps\n");
    }
  } else {
    event_time_ns  = ktime_get_real_ns();
    engine_time_ns = ktime_get_real_ns();
  }

  mutex_unlock(&generator_lock);

  format_money(bid_price,  sizeof(bid_price),  bid_cents);
  format_money(ask_price,  sizeof(ask_price),  ask_cents);
  format_money(mid_price,  sizeof(mid_price),  mid_cents);
  format_money(spread,     sizeof(spread),     (s64)spread_cents);
  format_money(last_price, sizeof(last_price), last_cents);

  format_size(bid_size,  sizeof(bid_size),  bid_size_units);
  format_size(ask_size,  sizeof(ask_size),  ask_size_units);
  format_size(last_size, sizeof(last_size), last_size_units);

  written =
      scnprintf(buffer, buffer_size,
                "{\"schema_version\":1,"
                "\"sequence\":%llu,"
                "\"event_type\":\"quote\","
                "\"quote_type\":\"update\","
                "\"event_time_ns\":%llu,"
                "\"engine_time_ns\":%llu,"
                "\"scenario_id\":\"%s\","
                "\"venue\":\"%s\","
                "\"symbol\":\"%s\","
                "\"bid_price\":%s,"
                "\"bid_size\":%s,"
                "\"ask_price\":%s,"
                "\"ask_size\":%s,"
                "\"mid_price\":%s,"
                "\"spread\":%s,"
                "\"last_price\":%s,"
                "\"last_size\":%s,"
                "\"last_trade_side\":\"%s\"}\n",
                event_sequence, event_time_ns, engine_time_ns, PE_SCENARIO_ID,
                PE_VENUE, symbol, bid_price, bid_size, ask_price, ask_size,
                mid_price, spread, last_price, last_size, last_trade_side);

  return written;
}
