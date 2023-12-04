package com.example.myapplication;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class ViewCharacteristicActivity extends AppCompatActivity {

    private String selectedCharacteristicUUID;
    private String selectedCharacteristicName;
    private MainActivity mainActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_characteristic);

        // Get data from the intent extras
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            selectedCharacteristicUUID = extras.getString("characteristicUUID");
            selectedCharacteristicName = extras.getString("characteristicName");
        }

        // Get the instance of MainActivity from the intent
        mainActivity = MainActivity.getInstance();

        // Set the characteristic name and UUID at the top
        TextView characteristicNameTextView = findViewById(R.id.characteristicNameTextView);
        characteristicNameTextView.setText(selectedCharacteristicName + " (" + selectedCharacteristicUUID + ")");

        // Set the initial value for the read and write fields (empty by default)
        TextView readValueTextView = findViewById(R.id.readValueTextView);
        EditText writeValueEditText = findViewById(R.id.writeValueEditText);

        // Back button to return to ViewService
        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(view -> {
            if(GlobalDataHub.isEditingProtocol()){
                // Broadcast the Protocol update to ViewProtocolActivity
                Intent intent = new Intent("com.example.myapplication.PROTOCOL_UPDATED");
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            }
            finish();
        });

        // Rename button and input field to rename the characteristic
        Button renameButton = findViewById(R.id.renameButton);
        renameButton.setOnClickListener(view -> {
            EditText renameInput = findViewById(R.id.renameInput);
            String newName = renameInput.getText().toString().trim();

            if (!newName.isEmpty()) {
                // Update the characteristic name in MainActivity
                GlobalDataHub.updateCharacteristicName(selectedCharacteristicUUID, newName);
                selectedCharacteristicName = newName;

                // Broadcast the updated characteristic name to CharacteristicOverviewActivity
                Intent intent = new Intent("com.example.myapplication.CHARACTERISTIC_NAME_UPDATED");
                intent.putExtra("characteristicUUID", selectedCharacteristicUUID);
                intent.putExtra("updatedCharacteristicName", newName);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

                // Update the characteristic name text view
                characteristicNameTextView.setText(newName + " (" + selectedCharacteristicUUID + ")");
            } else {
                showToast("Please enter a valid name.");
            }
        });

        // Read button to read the characteristic value
        Button readButton = findViewById(R.id.readButton);
        readButton.setOnClickListener(view -> {
            if (mainActivity.isCharacteristicReadable(selectedCharacteristicUUID)) {
                mainActivity.readCharacteristic(selectedCharacteristicUUID, readValueTextView);
            } else {
                showToast("Cannot read characteristic due to lack of permissions.");
            }
        });

        // Write button to write a new value to the characteristic
        Button writeButton = findViewById(R.id.writeButton);
        writeButton.setOnClickListener(view -> {
            if (mainActivity.isCharacteristicWritable(selectedCharacteristicUUID)) {
                String valueToWrite = writeValueEditText.getText().toString().trim();
                if(GlobalDataHub.isEditingProtocol()){
                    // Broadcast the updated characteristic name to CharacteristicOverviewActivity
                    Intent intent = new Intent("com.example.myapplication.PROTOCOL_UPDATED");
                    intent.putExtra("protocolSetting", new CharacteristicSetting(selectedCharacteristicUUID, valueToWrite));
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                } else {
                    mainActivity.writeCharacteristic(selectedCharacteristicUUID, valueToWrite);
                }
            } else {
                showToast("Cannot write to characteristic due to lack of permissions.");
            }
        });

        Button deleteSettingButton = findViewById(R.id.deleteSettingButton);
        deleteSettingButton.setOnClickListener(view -> {
            // Broadcast the updated characteristic name to CharacteristicOverviewActivity
            Intent intent = new Intent("com.example.myapplication.PROTOCOL_UPDATED");
            intent.putExtra("protocolSetting", new CharacteristicSetting(selectedCharacteristicUUID, null));
            intent.putExtra("deleteSetting", true);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        });


        // Write button to write a new value to the characteristic
        Button markButton = findViewById(R.id.markButton);
        markButton.setOnClickListener(view -> {
            if (mainActivity.isCharacteristicReadable(selectedCharacteristicUUID)) {
                if(GlobalDataHub.isEditingProtocol()){
                    // Broadcast the updated characteristic name to CharacteristicOverviewActivity
                    Intent intent = new Intent("com.example.myapplication.PROTOCOL_UPDATED");
                    intent.putExtra("protocolMarked", selectedCharacteristicUUID);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                    ViewProtocolActivity protocolViewer = ViewProtocolActivity.getInstance();
                    TestProtocol protocol = protocolViewer.getCurrProtocol();
                    // Logic is counterintuitive because this is simpler than having the background thread call back to this.
                    // Testing has proven consistently that this method works.
                    if(!protocol.containsMarked(selectedCharacteristicUUID)){
                        markButton.setText("Remove Characteristic from Monitoring");
                    } else markButton.setText("Mark Characteristic for Monitoring");
                } else{
                    GlobalDataHub.markCharacteristic(selectedCharacteristicUUID);
                    if(GlobalDataHub.isCharacteristicMarked(selectedCharacteristicUUID)){
                        markButton.setText("Remove Characteristic from Monitoring");
                    } else markButton.setText("Mark Characteristic for Monitoring");
                }
            } else {
                showToast("Cannot mark characteristic due to lack of reading permissions.");
            }
        });
        if(GlobalDataHub.isEditingProtocol()){
            ViewProtocolActivity protocolViewer = ViewProtocolActivity.getInstance();
            TestProtocol protocol = protocolViewer.getCurrProtocol();
            if(protocol.containsMarked(selectedCharacteristicUUID)){
                markButton.setText("Remove Characteristic from Monitoring");
            }
        } else{
            if(GlobalDataHub.isCharacteristicMarked(selectedCharacteristicUUID)){
                markButton.setText("Remove Characteristic from Monitoring");
            }
        }


        // Find the help button by its ID and add a click listener
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> {
            // On click, navigate to HelpActivity
            String helpMessage = "Here is an in depth look at whichever characteristic you selected. Here you can read or write the values of" +
                    " characteristics manually. You can also rename it just like services. You can also mark the characteristic for monitoring." +
                    " This will make sure that the characteristic's data is placed in the spreadsheet when you go to actually monitor the data.\n\n" +
                    (GlobalDataHub.isEditingProtocol()?" You are in Edit Protocol mode, attempting to write the value of the characteristic will instead save that value as something" +
                    " for the protocol to write to when it is initiated, and does not actually write the value of the characteristic at that time.":"");
            Intent intent = new Intent(ViewCharacteristicActivity.this, HelpActivity.class);
            intent.putExtra("helpMessage", helpMessage);
            startActivity(intent);
        });

        if(GlobalDataHub.isEditingProtocol()) deleteSettingButton.setVisibility(View.VISIBLE);

    }

    public static void updateMarkedButton(){

    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

}
