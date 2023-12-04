package com.example.myapplication;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.List;

public class ViewProtocolActivity extends AppCompatActivity {

    private TextView protocolNameHeader;
    private TextView protocolDetails;
    private Button editMarkedButton;
    private Button editWrittenButton;
    private Button backButton;
    private Button deleteButton;
    private Button btnInitiateProtocol;
    private TestProtocol protocol;
    private ProtocolManager manager;
    private static ViewProtocolActivity instance;
    private MainActivity mainActivity = MainActivity.getInstance();

    private BroadcastReceiver protocolUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            CharacteristicSetting newSetting = (CharacteristicSetting) intent.getSerializableExtra("protocolSetting");
            String newMarked = (String) intent.getSerializableExtra("protocolMarked");
            boolean deleteSetting = intent.getBooleanExtra("deleteSetting", false);
            if(newSetting != null){
                if(deleteSetting) {
                    Log.d("DEBUG", "Setting deleted");
                    protocol.removeSetting(newSetting);
                } else {
                    Log.d("DEBUG", "New Setting detected");
                    protocol.addSetting(newSetting);
                }

                Log.d("DEBUG", "All Settings of protocol: "+protocol.getName());
                for(CharacteristicSetting s : protocol.getSettings()){
                    Log.d("DEBUG", "Writing "+s.getUUID() + " with "+s.getValue());
                }
            } else if(newMarked != null){
                Log.d("DEBUG", "New Marked detected");
                protocol.markCharacteristic(newMarked);
                Log.d("DEBUG", "All Marked Characteristics of protocol: "+protocol.getName());
                for(String s : protocol.getMarkedCharacteristics()){
                    Log.d("DEBUG", s);
                }
            } else{
                Log.d("DEBUG", "Updating lists...");
                updateLists();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_protocol);

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter("com.example.myapplication.PROTOCOL_UPDATED");
        LocalBroadcastManager.getInstance(this).registerReceiver(protocolUpdateReceiver, filter);

        // Initialize views
        protocolNameHeader = findViewById(R.id.protocolNameHeader);
        protocolDetails = findViewById(R.id.protocolDetails);
        editMarkedButton = findViewById(R.id.editMarkedButton);
        editWrittenButton = findViewById(R.id.editWrittenButton);
        backButton = findViewById(R.id.backButton);
        deleteButton = findViewById(R.id.deleteButton);

        // Handle the Help Button click (replace 'R.drawable.ic_help' with your help icon resource)
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> {
            // On click, navigate to HelpActivity
            String helpMessage = "This screen is an in depth view of the Test Protocol you selected. Here " +
                    "you can rename the protocol, edit the specific actions it will do, and initiate the protocol. " +
                    "You can also delete the protocol if you don't want it to be saved anymore.";
            Intent intent = new Intent(ViewProtocolActivity.this, HelpActivity.class);
            intent.putExtra("helpMessage", helpMessage);
            startActivity(intent);
        });

        // Rename button and input field to rename the characteristic
        Button renameButton = findViewById(R.id.renameButton);
        renameButton.setOnClickListener(view -> {
            EditText renameInput = findViewById(R.id.renameInput);
            String newName = renameInput.getText().toString().trim();

            if (!newName.isEmpty()) {
                // Update the characteristic name in MainActivity
                if(!manager.checkRename(newName)){
                    showToast("There is already a protocol with that name. Please choose a different name, or delete the conflicting protocol.");
                } else{
                    // Broadcast the updated characteristic name to CharacteristicOverviewActivity
                    Intent intent = new Intent("com.example.myapplication.PROTOCOL_NAME_UPDATED");
                    intent.putExtra("oldProtocolName", protocol.getName());
                    intent.putExtra("newProtocolName", newName);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

                    protocol.setName(newName);

                    // Update the characteristic name text view
                    protocolNameHeader.setText(newName);
                }
            } else {
                showToast("Please enter a valid name.");
            }
        });

        // Get the TestProtocol object passed through Intent
        protocol = (TestProtocol) getIntent().getSerializableExtra("protocol");
        manager = new ProtocolManager(this);
        mainActivity = MainActivity.getInstance();

        // Display the protocol name
        protocolNameHeader.setText(protocol.getName());

        updateLists();

        // Handle the Edit Button click
        editMarkedButton.setOnClickListener(v -> {
            GlobalDataHub.setProtocolStatus(true);
            Intent intent = new Intent(ViewProtocolActivity.this, ServicesOverviewActivity.class);
            startActivity(intent);
        });
        editWrittenButton.setOnClickListener(v -> {
            GlobalDataHub.setProtocolStatus(true);
            Intent intent = new Intent(ViewProtocolActivity.this, ServicesOverviewActivity.class);
            startActivity(intent);
        });
        deleteButton.setOnClickListener(v -> { showConfirmationDialog(); });

        // Handle the Back Button click
        backButton.setOnClickListener(v -> {
            manager.addProtocol(protocol);
            // Broadcast the updated characteristic name to CharacteristicOverviewActivity
            Intent intent = new Intent("com.example.myapplication.PROTOCOL_NAME_UPDATED");
            intent.putExtra("oldProtocolName", protocol.getName());
            intent.putExtra("protocol", protocol);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            finish();
        });

        // Find the Initiate Protocol button
        btnInitiateProtocol = findViewById(R.id.btnInitiateProtocol);

        // Set the click listener for the Initiate Protocol button
        btnInitiateProtocol.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(protocol.getMarkedCharacteristics().size() > 0){
                    manager.addProtocol(protocol);
                    mainActivity.initiateProtocol(protocol);
                    // Create an intent to navigate to MonitorDataActivity
                    Intent intent = new Intent(ViewProtocolActivity.this, MonitorDataActivity.class);

                    // Start the MonitorDataActivity
                    startActivity(intent);
                } else {
                    showToast("Please mark at least one characteristic in the protocol to initiate it.");
                }
            }
        });
        instance = this;
    }

    private void updateLists() {
        protocolDetails.setText("");
        // Display the marked characteristics
        List<String> markedCharacteristics = protocol.getMarkedCharacteristics();
        String textToAdd = "";
        for (String characteristic : markedCharacteristics) {
            textToAdd += GlobalDataHub.getUUIDNamesMap().get(characteristic)+" is MARKED\n";
        }
        protocolDetails.append(textToAdd);
        textToAdd = "";
        // Display the characteristic settings
        List<CharacteristicSetting> characteristicSettings = protocol.getSettings();
        for (CharacteristicSetting setting : characteristicSettings) {
            textToAdd += "Writing " + GlobalDataHub.getUUIDNamesMap().get(setting.getUUID()) + "'s value to "+setting.getValue()+"\n";
        }
        protocolDetails.append(textToAdd);
    }

    public static ViewProtocolActivity getInstance(){
        return instance;
    }

    protected TestProtocol getCurrProtocol(){
        return protocol;
    }
    protected ProtocolManager getManager(){
        return manager;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void showConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Are you sure you want to delete this protocol? This cannot be undone.");
        Context context = this;
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(!manager.deleteProtocol(protocol, context)) showToast("Protocol deletion failed.");
                Intent intent = new Intent("com.example.myapplication.PROTOCOL_NAME_UPDATED");
                intent.putExtra("oldProtocolName", protocol.getName());
                intent.putExtra("deleteProtocol", true);
                LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
                finish();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // User clicked "No", do nothing
            }
        });
        builder.show();
    }

}
