package com.example.myapplication;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    // Bluetooth variables
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothLeScanner bluetoothLeScanner;

    // UI elements
    private EditText deviceAddressInput;
    private Button connectButton;
    private Button scanButton;
    private Button disconnectButton;
    private TextView logTextView;
    private ScrollView scrollView;
    private TextView readValueTextView;
    private Button nextButton;
    private TextView monitorLog;

    // Permissions request code
    private static final int PERMISSION_REQUEST_CODE = 1;

    // Global variables
    private static MainActivity instance;
    private BluetoothGattCallback gattCallback;
    private BluetoothAdapter.LeScanCallback scanCallback;
    private ScheduledExecutorService scheduledExecutor;
    private int numWrittenCharacteristics = 0;
    private int reconnectionAttempts = 0;
    private int numMarkedReadings = 0;
    private boolean monitoringInterrupted = false;
    private boolean attemptingReconnection = false;
    private boolean isCharacteristicBeingRead = false;
    private boolean isCharacteristicBeingWritten = false;
    private boolean bulkCharacteristicsReading = false;
    private boolean bulkWriting = false;
    private boolean readyToMonitor = true;
    private boolean continueMonitoring = false;
    private boolean isScanning = false;
    private boolean nearbyScan = false;
    private boolean intentionalDisconnect = false;
    private Handler monitorHandler;
    private int monitorInterval;
    private Runnable runnableMonitor;


    @Override
    public void onDestroy(){
      super.onDestroy();
      if(scheduledExecutor != null) scheduledExecutor.shutdown();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceAddressInput = findViewById(R.id.deviceAddressInput);
        connectButton = findViewById(R.id.connectButton);
        scanButton = findViewById(R.id.scanButton);
        disconnectButton = findViewById(R.id.disconnectButton);
        logTextView = findViewById(R.id.logTextView);
        scrollView = findViewById(R.id.scrollView);
        nextButton = findViewById(R.id.nextButton); // New "Next" button
        monitorHandler = new Handler(Looper.getMainLooper());

        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        // Request location permission required for scanning on Android 6.0+
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2);
        }
        // Request location permission required for scanning on Android 6.0+
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1);
        }

        instance = this;
        GlobalDataHub.setContext(this);
        scheduledExecutor = Executors.newScheduledThreadPool(2);

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check if a connection is already established
                if (bluetoothGatt != null) {
                    logMessage("A connection is already established.");
                    return;
                }

                GlobalDataHub.clearLinkedServices();

                String desiredDeviceAddress = deviceAddressInput.getText().toString().toUpperCase().trim();
                if (!desiredDeviceAddress.isEmpty()) {
                    GlobalDataHub.setDeviceAddress(desiredDeviceAddress);
                    logTextView.setText("");
                    reconnectionAttempts = 0;
                    nearbyScan = false;
                    GlobalDataHub.clearFoundDevices();
                    checkBluetoothPermissions(); // Check Bluetooth permissions
                } else {
                    showToast("Please enter a device address");
                }
            }
        });

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GlobalDataHub.clearFoundDevices();
                nearbyScan = true;
                checkBluetoothPermissions();
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Disconnect from the BLE device
                disconnectFromBLEDevice();
            }
        });

        // New "Next" button click listener
        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start the ServicesOverviewActivity
                Intent intent = new Intent(MainActivity.this, SheetsConnectionActivity.class);
                startActivity(intent);
            }
        });

        // Find the help button by its ID and add a click listener
        Button helpButton = findViewById(R.id.helpButton);
        helpButton.setOnClickListener(v -> {
            // On click, navigate to HelpActivity
            String helpMessage = "Welcome! This is your first step in monitoring your BLE device's data! " +
                    "If you know the device address of what you want to connect to, enter it into the field at the top of the screen." +
                    "Otherwise, click the scan for nearby devices button and after 10 seconds you'll be shown a list of all discovered devices.";
            Intent intent = new Intent(MainActivity.this, HelpActivity.class);
            intent.putExtra("helpMessage", helpMessage);
            startActivity(intent);
        });

        GlobalDataHub.readDatabaseFromFile();
    }
    @SuppressLint("MissingPermission")
    private void disconnectFromBLEDevice() {
        // Check if a connection exists
        if (bluetoothGatt != null) {
            intentionalDisconnect = true;
            bluetoothGatt.disconnect();
            bluetoothGatt = null; // Set bluetoothGatt to null after disconnecting
            nextButton.setVisibility(View.INVISIBLE); // Hide the "Next" button after disconnection
            bluetoothAdapter.stopLeScan(scanCallback);
        } else {
            if(isScanning){
                bluetoothAdapter.stopLeScan(scanCallback);
                isScanning = false;
                logMessage("Scan stopped.");
            } else{
                logMessage("No connection to disconnect.");
            }

        }
    }
    @SuppressLint("MissingPermission")
    private void checkBluetoothPermissions() {
        // Check if Bluetooth is supported on the device
        if (bluetoothAdapter == null) {
            showToast("Bluetooth is not supported on this device");
            return;
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, PERMISSION_REQUEST_CODE);
        } else {
            startBLEScan();
        }
    }

    @SuppressLint("MissingPermission")
    private void startBLEScan() {
        scanCallback = new BluetoothAdapter.LeScanCallback() {
            @Override
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                runOnUiThread(new Runnable() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void run() {
                        // Compare the found device address with the desired device address
                        if(nearbyScan){
                            GlobalDataHub.addFoundDevice(device.getAddress(), device.getName());
                        } else if (device.getAddress().equals(GlobalDataHub.getDeviceAddress())) {
                            logMessage("Found desired device, attempting connection...");
                            // Device found, stop scanning
                            bluetoothAdapter.stopLeScan(scanCallback);
                            isScanning = false;
                            // Connect to the desired device
                            connectToDevice(device);
                        }
                    }
                });
            }
        };

        // Start scanning for BLE devices
        if (isScanning) {
            logMessage("Scan already started, restarting scanner...");
            bluetoothAdapter.stopLeScan(scanCallback);
        }
        bluetoothAdapter.startLeScan(scanCallback);
        isScanning = true;


            // Stop scanning after a certain duration (e.g., 10 seconds)
        long scanDuration = 10; // seconds
        scheduledExecutor.schedule(() -> {
            if (isScanning) {
                isScanning = false;
                bluetoothAdapter.stopLeScan(scanCallback);
                if (!nearbyScan) {
                    logMessage("Scan timed out, try scanning again.");
                } else {
                    nearbyScan = false;
                    Intent intent = new Intent(MainActivity.this, NearbyDevicesActivity.class);
                    startActivity(intent);
                }
            }
        }, scanDuration, TimeUnit.SECONDS);

        if(!nearbyScan) logMessage("Scanning for specified BLE device...");
        else logMessage("Scanning for nearby BLE devices, please wait for 10 seconds...");
    }

    @SuppressLint("MissingPermission")
    private void restartReconnection(BluetoothGatt gatt){
        Log.d("DEBUG", "Maximum reconnection attempts made, reconnection failed. Attempting again in 5 minutes.");
        reconnectionAttempts = 0;
        // Schedule the reconnection attempt after a delay
        scheduledExecutor.schedule(() -> {
            Log.d("DEBUG", "Reattempting connection after waiting.");
            bluetoothGatt = gatt.getDevice().connectGatt(instance, false, gattCallback);
        }, 5, TimeUnit.MINUTES);

        // Attempt reconnection after 5 minutes, delay in milliseconds
        Log.d("DEBUG", "Reconnection scheduled for 5 minutes from now.");
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice(BluetoothDevice device) {
        // Establish a connection with the selected BLE device

        gattCallback = new BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                Looper looper = Looper.myLooper();
                if (looper == null) {
                    Looper.prepare();
                    looper = Looper.myLooper();
                }
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // Discover services on the connected device
                    bluetoothGatt.discoverServices();

                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // Device disconnected
                    if(!intentionalDisconnect) {
                        if (GlobalDataHub.getServiceAccount() != null) {
                            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss", Locale.getDefault());
                            String dateTime = sdf.format(new Date());
                            ArrayList<Object> disconnectNotif = new ArrayList<>();
                            disconnectNotif.add(dateTime);
                            if (!attemptingReconnection) disconnectNotif.add("DEVICE DISCONNECTED");
                            else disconnectNotif.add("RECONNECTION FAILED");
                            GlobalDataHub.clearWriteToSpreadsheet();
                            GlobalDataHub.addToWriteToSpreadsheet(disconnectNotif);
                            String[] info = GlobalDataHub.getSheetsInfo();
                            writeToWorksheet(info[0], info[1], GlobalDataHub.getToWriteToSpreadsheet(), true);
                        }
                        if (reconnectionAttempts >= 4) {
                            restartReconnection(gatt);
                        } else {
                            attemptingReconnection = true;
                            Log.d("DEBUG", "DEVICE DISCONNECTED, ATTEMPTING RECONNECTION");
                            bluetoothGatt = gatt.getDevice().connectGatt(instance, false, this);
                            reconnectionAttempts++;
                        }
                    }
                }
            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    intentionalDisconnect = false;
                    attemptingReconnection = false;
                    if(runnableMonitor != null){
                        reconnectionAttempts = 0;
                        Log.d("DEBUG", "DEVICE RECONNECTED, RESTARTING MONITORING");
                        monitoringInterrupted = true;
                        numMarkedReadings = 0;
                        monitorData(monitorInterval, monitorLog);
                        return;
                    }
                    try{
                        logMessage("Connected to BLE device, services discovered: ");
                    } catch (Exception e){
                        Log.d("DEBUG", "Probably just CalledFromWrongThreadException, just ignore.");
                    }


                    // Clear the ServicesMap before populating it
                    GlobalDataHub.clearServicesMap();
                    GlobalDataHub.clearLinkedServices();

                    // Log the discovered services and characteristics
                    List<BluetoothGattService> services = gatt.getServices();
                    for (BluetoothGattService service : services) {
                        String serviceUuid = service.getUuid().toString();
                        String serviceName = GlobalDataHub.getUUIDNamesMap().get(serviceUuid);
                        if (serviceName == null) {
                            serviceName = "Unknown Service";
                        }
                        logMessage("Service UUID: " + serviceUuid + ", Name: " + serviceName);

                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                        ArrayList<String> characteristicUuids = new ArrayList<>();

                        for (BluetoothGattCharacteristic characteristic : characteristics) {
                            String characteristicUuid = characteristic.getUuid().toString();
                            String characteristicName = GlobalDataHub.getUUIDNamesMap().get(characteristicUuid);
                            if (characteristicName == null) {
                                characteristicName = "Unknown Characteristic";
                            }
                            GlobalDataHub.addLinkedService(characteristicUuid, serviceUuid);
                            logMessage("Characteristic UUID: " + characteristicUuid + ", Name: " + characteristicName);

                            characteristicUuids.add(characteristicUuid);
                            GlobalDataHub.addToUUIDNamesMap(characteristicUuid, characteristicName);
                        }

                        // Add the service UUID and its corresponding characteristic UUIDs to the ServicesMap
                        GlobalDataHub.addToServicesMap(serviceUuid, characteristicUuids);
                        GlobalDataHub.addToUUIDNamesMap(serviceUuid, serviceName);
                    }

                    // Write the database to a .txt file (optional, you can remove this if not needed)
                    GlobalDataHub.updateDatabase();

                    reconnectionAttempts = 0;

                    // Show the "Next" button after successful connection and service discovery
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            nextButton.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    // Failed to discover services
                    logMessage("Failed to discover services on BLE device. Status: " + status);
                }
            }
            @SuppressLint("MissingPermission")
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {

                    // Assuming the characteristic value is encoded in UTF-8
                    String readValue = byteArrayToString(characteristic.getValue(), "UTF-8");
                    if(readValue == null) readValue = "null";

                    if(!bulkCharacteristicsReading){
                        String finalReadValue = readValue;
                        runOnUiThread(() -> {
                            // Update the TextView with the read value
                            readValueTextView.setText(finalReadValue);

                            isCharacteristicBeingRead = false;
                        });
                    } else {
                        if(continueMonitoring){
                            GlobalDataHub.addMarkedReading(readValue);
                            if(numMarkedReadings >= GlobalDataHub.getMarkedCharacteristics().size()-1){
                                GlobalDataHub.addToWriteToSpreadsheet(new ArrayList<>(GlobalDataHub.getMarkedReadings()));
                                GlobalDataHub.clearMarkedReadings();
                                numMarkedReadings = 0;
                                String[] info = GlobalDataHub.getSheetsInfo();
                                writeToWorksheet(info[0], info[1], GlobalDataHub.getToWriteToSpreadsheet(), false);
                            } else{
                                numMarkedReadings++;
                                // Log.d("DEBUG", "Next characteristic to be read: "+markedCharacteristics.get(numMarkedReadings));
                                BluetoothGattCharacteristic nextCharacteristic = findCharacteristic(GlobalDataHub.getMarkedCharacteristics().get(numMarkedReadings));
                                bluetoothGatt.readCharacteristic(nextCharacteristic);
                            }
                        }
                    }
                    // Update the UI or data structures with the read value

                } else {
                    GlobalDataHub.clearMarkedReadings();
                    Log.d("DEBUG", "Characteristic read failed with status: " + status);
                }
            }
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                // Handle the write operation result here
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    isCharacteristicBeingWritten = false;
                    if(bulkWriting){
                        numWrittenCharacteristics++;
                        if(numWrittenCharacteristics > GlobalDataHub.getCharacteristicsToWrite().size()-1){
                            bulkWriting = false;
                            readyToMonitor = true;
                        } else{
                            CharacteristicSetting setting = GlobalDataHub.getCharacteristicsToWrite().get(numWrittenCharacteristics);
                            writeCharacteristic(setting.getUUID(), setting.getValue());
                        }
                    }
                } else {
                    isCharacteristicBeingWritten = false;
                    showToast("Something went wrong with writing the value!");
                }
            }
        };

        bluetoothGatt = device.connectGatt(this, false, gattCallback);
        logMessage("Connecting to BLE device: " + device.getAddress());
    }

    private BluetoothGattCharacteristic findCharacteristic(String characteristicUUID) {
        if (bluetoothGatt == null) {
            return null;
        }
        // Get the service using its UUID
        BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(GlobalDataHub.getLinkedServices().get(characteristicUUID)));

        if (service != null) {
            // Get the characteristic using its UUID
            return service.getCharacteristic(UUID.fromString(characteristicUUID));
        }

        return null;
    }

    public boolean isCharacteristicReadable(String characteristicUUID) {
        BluetoothGattCharacteristic characteristic = findCharacteristic(characteristicUUID);
        if (characteristic == null) {
            return false;
        }
        int properties = characteristic.getProperties();
        return (properties & BluetoothGattCharacteristic.PROPERTY_READ) != 0;
    }

    @SuppressLint("MissingPermission")
    public void readCharacteristic(String characteristicUUID, TextView returnTo) {
        if(isCharacteristicBeingRead){
            showToast("Slow Down! Previous read command unfinished!");
        }
        if (bluetoothGatt == null) {
            showToast("Error: Device is unreachable.");
            return;
        }

        BluetoothGattCharacteristic characteristic = findCharacteristic(characteristicUUID);
        if (characteristic == null) {
            showToast("Error: Characteristic is unreachable.");
            return;
        }

        if (!isCharacteristicReadable(characteristicUUID)) {
            showToast("Error: Characteristic is unreadable.");
            return;
        }
        isCharacteristicBeingRead = true;
        readValueTextView = returnTo;
        bluetoothGatt.readCharacteristic(characteristic);
    }

    public boolean isCharacteristicWritable(String characteristicUUID) {
        BluetoothGattCharacteristic characteristic = findCharacteristic(characteristicUUID);
        if (characteristic == null) {
            return false;
        }
        int properties = characteristic.getProperties();
        return (properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0;
    }

    @SuppressLint("MissingPermission")
    public boolean writeCharacteristic(String characteristicUUID, String value) {
        if(isCharacteristicBeingWritten){
            showToast("Slow Down! Previous write command unfinished!");
            return false;
        }
        if (bluetoothGatt == null) {
            showToast("Device unreachable");
            return false;
        }

        BluetoothGattCharacteristic characteristic = findCharacteristic(characteristicUUID);
        if (characteristic == null) {
            showToast("Characteristic unreachable");
            return false;
        }

        if (!isCharacteristicWritable(characteristicUUID)) {
            showToast("Proper writing permissions do not exist.");
            return false;
        }

        byte[] data;

        try {
            data = value.getBytes("UTF-8"); // Convert the string to bytes using UTF-8 encoding
        } catch (UnsupportedEncodingException e) {
            showToast("Encoding of given value failed.");
            return false; // Handle the error if the encoding is not supported
        }

        characteristic.setValue(data);
        isCharacteristicBeingWritten = true;
        return bluetoothGatt.writeCharacteristic(characteristic);
    }


    public void monitorData(int seconds, TextView monitorLog) {
        if(!readyToMonitor){
            showToast("Standby, Test Protocol is still initiating...");
            return;
        }

        continueMonitoring = true;
        this.monitorLog = monitorLog;

        if(!monitoringInterrupted){
            logMonitorMessage("Preparing to Monitor Data...");
            GlobalDataHub.clearMarkedReadings();
            Collections.sort(GlobalDataHub.getMarkedCharacteristics());
            monitorInterval = seconds;
            logMonitorMessage("Adding headers to Google Sheet...");
            ArrayList<Object> headerValues = new ArrayList<>();
            headerValues.add("Date and Time");
            for(String uuid : GlobalDataHub.getMarkedCharacteristics()){
                headerValues.add(GlobalDataHub.getUUIDNamesMap().get(uuid));
            }
            GlobalDataHub.addToWriteToSpreadsheet(headerValues);
            String[] info = GlobalDataHub.getSheetsInfo();
            writeToWorksheet(info[0], info[1], GlobalDataHub.getToWriteToSpreadsheet(), true);
        } else{
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss", Locale.getDefault());
            String dateTime = sdf.format(new Date());
            ArrayList<Object> disconnectNotif = new ArrayList<>();
            disconnectNotif.add(dateTime);
            disconnectNotif.add("DEVICE RECONNECTED");
            GlobalDataHub.clearWriteToSpreadsheet();
            GlobalDataHub.addToWriteToSpreadsheet(disconnectNotif);
            String[] info = GlobalDataHub.getSheetsInfo();
            writeToWorksheet(info[0], info[1], GlobalDataHub.getToWriteToSpreadsheet(), true);
        }
            // Create a runnable to run the monitoring task
        if(!monitoringInterrupted) logMonitorMessage("Initiating Data Monitoring...");

        // Create a runnable to run the monitoring task
        runnableMonitor = new Runnable() {
            @SuppressLint("MissingPermission")
            @Override
            public void run() {
                if (continueMonitoring) {
                    // Get the current date/time
                    SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss", Locale.getDefault());
                    String dateTime = sdf.format(new Date());

                    // Get characteristic values for each marked characteristic
                    GlobalDataHub.addMarkedReading(dateTime); // Add date/time as the first value
                    bulkCharacteristicsReading = true;
                    BluetoothGattCharacteristic characteristic = findCharacteristic(GlobalDataHub.getMarkedCharacteristics().get(0));
                    bluetoothGatt.readCharacteristic(characteristic);
                } else {
                    // Monitoring interrupted
                    monitoringInterrupted = false;
                }
            }
        };

        scheduledExecutor.schedule(runnableMonitor, monitorInterval, TimeUnit.SECONDS);

    }

    public void stopDataMonitoring(){
        continueMonitoring = false;
    }

    private void writeToWorksheet(String spreadsheetId, String worksheetName, List<List<Object>> values, boolean restarting) {
        try {
            // Create the value range to write to the worksheet
            ValueRange valueRange = new ValueRange();
            valueRange.setValues(values);

            // Append the values to the worksheet
            Sheets sheetsService = GlobalDataHub.getServiceAccount();
            sheetsService.spreadsheets().values()
                    .append(spreadsheetId, worksheetName, valueRange)
                    .setValueInputOption("RAW")
                    .execute();


            if(!restarting){
                scheduledExecutor.schedule(runnableMonitor, monitorInterval, TimeUnit.SECONDS);
            }
            GlobalDataHub.clearWriteToSpreadsheet();

        } catch (NetworkOnMainThreadException e) {
            Log.d("DEBUG", "NetworkOnMainThreadException, safe to ignore (I think)");
        } catch (Exception e){
            e.printStackTrace();
            showToast("Something went wrong!");
        }
    }

    public void initiateProtocol(TestProtocol protocol){
        GlobalDataHub.setMarkedCharacteristics(protocol.getMarkedCharacteristics());
        GlobalDataHub.setCharacteristicsToWrite(protocol.getSettings());
        boolean allDataPresent = true;
        String missingCharacteristic = "";
        for(String s : GlobalDataHub.getMarkedCharacteristics()){
            if(!GlobalDataHub.getLinkedServices().containsKey(s)){
                allDataPresent = false;
                missingCharacteristic = s;
            }
        }
        for(CharacteristicSetting c : GlobalDataHub.getCharacteristicsToWrite()){
            if(!GlobalDataHub.getLinkedServices().containsKey(c.getUUID())){
                allDataPresent = false;
                missingCharacteristic = c.getUUID();
            }
        }
        if(allDataPresent) {
            numWrittenCharacteristics = 0;
            if (GlobalDataHub.getCharacteristicsToWrite().size() > 0) {
                CharacteristicSetting setting = GlobalDataHub.getCharacteristicsToWrite().get(numWrittenCharacteristics);
                readyToMonitor = false;
                bulkWriting = true;
                writeCharacteristic(setting.getUUID(), setting.getValue());
            }
        } else {
            Log.d("DEBUG", "Missing protocol characteristic: " + GlobalDataHub.getUUIDNamesMap().get(missingCharacteristic));
            showToast("Error: protocol references characteristic " + GlobalDataHub.getUUIDNamesMap().get(missingCharacteristic) + " which does not exist on connected device.");
        }
    }

    private String byteArrayToString(byte[] byteArray, String encoding) {
        if (byteArray == null || byteArray.length == 0) {
            showToast("byteArray Invalid");
            return null;
        }

        try {
            return new String(byteArray, encoding);
        } catch (UnsupportedEncodingException e) {
            showToast("UnsupportedEncodingException");
            e.printStackTrace();
            return null;
        }
    }

    public void setDeviceAddressText(String address){
        deviceAddressInput.setText(address);
    }
    public static MainActivity getInstance(){
        return instance;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void logMessage(String message) {
        Log.d("BLEApp", message);
        logTextView.append(message + "\n");
        scrollView.postDelayed(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        }, 100);
    }
    private void logMonitorMessage(String message) {
        Log.d("BLEApp", message);
        monitorLog.append(message + "\n");
        scrollView.postDelayed(new Runnable() {
            @Override
            public void run() {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        }, 100);
    }
}