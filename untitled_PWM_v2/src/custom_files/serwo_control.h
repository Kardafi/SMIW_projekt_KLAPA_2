#include <zephyr/kernel.h>
#include <zephyr/logging/log.h>
#include <zephyr/drivers/pwm.h>
#include <zephyr/drivers/gpio.h>

int servo_init(void);
int set_servo_angle(uint8_t duty_cycle_ns);