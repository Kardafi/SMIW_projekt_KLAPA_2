package com.example.bleapp2;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


public class MainActivity2 extends AppCompatActivity {

    public static final String configPref = "config";
    private Button returnButton;
    private Button saveButton;
    private EditText openPointText;
    private EditText closePointText;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        returnButton = findViewById(R.id.returnButton);
        saveButton = findViewById(R.id.saveButton);
        openPointText = findViewById(R.id.openPointText);
        closePointText = findViewById(R.id.closePointText);

        returnButton.setOnClickListener(returnButtonListener);
        saveButton.setOnClickListener(saveButtonListener);

        openPointText.setText(String.valueOf(getPreferenceValueOpen()));
        closePointText.setText(String.valueOf(getPreferenceValueClose()));
    }


    private final View.OnClickListener returnButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };
    private final View.OnClickListener saveButtonListener = new View.OnClickListener() {
        public void onClick(View v) {
            if(Integer.parseInt(openPointText.getText().toString())>180 || Integer.parseInt(openPointText.getText().toString())<0){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity2.this, "Zła wartosc pola Otwarty!", Toast.LENGTH_LONG).show();
                    }
                });
            }else if (Integer.parseInt(closePointText.getText().toString())>180 || Integer.parseInt(closePointText.getText().toString())<0){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity2.this, "Zła wartosc pola Zamknięty!", Toast.LENGTH_LONG).show();
                    }
                });
            }else if(Integer.parseInt(openPointText.getText().toString()) >= Integer.parseInt(closePointText.getText().toString()) ){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity2.this, "Wartość pola Otwarty musi być mniejsza od wartości pola Zamknięty", Toast.LENGTH_LONG).show();
                    }
                });
            }else {
                writeToPreference(Integer.parseInt(openPointText.getText().toString()), Integer.parseInt(closePointText.getText().toString()));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity2.this, "Zapisano!", Toast.LENGTH_SHORT).show();
                    }
                });

            }
        }
    };

    public void writeToPreference(int prefOpen, int prefClose)
    {
        SharedPreferences.Editor editor = getSharedPreferences(configPref,0).edit();
        editor.putInt("openPoint", prefOpen);
        editor.putInt("closePoint",prefClose);
        editor.commit();
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

}