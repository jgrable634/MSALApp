package com.example.myapplication;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiConfiguration;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestProtocolsActivity extends AppCompatActivity {

    private LinearLayout protocolsLayout;
    private List<TestProtocol> foundProtocols;
    private ProtocolManager manager;

    // Receiver for the custom broadcast
    private BroadcastReceiver protocolNameReciever = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String oldProtocolName = intent.getStringExtra("oldProtocolName");
            String newProtocolName = intent.getStringExtra("newProtocolName");
            boolean deleteProtocol = intent.getBooleanExtra("deleteProtocol", false);
            TestProtocol protocol = (TestProtocol) intent.getSerializableExtra("protocol");

            // Update the button text corresponding to the service
            Button protocolButton = findButtonByName(oldProtocolName);
            if (protocolButton != null) {
                if(deleteProtocol) protocolsLayout.removeView(protocolButton);
                else if(protocol != null) protocolButton.setTag(protocol);
                else protocolButton.setText(newProtocolName);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_protocols);

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter("com.example.myapplication.PROTOCOL_NAME_UPDATED");
        LocalBroadcastManager.getInstance(this).registerReceiver(protocolNameReciever, filter);

        protocolsLayout = findViewById(R.id.protocolsLayout);
        manager = new ProtocolManager(this);
        foundProtocols = manager.getAllProtocols();

        // Find the help button by its ID and add a click listener
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> {
            // On click, navigate to HelpActivity
            String helpMessage = "This screen is an overview of all of the Test Protocols saved on this phone. These " +
                    "protocols contain data for specific characteristics to be marked and specific characteristics to " +
                    "write values to. Essentially bringing the BLE device to a specified state before Data Monitoring.";
            Intent intent = new Intent(TestProtocolsActivity.this, HelpActivity.class);
            intent.putExtra("helpMessage", helpMessage);
            startActivity(intent);
        });

        createProtocolButtons();
    }

    public void onBackButtonClick(View view) {
        finish(); // Close the NearbyDevicesActivity and return to the MainActivity
    }

    private void createProtocolButtons() {
        for (TestProtocol protocol : foundProtocols) {

            Button protocolButton = new Button(this);
            String protocolName = protocol.getName();
            protocolButton.setText(protocolName);
            protocolButton.setTag(protocol);
            protocolButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onProtocolButtonClick(v);
                }
            });

            protocolsLayout.addView(protocolButton);
        }
            Button protocolButton = new Button(this);
            String protocolName = "Create new Protocol";
            protocolButton.setText(protocolName);
            protocolButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    createNewProtocol(v);
                }
            });

            protocolsLayout.addView(protocolButton);
    }

    public void onProtocolButtonClick(View view) {
        TestProtocol protocol = (TestProtocol) view.getTag();
        Intent intent = new Intent(TestProtocolsActivity.this, ViewProtocolActivity.class);
        intent.putExtra("protocol", protocol);
        startActivity(intent);
    }

    public void createNewProtocol(View view) {
        TestProtocol protocol = new TestProtocol(manager.getNewName());
        Button protocolButton = new Button(this);
        String protocolName = protocol.getName();
        protocolButton.setText(protocolName);
        protocolButton.setTag(protocol);
        protocolButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onProtocolButtonClick(v);
            }
        });

        protocolsLayout.addView(protocolButton);

        Intent intent = new Intent(TestProtocolsActivity.this, ViewProtocolActivity.class);
        intent.putExtra("protocol", protocol);
        startActivity(intent);
    }

    // Method to find the button by Name
    private Button findButtonByName(String oldName) {
        // Iterate through the service buttons to find the one with matching UUID
        LinearLayout layout = findViewById(R.id.protocolsLayout);
        for (int i = 0; i < layout.getChildCount(); i++) {
            View childView = layout.getChildAt(i);
            if (childView instanceof Button) {
                Button button = (Button) childView;
                String name = button.getText().toString();
                if (name.equals(oldName)) {
                    return button;
                }
            }
        }
        return null; // Button not found
    }

}
