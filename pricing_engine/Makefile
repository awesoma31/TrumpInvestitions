CC = cc
CFLAGS = -std=c11 -Wall -Wextra -Werror -pedantic -Iinclude
LDFLAGS =

SRC = \
	src/main.c \
	src/scenario.c \
	src/generator.c \
	src/rng.c \
	src/json_writer.c

OBJ = $(SRC:.c=.o)
TARGET = pricing_engine

all: $(TARGET)

$(TARGET): $(OBJ)
	$(CC) $(OBJ) -o $@ $(LDFLAGS)

clean:
	rm -f $(OBJ) $(TARGET)

.PHONY: all clean
