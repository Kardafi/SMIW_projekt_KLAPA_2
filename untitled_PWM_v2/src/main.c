/*
 * Bateria p0.02
 * Serwo   p0.24
 * Czujnik p0.17
 * Serwo mosfet p0.15
 */

#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/devicetree.h>
#include <zephyr/logging/log.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/drivers/uart.h>
#include "serwo_control.h"
#include "remote.h"

#define LOG_MODULE_NAME app
LOG_MODULE_REGISTER(LOG_MODULE_NAME);

const struct device *const dev = DEVICE_DT_GET(DT_CHOSEN(zephyr_console));
uint32_t dtr = 0;

static struct bt_conn *current_conn;

#define LED_BUTTON_NODE DT_ALIAS(led0)
static const struct gpio_dt_spec led_button_spec = GPIO_DT_SPEC_GET(LED_BUTTON_NODE,gpios);

//#define BUTTON_NODE DT_NODELABEL(button0)
#define BUTTON_NODE DT_ALIAS(sw0)
static const struct gpio_dt_spec button_spec = GPIO_DT_SPEC_GET(BUTTON_NODE,gpios);
static struct gpio_callback button_cb;

//declaring workitem and handler
static void sending_workhandler(struct k_work *sending_work);
static K_WORK_DEFINE(sending_work, sending_workhandler);

/* Declarations */
volatile int servo_angle = 90;

void on_connected(struct bt_conn *conn, uint8_t err);
void on_disconnected(struct bt_conn *conn, uint8_t reason);
void on_notif_changed(enum bt_pwm_notifications_enabled status);
void on_data_received(struct bt_conn *conn, const uint8_t *const data, uint16_t len);

struct bt_conn_cb bluetooth_callbacks = {
    .connected = on_connected,
    .disconnected = on_disconnected,
};

struct bt_remote_service_cb remote_callbacks = {
    .notif_changed = on_notif_changed,
    .data_received = on_data_received,
};

/* Callbacks */

void on_data_received(struct bt_conn *conn, const uint8_t *const data, uint16_t len)
{
    uint8_t temp_str[len + 1];
    memcpy(temp_str, data, len);
    temp_str[len] = 0x00;

    LOG_INF("Received data on conn %p. Len: %d", (void *)conn, len);
    LOG_INF("Data: %" PRIu8, data[0]);

    servo_angle = (temp_str[0]);
    set_servo_angle(temp_str[0]);
    set_pwm_change(temp_str[0]); // for ble
    k_work_submit(&sending_work);
}

void on_notif_changed(enum bt_pwm_notifications_enabled status)
{
    if (status == BT_PWM_NOTIFICATIONS_ENABLED)
    {
        LOG_INF("Notifications enabled");
    }
    else
    {
        LOG_INF("Notifications disabled");
    }
}

void on_connected(struct bt_conn *conn, uint8_t err)
{
    if (err)
    {
        LOG_ERR("connection failed, err %d", err);
    }
    LOG_INF("Connected to central");
    current_conn = bt_conn_ref(conn);
    // dk_set_led_on(CONN_STATUS_LED);
}

void on_disconnected(struct bt_conn *conn, uint8_t reason)
{
    LOG_INF("Disconnected (reason: %d)", reason);
    // dk_set_led_off(CONN_STATUS_LED);
    if (current_conn)
    {
        bt_conn_unref(current_conn);
        current_conn = NULL;
    }
}

void button_pressed_callback(const struct device *dev, struct gpio_callback *cb, uint32_t pins){
    LOG_INF("Button pressed!");
    gpio_pin_toggle_dt(&led_button_spec);
    servo_angle +=20;
    if(servo_angle>180)
        servo_angle = 0;
    set_servo_angle(servo_angle);
    set_pwm_change(servo_angle); // for ble
    LOG_INF("Sending PWM notification!");
    k_work_submit(&sending_work);
}

static void sending_workhandler(struct k_work *sending_work)
{
    send_pwm_notification(current_conn, servo_angle);
    LOG_INF("... sending pwm notification DONE ...");
}

/* Configurations */
static int init(void)
{
    int err = 0;

    //USB
	if (usb_enable(NULL)) {
        return -1;
	}
	//bez while bo bedziemy czekac w nieskonczonosc jezeli nie wlaczymy serial monitora
	//while (!dtr) {
        uart_line_ctrl_get(dev, UART_LINE_CTRL_DTR, &dtr);
        k_sleep(K_MSEC(1000));
	//}

    if (!device_is_ready(led_button_spec.port)) {
    	return -1;
    }
    gpio_pin_configure_dt(&led_button_spec,GPIO_OUTPUT_ACTIVE);

    if (!device_is_ready(button_spec.port)) {
    	return -1;
    }
    gpio_pin_configure_dt(&button_spec, GPIO_INPUT);
    gpio_pin_interrupt_configure_dt(&button_spec,GPIO_INT_EDGE_TO_ACTIVE);
    gpio_init_callback(&button_cb,button_pressed_callback,BIT(button_spec.pin));
    gpio_add_callback(button_spec.port,&button_cb);

    return err;
}

int main(void)
{
    int err;
    //LOG_INF("Hello World! %s", CONFIG_BOARD);

    err = init();
    if (err)
    {
        LOG_ERR("init() failed. (err %d)", err);
    }
    err = servo_init();
    if (err)
    {
        LOG_ERR("servo_init() failed. (err %d)", err);
    }
    err = bluetooth_init(&bluetooth_callbacks, &remote_callbacks);
    if (err)
    {
        LOG_ERR("Bluetooth_init() failed. (err %d)", err);
    }

    LOG_INF("Running");

    set_pwm_change(90);

    for (;;)
    {
        k_sleep(K_FOREVER);
    }

    return 0;
}