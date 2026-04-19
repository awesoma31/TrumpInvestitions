#ifndef JSON_WRITER_H
#define JSON_WRITER_H

#include <stdio.h>

#include "types.h"

void pe_quote_event_write_json(FILE *out, const PeQuoteEvent *event);

#endif
