package com.example.bleapp2;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.BluetoothManager;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import java.text.DecimalFormat;
import java.util.List;
import java.util.UUID;


public class MainActivity extends AppCompatActivity {

    //shared preferences
    public static final String configPref = "config";
    private Button setAngleButton;
    private TextView batteryLevel;
    private TextView serwoAngle;
    private TextView connectionStatus;
    private Button refreshStatusButton;
    private Button refreshBatteryButton;
    private SeekBar seekBar;
    private Button closeButton;
    private Button openButton;
    private Button configButton;


    private Handler handler;
    BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice bluetoothDevice;

    private float angleValue;
    private float batteryValue;
    DecimalFormat decimalFormatAngle = new DecimalFormat("##.##");
    DecimalFormat decimalFormatBattery = new DecimalFormat("#.##");
    private static final String TAG = "MainActivity";

    private static final String DEVICE_MAC ="DF:04:3C:F8:C9:86" ;  //"DF:04:3C:F8:C9:86" -> dongle //"CB:69:D8:5F:BB:6E" -> DK
    //UUID of the Remote Service
    private static final UUID UUID_REMOTE_SERVICE = UUID.fromString("e9ea0001-e19b-482d-9293-c7907585fc48"); //service
    //UUID of the PWM Characteristic
    private static final UUID UUID_REMOTE_PWM_NOTIFY_CHRC = UUID.fromString("e9ea0002-e19b-482d-9293-c7907585fc48"); //read notify
    private static final UUID UUID_REMOTE_PWM_WRITE_CHRC = UUID.fromString("e9ea0003-e19b-482d-9293-c7907585fc48"); //write
    private static final UUID UUID_REMOTE_BATTERY_CHRC = UUID.fromString("e9ea0004-e19b-482d-9293-c7907585fc48"); //battery read
    static final UUID DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"); //descriptor uuid default

    private int openPointVal;
    private int closePointVal;


    //-------------------------------------------------------------------------------------------


    @Override
    protected void onDestroy(){
        super.onDestroy();
        Log.i(TAG, "Aplikacja Zamknieta!");
        int err = checkPermission();
        if (err == -1) return;
        if(bluetoothGatt != null)
            bluetoothGatt.disconnect();
    }

