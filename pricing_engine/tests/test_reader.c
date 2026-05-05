#include <errno.h>
#include <fcntl.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define DEVICE_PATH "/dev/pricing_engine"
#define LINE_SIZE 8192
#define TEST_LINES 1000
#define EPS 0.000001

static int extract_double(const char *line, const char *key, double *out) {
  char pattern[64];
  char *pos;

  snprintf(pattern, sizeof(pattern), "\"%s\":", key);
  pos = strstr(line, pattern);
  if (!pos) {
    return -1;
  }

  pos += strlen(pattern);
  errno = 0;
  *out = strtod(pos, NULL);

  return errno == 0 ? 0 : -1;
}

static int extract_u64(const char *line, const char *key,
                       unsigned long long *out) {
  char pattern[64];
  char *pos;

  snprintf(pattern, sizeof(pattern), "\"%s\":", key);
  pos = strstr(line, pattern);
  if (!pos) {
    return -1;
  }

  pos += strlen(pattern);
  errno = 0;
  *out = strtoull(pos, NULL, 10);

  return errno == 0 ? 0 : -1;
}

static int validate_quote(const char *line,
                          unsigned long long previous_sequence) {
  unsigned long long sequence;
  double bid_price;
  double ask_price;
  double mid_price;
  double spread;
  double last_price;

  if (extract_u64(line, "sequence", &sequence) != 0 ||
      extract_double(line, "bid_price", &bid_price) != 0 ||
      extract_double(line, "ask_price", &ask_price) != 0 ||
      extract_double(line, "mid_price", &mid_price) != 0 ||
      extract_double(line, "spread", &spread) != 0 ||
      extract_double(line, "last_price", &last_price) != 0) {
    fprintf(stderr, "Invalid JSON quote fields:\n%s\n", line);
    return -1;
  }

  if (sequence <= previous_sequence) {
    fprintf(stderr, "Sequence is not increasing: prev=%llu current=%llu\n",
            previous_sequence, sequence);
    return -1;
  }

  if (bid_price > ask_price) {
    fprintf(stderr, "Crossed book: bid=%f ask=%f\n", bid_price, ask_price);
    return -1;
  }

  if (mid_price < bid_price || mid_price > ask_price) {
    fprintf(stderr, "Mid price outside bid/ask: bid=%f mid=%f ask=%f\n",
            bid_price, mid_price, ask_price);
    return -1;
  }

  if (fabs((ask_price - bid_price) - spread) > EPS) {
    fprintf(stderr, "Invalid spread: ask-bid=%f spread=%f\n",
            ask_price - bid_price, spread);
    return -1;
  }

  if (last_price < bid_price || last_price > ask_price) {
    fprintf(stderr, "Last price outside bid/ask: bid=%f last=%f ask=%f\n",
            bid_price, last_price, ask_price);
    return -1;
  }

  return 0;
}

int main(void) {
  int fd;
  FILE *stream;
  char line[LINE_SIZE];
  unsigned long long previous_sequence = 0;

  fd = open(DEVICE_PATH, O_RDONLY);
  if (fd < 0) {
    perror("open");
    return EXIT_FAILURE;
  }

  stream = fdopen(fd, "r");
  if (!stream) {
    perror("fdopen");
    close(fd);
    return EXIT_FAILURE;
  }

  for (int i = 0; i < TEST_LINES; ++i) {
    if (!fgets(line, sizeof(line), stream)) {
      fprintf(stderr, "Failed to read quote line\n");
      fclose(stream);
      return EXIT_FAILURE;
    }

    if (validate_quote(line, previous_sequence) != 0) {
      fclose(stream);
      return EXIT_FAILURE;
    }

    extract_u64(line, "sequence", &previous_sequence);
  }

  fclose(stream);

  printf("OK: validated %d quote events\n", TEST_LINES);
  return EXIT_SUCCESS;
}
