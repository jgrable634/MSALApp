package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class NearbyDevicesActivity extends AppCompatActivity {

    private LinearLayout devicesLayout;

    private MainActivity mainActivity;
    private Map<String, String> foundDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nearby_devices);

        mainActivity = MainActivity.getInstance();
        devicesLayout = findViewById(R.id.devicesLayout);
        foundDevices = GlobalDataHub.getFoundDevices();

        // Find the help button by its ID and add a click listener
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> {
            // On click, navigate to HelpActivity
            String helpMessage = "This screen shows all the devices the app could discover nearby, if you recognize the name of the device," +
                    " just click that button and it will copy the address to the input field in the first screen you were on, then just click the connect button!";
            Intent intent = new Intent(NearbyDevicesActivity.this, HelpActivity.class);
            intent.putExtra("helpMessage", helpMessage);
            startActivity(intent);
        });

        createDeviceButtons();
    }

    public void onBackButtonClick(View view) {
        finish(); // Close the NearbyDevicesActivity and return to the MainActivity
    }

    private void createDeviceButtons() {
        for (String address : foundDevices.keySet()) {

            Button deviceButton = new Button(this);
            String deviceName = foundDevices.get(address);
            deviceButton.setText((deviceName != null?deviceName:"Unknown Name")+", Address: "+address);
            deviceButton.setTag(address);
            deviceButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDeviceButtonClick(v);
                }
            });

            devicesLayout.addView(deviceButton);
        }
    }

    public void onDeviceButtonClick(View view) {
        mainActivity.setDeviceAddressText((String) view.getTag());
        finish();
    }
}