    @Override
    protected void onStart() {
        super.onStart();

        openPointVal = getPreferenceValueOpen();
        closePointVal = getPreferenceValueClose();
        if(seekBar.getProgress()>closePointVal) {
            seekBar.setProgress(closePointVal);
            serwoAngle.setText(String.valueOf(closePointVal));
        }
        else if (seekBar.getProgress()<openPointVal){
            seekBar.setProgress(openPointVal);
            serwoAngle.setText(String.valueOf(openPointVal));
        }
        seekBar.setMax(closePointVal);
        seekBar.setMin(openPointVal);
        if (!bluetoothAdapter.isEnabled()) {
            int err = checkPermission();
            if (err == -1) return;
            bluetoothAdapter.enable();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        handler = new Handler(Looper.getMainLooper());

        //uchwyty do elementów interfejsu
        setAngleButton = findViewById(R.id.setAngleButton);
        batteryLevel = findViewById(R.id.batteryLevel);
        serwoAngle = findViewById(R.id.serwoAngle);
        connectionStatus = findViewById(R.id.connectionStatus);
        refreshStatusButton = findViewById(R.id.refreshStatusButton);
        refreshBatteryButton = findViewById(R.id.refreshBatteryButton);
        seekBar = findViewById(R.id.seekBar);
        closeButton= findViewById(R.id.closeButton);
        openButton= findViewById(R.id.openButton);
        configButton = findViewById(R.id.configButton);

        //onclick
        setAngleButton.setOnClickListener(setAngleButtonListener);
        refreshStatusButton.setOnClickListener(connectionStatusButtonListener);
        refreshBatteryButton.setOnClickListener(batteryLevelButtonListener);
        configButton.setOnClickListener(configButtonListener);
        openButton.setOnClickListener(openButtonListener);
        closeButton.setOnClickListener(closeButtonListener);

        //listener
        seekBar.setOnSeekBarChangeListener(seekBarChangeListener);

        //sprawdzamy runtime permissions
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 31) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
            }
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 31) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 101);
            }
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 31) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 102);
            }
        }

        // Inicjalizacja BluetoothAdapter
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        //sprawdzamy czy bt jest i czy jest wlaczony
        if (bluetoothAdapter != null || !bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }

    }

    private void connectToBLEDevice() {
        int err = checkPermission();
        if (err == -1) return;
        bluetoothDevice = bluetoothAdapter.getRemoteDevice(DEVICE_MAC);
        bluetoothGatt = bluetoothDevice.connectGatt(this, false, gattCallback);
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Polaczono z urzadzeniem!");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatus.setText("Status: Połączono");
                        connectionStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.green));
                    }
                });
                int err = checkPermission();
                if (err == -1) return;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Rozlaczono z urzadzeniem");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatus.setText("Status: Rozłączono");
                        connectionStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.red));
                    }
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                List<BluetoothGattService> services = gatt.getServices();

                for (BluetoothGattService service : services) {
                    if (service.getUuid().equals(UUID_REMOTE_SERVICE)) {
                        // Pobieranie charakterystyki na podstawie UUID
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID_REMOTE_PWM_NOTIFY_CHRC); // PWM READ char.
                        int err = checkPermission();
                        if (err == -1) return;

                        for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                            bluetoothGatt.setCharacteristicNotification(characteristic, true);
                            bluetoothGatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            //bluetoothGatt.readDescriptor(descriptor);
                        }
                        break;
                    }
                }
            }
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
            //byte[] value = characteristic.getValue();
            int temp = 0;
            if (value.length >= 1) {
                // Convert the first two bytes to a 16-bit unsigned integer
                temp = value[0] & 0xFF;
                if (characteristic.getUuid().equals(UUID_REMOTE_PWM_NOTIFY_CHRC)) {
                    angleValue = temp;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, "(notify) Odczytano wartosc PWM: " + angleValue);
                            String formattedValue = decimalFormatAngle.format(angleValue);
                            serwoAngle.setText(formattedValue);
                            Toast.makeText(MainActivity.this, "Zaktualizowano wartość PWM!", Toast.LENGTH_SHORT).show();
                            // Tutaj można zaktualizować interfejs użytkownika lub podjąć inne działania
                        }
                    });
                }
            }
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if ((value.length >= 1) && characteristic.getUuid().equals(UUID_REMOTE_PWM_NOTIFY_CHRC)) {
                    angleValue = value[0] & 0xFF;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, " Odczytano wartosc PWM: " + angleValue);
                            String formattedValue = decimalFormatAngle.format(angleValue);
                            serwoAngle.setText(formattedValue);
                            Toast.makeText(MainActivity.this, "Odczytano wartość PWM!", Toast.LENGTH_SHORT).show();
                            // Tutaj można zaktualizować interfejs użytkownika lub podjąć inne działania
                        }
                    });
                } else if ((value.length >= 2) && characteristic.getUuid().equals(UUID_REMOTE_BATTERY_CHRC)) {
                    Log.i(TAG, "value[1]"+(value[1]) +", value[0]" + (value[0] ));
                    int beforeDecimal = value[1] & 0xFF;
                    int afterDecimal = value[0] & 0xFF;
                    float batteryValue = (float) ((beforeDecimal << 8) + afterDecimal) / 1000;
                    //batteryValue = (float) ((beforeDecimal ) + afterDecimal) /100; // Assuming a two-digit decimal part
                    //batteryValue = ((value[1] & 0xFF) << 8 | (value[0] & 0xFF));
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.i(TAG, " Odczytano napiecie baterii: " + batteryValue);
                            String formattedValue = decimalFormatBattery.format(batteryValue);
                            batteryLevel.setText("Napięcie baterii: " + formattedValue + "V");
                            Toast.makeText(MainActivity.this, "Odczytano napięcie baterii!", Toast.LENGTH_SHORT).show();
                            // Tutaj można zaktualizować interfejs użytkownika lub podjąć inne działania
                        }
                    });
                }
            } else {
                Log.e(TAG, "Blad odczytu charakterystyki" + status);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Blad odczytu charakterystyki!", Toast.LENGTH_LONG).show();
                        // Tutaj można zaktualizować interfejs użytkownika lub podjąć inne działania
                    }
                });
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Wysłano wartość charakterystyki pomyślnie");
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Pomyślnie wysłano wartość PWM!", Toast.LENGTH_SHORT).show();
                        // Tutaj można zaktualizować interfejs użytkownika lub podjąć inne działania
                    }
                });
            } else {
                Log.e(TAG, "onCharacteristicWrite error: " + status);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Błąd podczas wysyłania wartości PWM, error: " + status, Toast.LENGTH_LONG).show();
                        // Tutaj można zaktualizować interfejs użytkownika lub podjąć inne działania
                    }
                });
            }
        }

        @Override
        public void onDescriptorRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor descriptor, int status, @NonNull byte[] value) {
            int temp = 0;
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (value.length >= 1) {
                    temp = value[0];
                    if (descriptor.getCharacteristic().equals(UUID_REMOTE_PWM_NOTIFY_CHRC)) {
                        angleValue = temp;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Log.i(TAG, "(odDescriptorRead) Odczytano wartosc PWM: " + angleValue);
                                String formattedValue = decimalFormatAngle.format(angleValue);
                                serwoAngle.setText(formattedValue);
                                // Tutaj można zaktualizować interfejs użytkownika lub podjąć inne działania
                            }
                        });
                    }
                }
            } else {
                Log.e(TAG, "onDescriptorRead error: " + status);
            }
        }
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

        }
    };


    //zwraca szukana charakterystyke
    private BluetoothGattCharacteristic findCharacteristic(UUID characteristicUuid) {
        // Pobieranie listy usług
        List<BluetoothGattService> services = bluetoothGatt.getServices();

        // Szukanie charakterystyki na podstawie UUID
        for (BluetoothGattService service : services) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(characteristicUuid);
            if (characteristic != null) {
                return characteristic;
            }
        }
        return null;
    }

    //odczytuje wybrana charakterystyke
    private void readCharacteristic(UUID characteristicUuid) {
        // Pobieranie charakterystyki na podstawie UUID
        BluetoothGattCharacteristic characteristic = findCharacteristic(characteristicUuid);

        if (characteristic != null) {
            int err = checkPermission();
            if (err == -1) return;
            bluetoothGatt.readCharacteristic(characteristic);
        } else {
            Log.e(TAG, "Charakterystyka o UUID " + characteristicUuid + " nie została znaleziona.");
        }
    }

    private void writeCharacteristicPwm(int _angle) {
        BluetoothGattCharacteristic characteristic = findCharacteristic(UUID_REMOTE_PWM_WRITE_CHRC);
        if (characteristic != null) {
            int err = checkPermission();
            if (err == -1) return;
            byte[] valueBytes = new byte[]{(byte) _angle};
            bluetoothGatt.writeCharacteristic(characteristic, valueBytes, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        } else {
            Log.e(TAG, "Charakterystyka o UUID " + UUID_REMOTE_PWM_WRITE_CHRC + " nie została znaleziona.");
        }
    }

    //onclick listenery
    private final View.OnClickListener connectionStatusButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (bluetoothAdapter.isEnabled()) {
                int err = checkPermission();
                if (err == -1) return;
                if ((bluetoothGatt != null) && (bluetoothManager.getConnectionState(bluetoothGatt.getDevice(), BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED)) {
                    readCharacteristic(UUID_REMOTE_PWM_NOTIFY_CHRC);
                } else {
                    connectToBLEDevice();
                }
            }
        }
    };
    private final View.OnClickListener batteryLevelButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            int err = checkPermission();
            if (err == -1) return;
            if ((bluetoothGatt != null) && (bluetoothManager.getConnectionState(bluetoothGatt.getDevice(), BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED)) {
                readCharacteristic(UUID_REMOTE_BATTERY_CHRC);
            }
        }
    };
    private final View.OnClickListener setAngleButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            int err = checkPermission();
            if (err == -1) return;
            if ((bluetoothGatt != null) && (bluetoothManager.getConnectionState(bluetoothGatt.getDevice(), BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED)) {
                angleValue = seekBar.getProgress();
                int temp = (int) angleValue;
                writeCharacteristicPwm(temp);
            }
        }
    };
    private final View.OnClickListener configButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            OpenView(MainActivity2.class);
        }
    };
    private final View.OnClickListener closeButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            int err = checkPermission();
            if (err == -1) return;
            if ((bluetoothGatt != null) && (bluetoothManager.getConnectionState(bluetoothGatt.getDevice(), BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED)) {
                writeCharacteristicPwm(closePointVal);
            }
        }
    };
    private final View.OnClickListener openButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            int err = checkPermission();
            if (err == -1) return;
            if ((bluetoothGatt != null) && (bluetoothManager.getConnectionState(bluetoothGatt.getDevice(), BluetoothProfile.GATT) == BluetoothProfile.STATE_CONNECTED)) {
                writeCharacteristicPwm(openPointVal);
            }
        }
    };

    //listenery dla seekBar
    OnSeekBarChangeListener seekBarChangeListener = (new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // Wywoływane, gdy użytkownik przestaje przesuwać kciukiem po SeekBar
            angleValue = seekBar.getProgress();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    String formattedValue = decimalFormatAngle.format(angleValue);
                    serwoAngle.setText(formattedValue);
                    // Tutaj można zaktualizować interfejs użytkownika lub podjąć inne działania
                }
            });
        }
    });





    private void OpenView(Class obj){
        Intent intent = new Intent(this,obj);
        startActivity(intent);
    }
    public int getPreferenceValueOpen()
    {
        SharedPreferences sp = getSharedPreferences(configPref,0);
        return sp.getInt("openPoint",1);
    }
    public int getPreferenceValueClose()
    {
        SharedPreferences sp = getSharedPreferences(configPref,0);
        return sp.getInt("closePoint",179);
    }
    private int checkPermission() {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= 31) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 100);
            }
        }
        return 0;
    }
}