#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "generator.h"
#include "json_writer.h"
#include "scenario.h"

static void pe_print_usage(FILE *out, const char *argv0) {
    fprintf(out, "Usage: %s --scenario <path> [--limit N]\n", argv0);
}

int main(int argc, char **argv) {
    const char *scenario_path = NULL;
    long long limit = -1;
    int i;
    PeScenario scenario;
    PeGenerator generator;
    PeQuoteEvent event;
    char errbuf[256];
    long long emitted = 0;

    for (i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--scenario") == 0) {
            if (i + 1 >= argc) {
                fprintf(stderr, "missing value for --scenario\n");
                pe_print_usage(stderr, argv[0]);
                return 1;
            }
            scenario_path = argv[++i];
            continue;
        }
        if (strcmp(argv[i], "--limit") == 0) {
            char *endptr;
            if (i + 1 >= argc) {
                fprintf(stderr, "missing value for --limit\n");
                pe_print_usage(stderr, argv[0]);
                return 1;
            }
            limit = strtoll(argv[++i], &endptr, 10);
            if (*endptr != '\0' || limit < 0) {
                fprintf(stderr, "invalid value for --limit\n");
                return 1;
            }
            continue;
        }
        if (strcmp(argv[i], "--help") == 0 || strcmp(argv[i], "-h") == 0) {
            pe_print_usage(stdout, argv[0]);
            return 0;
        }
        fprintf(stderr, "unknown argument: %s\n", argv[i]);
        pe_print_usage(stderr, argv[0]);
        return 1;
    }

    if (scenario_path == NULL) {
        fprintf(stderr, "scenario file is required\n");
        pe_print_usage(stderr, argv[0]);
        return 1;
    }

    if (!pe_scenario_load_from_file(scenario_path, &scenario, errbuf, sizeof(errbuf))) {
        fprintf(stderr, "scenario error: %s\n", errbuf);
        return 1;
    }

    pe_generator_init(&generator, &scenario);

    while ((limit < 0 || emitted < limit) && pe_generator_next(&generator, &event)) {
        pe_quote_event_write_json(stdout, &event);
        emitted++;
    }

    return 0;
}
