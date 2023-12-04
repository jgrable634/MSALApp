package com.example.myapplication;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.Spreadsheet;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SheetsConnectionActivity extends AppCompatActivity {

    private EditText editSpreadsheetId;
    private EditText editWorksheetName;
    private Button btnConnectSpreadsheet;
    private Button btnMonitorData;
    private Button btnViewServices;
    private Button btnTestProtocols;
    private Button backButton;
    private SheetsConnectionActivity currInstance;
    private boolean connectedToSpreadsheet = false;
    private boolean connectedToWorksheet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sheets_connection);

        editSpreadsheetId = findViewById(R.id.editSpreadsheetId);
        editWorksheetName = findViewById(R.id.editWorksheetName);
        btnConnectSpreadsheet = findViewById(R.id.btnConnectSpreadsheet);
        btnViewServices = findViewById(R.id.btnViewServices);
        btnMonitorData = findViewById(R.id.btnMonitorData);
        btnTestProtocols = findViewById(R.id.btnTestProtocols);
        backButton = findViewById(R.id.backButton);
        currInstance = this;

        if(GlobalDataHub.getServiceAccount() != null){
            btnViewServices.setVisibility(View.VISIBLE);
            btnMonitorData.setVisibility(View.VISIBLE);
            btnTestProtocols.setVisibility(View.VISIBLE);
            String[] data = GlobalDataHub.getSheetsInfo();
            editSpreadsheetId.setText(data[0]);
            editWorksheetName.setText(data[1]);
        }

        btnConnectSpreadsheet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Validate the input fields (you can add your own validation logic here)
                if (ContextCompat.checkSelfPermission(currInstance, android.Manifest.permission.INTERNET)
                        != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(currInstance,
                            new String[]{android.Manifest.permission.INTERNET}, 3);
                }
                if (ContextCompat.checkSelfPermission(currInstance, Manifest.permission.INTERNET)
                        != PackageManager.PERMISSION_GRANTED) {
                    showToast("Sorry, internet permissions are required for this part of the application.");
                } else{
                    try{
                        btnViewServices.setVisibility(View.INVISIBLE);
                        btnMonitorData.setVisibility(View.INVISIBLE);
                        btnTestProtocols.setVisibility(View.INVISIBLE);
                        connectedToSpreadsheet = false;
                        connectedToWorksheet = false;
                        new ConnectToGoogleSheetsTask().execute();
                    } catch (Exception e) {
                        showToast("Something went wrong!");
                    }

                }
            }
        });

        btnViewServices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to ServicesOverviewActivity
                Intent intent = new Intent(SheetsConnectionActivity.this, ServicesOverviewActivity.class);
                startActivity(intent);
            }
        });

        btnMonitorData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to MonitorDataActivity
                if(GlobalDataHub.getNumMarkedCharacteristics() < 1){
                    showToast("Please select at least one characteristic to monitor.");
                } else{
                    Intent intent = new Intent(SheetsConnectionActivity.this, MonitorDataActivity.class);
                    startActivity(intent);
                }
            }
        });

        btnTestProtocols.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navigate to TestProtocolsActivity
                Intent intent = new Intent(SheetsConnectionActivity.this, TestProtocolsActivity.class);
                startActivity(intent);
            }
        });

        backButton.setOnClickListener(view -> finish());

        // Find the help button by its ID and add a click listener
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> {
            // On click, navigate to HelpActivity
            String helpMessage = "This is where you enter the ID of your spreadsheet and the specific worksheet name you desire" +
                    " to be written in. Keep in mind a few things, the ID of your spreadsheet is present in the URL when you view " +
                    "the spreadsheet. For example: https://docs.google.com/spreadsheets/d/SPREADSHEET_ID/edit#gid=0." +
                    "So copy that ID and paste it into the first input field on the screen. Then after clicking connect, " +
                    "(and assuming nothing goes wrong) the View Services and Monitor Data buttons will appear. " +
                    "If the app can't find your spreadsheet, make sure you have shared the spreadsheet with " +
                    "python-program@internship-sheets.iam.gserviceaccount.com, and given it editor's permission.";
            Intent intent = new Intent(SheetsConnectionActivity.this, HelpActivity.class);
            intent.putExtra("helpMessage", helpMessage);
            startActivity(intent);
        });

    }

    private class ConnectToGoogleSheetsTask extends AsyncTask<Void, Void, Sheets> {

        @Override
        protected Sheets doInBackground(Void... params) {
            try {
                // Get the AssetManager
                AssetManager assetManager = getAssets();

                // Open the file as an InputStream
                InputStream credentialsStream = assetManager.open("credentials.json");
                Log.d("DEBUG", assetManager.open("credentials.json").toString());
                GoogleCredential credentials = GoogleCredential.fromStream(credentialsStream)
                        .createScoped(Arrays.asList(SheetsScopes.SPREADSHEETS_READONLY, SheetsScopes.SPREADSHEETS, SheetsScopes.DRIVE_FILE));

                credentialsStream.close();
                Log.d("Asset Read", "Successfully read credentials.json, attempting to create Sheets builder");
                // Create Sheets service
                Sheets sheets = new Sheets.Builder(credentials.getTransport(), credentials.getJsonFactory(), credentials)
                        .setApplicationName("The App of all time")
                        .build();

                String spreadsheetId = editSpreadsheetId.getText().toString().trim();
                String worksheetName = editWorksheetName.getText().toString().trim();
                Log.d("DEBUG", "data recorded");
                try {
                    Log.d("DEBUG", "Attempting spreadsheet connection");
                    Spreadsheet spreadsheet = sheets.spreadsheets().get(spreadsheetId).execute();
                    if (spreadsheet != null) {
                        connectedToSpreadsheet = true;
                        Log.d("DEBUG", "spreadsheet connection successful, attempting worksheet verification");
                        // Fetch the list of all sheets in the spreadsheet
                        List<Sheet> sheetsList = spreadsheet.getSheets();

                        boolean isValidWorksheet = false;
                        for (Sheet sheet : sheetsList) {
                            if (sheet.getProperties().getTitle().equals(worksheetName)) {
                                isValidWorksheet = true;
                                break;
                            }
                        }

                        if (isValidWorksheet) {
                            Log.d("DEBUG", "Worksheet is valid");
                            GlobalDataHub.setServiceAccount(sheets, spreadsheetId, worksheetName);
                            connectedToWorksheet = true;
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    Log.d("TAG", e.getMessage());
                    showToast("Something went wrong, make sure the spreadsheet ID is correct.");
                }
                return sheets;
            } catch (Exception e) {
                Log.d("TAG", e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(Sheets sheets) {
            Log.d("DEBUG", "onPostExecute called");
            if (connectedToSpreadsheet && connectedToWorksheet) {
                Log.d("DEBUG", "Making next button visible");
                // Connection successful, make "View Services" button visible
                btnViewServices.setVisibility(View.VISIBLE);
                btnMonitorData.setVisibility(View.VISIBLE);
                btnTestProtocols.setVisibility(View.VISIBLE);
            } else {
                if(connectedToSpreadsheet){
                    // Connection failed, show error message
                    Log.d("DEBUG", "Invalid worksheet name");
                    showToast("Invalid worksheet name. Please check the worksheet name.");
                } else{
                    showToast("Something went wrong, make sure the spreadsheet ID is correct.");
                }
            }
        }
    }
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
