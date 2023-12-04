package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Map;

public class ViewServiceActivity extends AppCompatActivity {

    private Map<String, ArrayList<String>> servicesMap;
    private Map<String, String> uuidNamesMap;
    private String selectedServiceUUID;
    private String selectedServiceName;

    // Receiver for the custom broadcast
    private BroadcastReceiver characteristicUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String characteristicUUID = intent.getStringExtra("characteristicUUID");
            String updatedCharacteristicName = intent.getStringExtra("updatedCharacteristicName");

            // Update the button text corresponding to the service
            Button characteristicButton = findButtonByUUID(characteristicUUID);
            if (characteristicButton != null) {
                showToast("Name: " + updatedCharacteristicName);
                characteristicButton.setText(updatedCharacteristicName);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_service);

        // Get data from the intent extras
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            selectedServiceUUID = extras.getString("serviceUUID");
            selectedServiceName = extras.getString("serviceName");
        }

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter("com.example.myapplication.CHARACTERISTIC_NAME_UPDATED");
        LocalBroadcastManager.getInstance(this).registerReceiver(characteristicUpdateReceiver, filter);

        servicesMap = GlobalDataHub.getServicesMap();
        uuidNamesMap = GlobalDataHub.getUUIDNamesMap();


        // Initialize UI elements and set the text for the service name and UUID
        TextView serviceNameTextView = findViewById(R.id.serviceNameTextView);
        serviceNameTextView.setText(selectedServiceName + " (" + selectedServiceUUID + ")");

        // Get the characteristics for the selected service from the servicesMap
        ArrayList<String> characteristicUUIDs = servicesMap.get(selectedServiceUUID);

        // Create and add buttons for each characteristic
        LinearLayout characteristicsLayout = findViewById(R.id.characteristicsLayout);
        for (String characteristicUUID : characteristicUUIDs) {
            Button characteristicButton = new Button(this);
            characteristicButton.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            characteristicButton.setText(uuidNamesMap.getOrDefault(characteristicUUID, "Unknown"));
            characteristicButton.setTag(characteristicUUID);
            characteristicButton.setOnClickListener(this::onCharacteristicButtonClick);
            characteristicsLayout.addView(characteristicButton);
        }

        // Find the help button by its ID and add a click listener
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> {
            // On click, navigate to HelpActivity
            String helpMessage = "Here is an in depth look at a service, where you can rename it, and view the characteristics within the service.";
            Intent intent = new Intent(ViewServiceActivity.this, HelpActivity.class);
            intent.putExtra("helpMessage", helpMessage);
            startActivity(intent);
        });
    }

    // Method to handle characteristic button click
    private void onCharacteristicButtonClick(View view) {
        String characteristicUUID = (String) view.getTag();

        // Get the service name from the services map
        String characteristicName = uuidNamesMap.get(characteristicUUID); // Assuming the service name is at index 0

        // Start ViewCharacteristicActivity and pass the characteristic UUID and name as extras
        Intent intent = new Intent(this, ViewCharacteristicActivity.class);
        intent.putExtra("characteristicUUID", characteristicUUID);
        intent.putExtra("characteristicName", characteristicName);
        startActivity(intent);
    }

    // Method to handle rename button click
    public void onRenameButtonClick(View view) {
        EditText renameInput = findViewById(R.id.renameInput);
        String newName = renameInput.getText().toString().trim();

        if (!newName.isEmpty()) {
            GlobalDataHub.updateServiceName(selectedServiceUUID, newName);
            selectedServiceName = newName;

            // Broadcast the updated service name to ServicesOverviewActivity
            Intent intent = new Intent("com.example.myapplication.SERVICE_NAME_UPDATED");
            intent.putExtra("serviceUUID", selectedServiceUUID);
            intent.putExtra("updatedServiceName", newName);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            // Update the service name text view
            TextView serviceNameTextView = findViewById(R.id.serviceNameTextView);
            serviceNameTextView.setText(newName + " (" + selectedServiceUUID + ")");
        } else {
            showToast("Please enter a valid name.");
        }
    }

    // Method to handle back button click
    public void onBackButtonClick(View view) {
        finish(); // Close the ViewServiceActivity and return to the ServicesOverviewActivity
    }

    // Helper method to show toast messages
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Unregister the broadcast receiver to avoid memory leaks
        LocalBroadcastManager.getInstance(this).unregisterReceiver(characteristicUpdateReceiver);
    }

    // Method to find the button by UUID
    private Button findButtonByUUID(String characteristicUUID) {
        // Iterate through the characteristic buttons to find the one with matching UUID
        LinearLayout layout = findViewById(R.id.characteristicsLayout);
        for (int i = 0; i < layout.getChildCount(); i++) {
            View childView = layout.getChildAt(i);
            if (childView instanceof Button) {
                Button button = (Button) childView;
                String tag = button.getTag().toString();
                if (tag.equals(characteristicUUID)) {
                    return button;
                }
            }
        }
        return null; // Button not found
    }
}