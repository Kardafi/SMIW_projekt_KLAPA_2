#include "serwo_control.h"

#define LOG_MODULE_NAME servo_control
LOG_MODULE_REGISTER(LOG_MODULE_NAME);

#define PWM_PERIOD_NS         20000000
#define MAX_DUTY_CYCLE        2000000
#define MEAN_DUTY_CYCLE       1500000
#define MIN_DUTY_CYCLE        1000000

uint32_t last_duty_cycle_ns;
uint32_t _duty_cycle_ns;
uint8_t last_direction =0 ; //1 to lewo, 2 to prawo

static const struct pwm_dt_spec pwm_led0 = PWM_DT_SPEC_GET(DT_ALIAS(pwm_led0));

#define GPIO_0_PIN_SENSOR 17     //pin 0.29 czujnik
#define GPIO_0_PIN_MOSFET 15    //pin 0.28 servo mosfet on/off                                
const struct device *gpio_dev = DEVICE_DT_GET(DT_NODELABEL(gpio0)); 

//declaring workitem and handler
static void servo_workhandler(struct k_work *servo_work);
static K_WORK_DEFINE(servo_work, servo_workhandler);


int servo_init(void)
{
    int err = 0;
    LOG_INF("Initializing Motor Control");

    gpio_pin_configure(gpio_dev,GPIO_0_PIN_MOSFET,GPIO_OUTPUT_INACTIVE); //pin 0.28 servo mosfet on/off
    gpio_pin_configure(gpio_dev,GPIO_0_PIN_SENSOR,GPIO_INPUT); //pin 0.29 czujnik

    if (!device_is_ready(pwm_led0.dev)) {
        LOG_ERR("Error: PWM device %s is not ready", pwm_led0.dev->name);
        return -EBUSY;
	}

    // err = pwm_set_dt(&pwm_led0, PWM_PERIOD_NS, 1400000);
    // if (err) {
    //     LOG_ERR("pwm_set_dt returned %d", err);
    // }

    return err;
}

int set_servo_angle(uint8_t _servo_angle)
{
    int err=0;

    LOG_INF("Serwo angle set: %"PRIu8,_servo_angle);
    uint32_t duty_cycle_ns = (uint32_t)(((double)_servo_angle / 180.0) * 1000000.0)+1000000;
    if (duty_cycle_ns > MAX_DUTY_CYCLE) //jezeli za duzo to obcinamy do max wartosci
    duty_cycle_ns=MAX_DUTY_CYCLE;
    else if (duty_cycle_ns < MIN_DUTY_CYCLE) //jezeli za malo to zwiekszamy do min wartosci
    duty_cycle_ns=MIN_DUTY_CYCLE;

    _duty_cycle_ns = duty_cycle_ns;
    k_work_submit(&servo_work);

    return err;
}

static void servo_workhandler(struct k_work *servo_work)
{
    LOG_INF("... Servo working ...");

    if(last_duty_cycle_ns<MIN_DUTY_CYCLE || last_duty_cycle_ns>MAX_DUTY_CYCLE) //gdy pierwszy raz wlaczamy to niezainicjalizowana
        last_duty_cycle_ns = MEAN_DUTY_CYCLE;

    gpio_pin_set(gpio_dev,GPIO_0_PIN_MOSFET,0); //servo on

    uint32_t i=last_duty_cycle_ns; //stare pwm - wartosc poczatkowa dla nas
    if(last_duty_cycle_ns>_duty_cycle_ns){//odejmujemy
        while(i>_duty_cycle_ns){
            if(gpio_pin_get(gpio_dev,GPIO_0_PIN_SENSOR)==true  && last_direction==2){
                break;
                LOG_INF("... SENSOR STOP ...");
            } 
            i-=5;
            pwm_set_dt(&pwm_led0, PWM_PERIOD_NS, i);
        }
        last_duty_cycle_ns = i;
        last_direction =2;
    }else{//dodajemy
        while(i<_duty_cycle_ns){
            if(gpio_pin_get(gpio_dev,GPIO_0_PIN_SENSOR)==true && last_direction==1){
                break;
                LOG_INF("... SENSOR STOP ...");
            }
            i+=5;
            pwm_set_dt(&pwm_led0, PWM_PERIOD_NS, i);
        }
        last_duty_cycle_ns = i;
        last_direction = 1;
    }

    

    //last_duty_cycle_ns = _duty_cycle_ns;
    
    gpio_pin_set(gpio_dev,GPIO_0_PIN_MOSFET,1); //serwo off

     LOG_INF("... Servo stop ...");
}