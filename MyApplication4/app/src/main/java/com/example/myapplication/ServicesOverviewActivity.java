package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ServicesOverviewActivity extends AppCompatActivity {

    private LinearLayout servicesLayout;

    private Map<String, ArrayList<String>> servicesMap;
    private Map<String, String> uuidNamesMap;

    // Receiver for the custom broadcast
    private BroadcastReceiver serviceUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String updatedServiceUUID = intent.getStringExtra("serviceUUID");
            String updatedServiceName = intent.getStringExtra("updatedServiceName");

            // Update the button text corresponding to the service
            Button serviceButton = findButtonByUUID(updatedServiceUUID);
            if (serviceButton != null) {
                serviceButton.setText(updatedServiceName);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_services_overview);

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter("com.example.myapplication.SERVICE_NAME_UPDATED");
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceUpdateReceiver, filter);


        servicesLayout = findViewById(R.id.servicesLayout);

        servicesMap = GlobalDataHub.getServicesMap();
        uuidNamesMap = GlobalDataHub.getUUIDNamesMap();

        // Find the help button by its ID and add a click listener
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> {
            // On click, navigate to HelpActivity
            String helpMessage = "Select the service you wish to view, this will take you to an in depth look at the service where you can " +
                    "see the characteristics it holds.";
            Intent intent = new Intent(ServicesOverviewActivity.this, HelpActivity.class);
            intent.putExtra("helpMessage", helpMessage);
            startActivity(intent);
        });

        createServiceButtons();
    }

    public void onBackButtonClick(View view) {
        GlobalDataHub.setProtocolStatus(false);
        finish(); // Close the ServicesOverviewActivity and return to the MainActivity
    }

    private void createServiceButtons() {
        for (String serviceUuid : servicesMap.keySet()) {
            ArrayList<String> characteristicUuids = servicesMap.get(serviceUuid);
            String serviceName = uuidNamesMap.get(serviceUuid);
            if (serviceName == null) {
                serviceName = "Unknown Service";
            }

            Button serviceButton = new Button(this);
            serviceButton.setText(serviceName);
            serviceButton.setTag(serviceUuid);
            serviceButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onServiceButtonClick(v);
                }
            });

            servicesLayout.addView(serviceButton);
        }
    }

    public void onServiceButtonClick(View view) {
        // Get the service UUID from the button's tag (assuming the UUID is set as a tag for the button)
        String serviceUUID = (String) view.getTag();

        // Get the service name from the services map
        String serviceName = uuidNamesMap.get(serviceUUID); // Assuming the service name is at index 0

        // Start ViewServiceActivity and pass the service UUID and name as extras
        Intent intent = new Intent(this, ViewServiceActivity.class);
        intent.putExtra("serviceUUID", serviceUUID);
        intent.putExtra("serviceName", serviceName);
        startActivity(intent);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister the broadcast receiver to avoid memory leaks
        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceUpdateReceiver);
    }

    // Method to find the button by UUID
    private Button findButtonByUUID(String serviceUUID) {
        // Iterate through the service buttons to find the one with matching UUID
        LinearLayout layout = findViewById(R.id.servicesLayout);
        for (int i = 0; i < layout.getChildCount(); i++) {
            View childView = layout.getChildAt(i);
            if (childView instanceof Button) {
                Button button = (Button) childView;
                String tag = button.getTag().toString();
                if (tag.equals(serviceUUID)) {
                    return button;
                }
            }
        }
        return null; // Button not found
    }

}
