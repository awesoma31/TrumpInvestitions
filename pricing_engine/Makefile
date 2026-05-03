MODULE_NAME := pricing_engine

SRC_DIR := src
INC_DIR := include

KDIR ?= /lib/modules/$(shell uname -r)/build
PWD := $(shell pwd)

obj-m := $(MODULE_NAME).o

$(MODULE_NAME)-y := \
	$(SRC_DIR)/pricing_engine_main.o \
	$(SRC_DIR)/pricing_engine_device.o \
	$(SRC_DIR)/pricing_engine_generator.o

ccflags-y := -I$(PWD)/$(INC_DIR) -Wall -Wextra

all: modules test-reader

modules:
	$(MAKE) -C $(KDIR) M=$(PWD) modules

test-reader:
	gcc -Wall -Wextra -Wpedantic tests/test_reader.c -lm -o test_reader

clean:
	rm -rf \
		*.ko \
		.*.o \
		*.o \
		*.mod \
		*.mod.c \
		*.order \
		*.symvers \
		.tmp_versions \
		test_reader \
		$(SRC_DIR)/*.o \
		$(SRC_DIR)/*.mod \
		$(SRC_DIR)/*.mod.c \
		$(SRC_DIR)/.*.cmd \
		.*.cmd

kclean:
	$(MAKE) -C $(KDIR) M=$(PWD) clean

install:
	$(MAKE) -C $(KDIR) M=$(PWD) modules_install
	depmod -a

uninstall:
	rm -f /lib/modules/$(shell uname -r)/extra/$(MODULE_NAME).ko
	depmod -a

