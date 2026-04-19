#include "scenario.h"

#include <ctype.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void pe_set_error(char *errbuf, size_t errbuf_size, const char *message) {
    if (errbuf == NULL || errbuf_size == 0) {
        return;
    }
    snprintf(errbuf, errbuf_size, "%s", message);
}

static char *pe_trim(char *s) {
    char *end;
    while (*s != '\0' && isspace((unsigned char)*s)) {
        s++;
    }
    if (*s == '\0') {
        return s;
    }
    end = s + strlen(s) - 1;
    while (end > s && isspace((unsigned char)*end)) {
        *end = '\0';
        end--;
    }
    return s;
}

static int pe_parse_u64(const char *value, uint64_t *out) {
    char *endptr;
    unsigned long long parsed;
    parsed = strtoull(value, &endptr, 10);
    if (endptr == value || *pe_trim(endptr) != '\0') {
        return 0;
    }
    *out = (uint64_t)parsed;
    return 1;
}

static int pe_parse_double(const char *value, double *out) {
    char *endptr;
    double parsed;
    parsed = strtod(value, &endptr);
    if (endptr == value || *pe_trim(endptr) != '\0') {
        return 0;
    }
    *out = parsed;
    return 1;
}

static int pe_parse_trade_side(const char *value, PeTradeSide *out) {
    if (strcmp(value, "buy") == 0) {
        *out = PE_TRADE_BUY;
        return 1;
    }
    if (strcmp(value, "sell") == 0) {
        *out = PE_TRADE_SELL;
        return 1;
    }
    if (strcmp(value, "none") == 0) {
        *out = PE_TRADE_NONE;
        return 1;
    }
    return 0;
}

static int pe_assign_top_level(PeScenario *scenario, const char *key, const char *value) {
    if (strcmp(key, "scenario_id") == 0) {
        snprintf(scenario->scenario_id, sizeof(scenario->scenario_id), "%s", value);
        return 1;
    }
    if (strcmp(key, "venue") == 0) {
        snprintf(scenario->venue, sizeof(scenario->venue), "%s", value);
        return 1;
    }
    if (strcmp(key, "symbol") == 0) {
        snprintf(scenario->symbol, sizeof(scenario->symbol), "%s", value);
        return 1;
    }
    if (strcmp(key, "seed") == 0) {
        return pe_parse_u64(value, &scenario->seed);
    }
    if (strcmp(key, "start_time_ns") == 0) {
        return pe_parse_u64(value, &scenario->start_time_ns);
    }
    if (strcmp(key, "tick_interval_ms") == 0) {
        return pe_parse_u64(value, &scenario->tick_interval_ms);
    }
    if (strcmp(key, "initial_mid_price") == 0) {
        return pe_parse_double(value, &scenario->initial_mid_price);
    }
    if (strcmp(key, "initial_spread") == 0) {
        return pe_parse_double(value, &scenario->initial_spread);
    }
    if (strcmp(key, "default_bid_size") == 0) {
        return pe_parse_double(value, &scenario->default_bid_size);
    }
    if (strcmp(key, "default_ask_size") == 0) {
        return pe_parse_double(value, &scenario->default_ask_size);
    }
    if (strcmp(key, "default_last_size") == 0) {
        return pe_parse_double(value, &scenario->default_last_size);
    }
    return 0;
}

static int pe_assign_step_field(PeScenarioStep *step, const char *key, const char *value) {
    if (strcmp(key, "move_mid_by") == 0) {
        return pe_parse_double(value, &step->move_mid_by);
    }
    if (strcmp(key, "spread") == 0) {
        return pe_parse_double(value, &step->spread);
    }
    if (strcmp(key, "bid_size") == 0) {
        return pe_parse_double(value, &step->bid_size);
    }
    if (strcmp(key, "ask_size") == 0) {
        return pe_parse_double(value, &step->ask_size);
    }
    if (strcmp(key, "last_size") == 0) {
        return pe_parse_double(value, &step->last_size);
    }
    if (strcmp(key, "trade_side") == 0) {
        return pe_parse_trade_side(value, &step->trade_side);
    }
    return 0;
}

