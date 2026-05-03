#include <linux/kernel.h>
#include <linux/ktime.h>
#include <linux/mutex.h>
#include <linux/random.h>

#include "pricing_engine.h"

static DEFINE_MUTEX(generator_lock);

static u64 sequence;
static s64 mid_price_cents;

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

void pe_generator_init(void) {
  mutex_lock(&generator_lock);

  sequence = 0;
  mid_price_cents = (s64)start_price_cents;

  mutex_unlock(&generator_lock);
}

size_t pe_generator_write_quote(char *buffer, size_t buffer_size) {
  u64 event_sequence;
  u64 event_time_ns;
  u64 engine_time_ns;

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

  mid_price_cents += random_signed_delta(max_move_cents);

  if (mid_price_cents < 1)
    mid_price_cents = 1;

  half_spread = (s64)spread_cents / 2;
  bid_cents = mid_price_cents - half_spread;
  ask_cents = mid_price_cents + ((s64)spread_cents - half_spread);

  last_cents = mid_price_cents + random_signed_delta(max_last_move_cents);

  if (last_cents < bid_cents)
    last_cents = bid_cents;

  if (last_cents > ask_cents)
    last_cents = ask_cents;

  bid_size_units = random_size_units();
  ask_size_units = random_size_units();
  last_size_units = random_size_units();

  last_trade_side = random_trade_side();

  sequence++;
  event_sequence = sequence;

  event_time_ns = ktime_get_real_ns();
  engine_time_ns = ktime_get_real_ns();

  mutex_unlock(&generator_lock);

  format_money(bid_price, sizeof(bid_price), bid_cents);
  format_money(ask_price, sizeof(ask_price), ask_cents);
  format_money(mid_price, sizeof(mid_price), mid_price_cents);
  format_money(spread, sizeof(spread), (s64)spread_cents);
  format_money(last_price, sizeof(last_price), last_cents);

  format_size(bid_size, sizeof(bid_size), bid_size_units);
  format_size(ask_size, sizeof(ask_size), ask_size_units);
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
                PE_VENUE, PE_SYMBOL, bid_price, bid_size, ask_price, ask_size,
                mid_price, spread, last_price, last_size, last_trade_side);

  return written;
}
