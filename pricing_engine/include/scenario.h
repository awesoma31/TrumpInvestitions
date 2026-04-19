#ifndef SCENARIO_H
#define SCENARIO_H

#include "types.h"

int pe_scenario_load_from_file(const char *path, PeScenario *out_scenario, char *errbuf, size_t errbuf_size);

#endif
