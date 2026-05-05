#include <linux/kernel.h>
#include <linux/miscdevice.h>
#include <linux/slab.h>
#include <linux/uaccess.h>

#include "pricing_engine.h"

static ssize_t pe_read(struct file *file, char __user *user_buffer,
                       size_t requested_size, loff_t *offset) {
  char *kernel_buffer;
  size_t used = 0;

  if (requested_size == 0)
    return 0;

  if (requested_size > PE_BUFFER_LIMIT)
    requested_size = PE_BUFFER_LIMIT;

  kernel_buffer = kzalloc(requested_size, GFP_KERNEL);
  if (!kernel_buffer)
    return -ENOMEM;

  while (used < requested_size) {
    size_t written;

    written =
        pe_generator_write_quote(kernel_buffer + used, requested_size - used);

    if (written == 0)
      break;

    if (used + written >= requested_size)
      break;

    used += written;
  }

  if (copy_to_user(user_buffer, kernel_buffer, used)) {
    kfree(kernel_buffer);
    return -EFAULT;
  }

  kfree(kernel_buffer);
  return used;
}

static int pe_open(struct inode *inode, struct file *file) { return 0; }

static int pe_release(struct inode *inode, struct file *file) { return 0; }

const struct file_operations pe_fops = {
    .owner = THIS_MODULE,
    .open = pe_open,
    .read = pe_read,
    .release = pe_release,
};

static struct miscdevice pe_misc_device = {
    .minor = MISC_DYNAMIC_MINOR,
    .name = PE_DEVICE_NAME,
    .fops = &pe_fops,
    .mode = 0666,
};

int pe_device_register(void) { return misc_register(&pe_misc_device); }

void pe_device_unregister(void) { misc_deregister(&pe_misc_device); }