int pe_scenario_load_from_file(const char *path, PeScenario *out_scenario, char *errbuf, size_t errbuf_size) {
    FILE *fp;
    char raw_line[512];
    unsigned long line_no = 0;
    int in_steps = 0;
    PeScenario scenario;
    PeScenarioStep *current_step = NULL;

    memset(&scenario, 0, sizeof(scenario));
    snprintf(scenario.scenario_id, sizeof(scenario.scenario_id), "default_scenario");
    snprintf(scenario.venue, sizeof(scenario.venue), "SIM");
    scenario.tick_interval_ms = 100;
    scenario.default_bid_size = 1.0;
    scenario.default_ask_size = 1.0;
    scenario.default_last_size = 0.1;
    scenario.initial_spread = 0.01;

    fp = fopen(path, "r");
    if (fp == NULL) {
        pe_set_error(errbuf, errbuf_size, "failed to open scenario file");
        return 0;
    }

    while (fgets(raw_line, sizeof(raw_line), fp) != NULL) {
        char *line;
        char *colon;
        int indent = 0;
        line_no++;

        raw_line[strcspn(raw_line, "\r\n")] = '\0';
        line = raw_line;
        while (*line == ' ') {
            indent++;
            line++;
        }
        line = pe_trim(line);
        if (*line == '\0' || *line == '#') {
            continue;
        }

        if (!in_steps) {
            if (strcmp(line, "steps:") == 0) {
                in_steps = 1;
                continue;
            }

            colon = strchr(line, ':');
            if (colon == NULL) {
                snprintf(errbuf, errbuf_size, "line %lu: expected key: value", line_no);
                fclose(fp);
                return 0;
            }
            *colon = '\0';
            {
                char *key = pe_trim(line);
                char *value = pe_trim(colon + 1);
                if (!pe_assign_top_level(&scenario, key, value)) {
                    snprintf(errbuf, errbuf_size, "line %lu: invalid top-level field '%s'", line_no, key);
                    fclose(fp);
                    return 0;
                }
            }
            continue;
        }

        if (indent < 2) {
            snprintf(errbuf, errbuf_size, "line %lu: step fields must be indented", line_no);
            fclose(fp);
            return 0;
        }

        if (line[0] == '-') {
            char *item = pe_trim(line + 1);
            if (scenario.step_count >= PE_MAX_STEPS) {
                pe_set_error(errbuf, errbuf_size, "too many steps in scenario");
                fclose(fp);
                return 0;
            }
            current_step = &scenario.steps[scenario.step_count++];
            memset(current_step, 0, sizeof(*current_step));
            current_step->spread = scenario.initial_spread;
            current_step->bid_size = scenario.default_bid_size;
            current_step->ask_size = scenario.default_ask_size;
            current_step->last_size = scenario.default_last_size;
            current_step->trade_side = PE_TRADE_NONE;

            if (*item == '\0') {
                continue;
            }

            colon = strchr(item, ':');
            if (colon == NULL) {
                snprintf(errbuf, errbuf_size, "line %lu: expected - key: value", line_no);
                fclose(fp);
                return 0;
            }
            *colon = '\0';
            {
                char *key = pe_trim(item);
                char *value = pe_trim(colon + 1);
                if (!pe_assign_step_field(current_step, key, value)) {
                    snprintf(errbuf, errbuf_size, "line %lu: invalid step field '%s'", line_no, key);
                    fclose(fp);
                    return 0;
                }
            }
            continue;
        }

        if (current_step == NULL) {
            snprintf(errbuf, errbuf_size, "line %lu: step field without list item", line_no);
            fclose(fp);
            return 0;
        }

        colon = strchr(line, ':');
        if (colon == NULL) {
            snprintf(errbuf, errbuf_size, "line %lu: expected key: value", line_no);
            fclose(fp);
            return 0;
        }
        *colon = '\0';
        {
            char *key = pe_trim(line);
            char *value = pe_trim(colon + 1);
            if (!pe_assign_step_field(current_step, key, value)) {
                snprintf(errbuf, errbuf_size, "line %lu: invalid step field '%s'", line_no, key);
                fclose(fp);
                return 0;
            }
        }
    }

    fclose(fp);

    if (scenario.symbol[0] == '\0') {
        pe_set_error(errbuf, errbuf_size, "scenario must define symbol");
        return 0;
    }
    if (scenario.start_time_ns == 0) {
        pe_set_error(errbuf, errbuf_size, "scenario must define start_time_ns");
        return 0;
    }
    if (scenario.initial_mid_price <= 0.0) {
        pe_set_error(errbuf, errbuf_size, "scenario must define positive initial_mid_price");
        return 0;
    }
    if (scenario.step_count == 0) {
        pe_set_error(errbuf, errbuf_size, "scenario must contain at least one step");
        return 0;
    }

    *out_scenario = scenario;
    return 1;
}
