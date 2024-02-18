#include "battery.h"

#define LOG_MODULE_NAME battery
LOG_MODULE_REGISTER(LOG_MODULE_NAME);

#define ADC_NODE DT_NODELABEL(adc)
static const struct device *adc_dev = DEVICE_DT_GET(ADC_NODE);

#define ADC_RESOLUTION 10
#define ADC_CHANNEL    0
#define ADC_PORT       SAADC_CH_PSELN_PSELN_AnalogInput5 //AIN5 //0.29
#define ADC_REFERENCE  ADC_REF_INTERNAL            //0.6V
#define ADC_GAIN       ADC_GAIN_1_5     //ADC_REFERENCE*5

struct adc_channel_cfg ch0_cfg = {
    .gain = ADC_GAIN,
    .reference = ADC_REFERENCE,
    .acquisition_time = ADC_ACQ_TIME_DEFAULT,
    .channel_id = ADC_CHANNEL,
    #ifdef CONFIG_ADC_NRFX_SAADC
        .input_positive = ADC_PORT
    #endif
};

uint16_t sample_buffer[1];

struct adc_sequence sequence ={
    .channels = BIT(ADC_CHANNEL),
    .buffer = sample_buffer,
    .buffer_size = sizeof(sample_buffer),
    .resolution = ADC_RESOLUTION
};

int init_battery_adc(){
    int err = 0;

    if(!device_is_ready(adc_dev)){
        LOG_ERR("Couldn't init battery ADC!");
        return -1;
    }

    err=adc_channel_setup(adc_dev,&ch0_cfg);
    if(err){
        LOG_ERR("Couldn't setup battery ADC!");
        return -1;
    }
    return err;
}

int read_battery(uint16_t* _battery_val){
    int err= adc_read(adc_dev, &sequence);

     if(err){
        LOG_ERR("Couldn't read battery ADC!");
        return err;
    }

    int32_t value = (int32_t)sample_buffer[0];
    uint16_t adc_vref = adc_ref_internal(adc_dev);
    adc_raw_to_millivolts(adc_vref,ADC_GAIN,ADC_RESOLUTION,&value);

    value = (value * 3)/2;
    
    LOG_INF("Battery voltage: %dmV",value);

    *_battery_val = (uint16_t)value;

    return err;
}