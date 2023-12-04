package com.example.myapplication;

import android.content.Context;
import android.provider.Settings;
import android.util.Log;

import com.google.api.services.sheets.v4.Sheets;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GlobalDataHub {
    private static Sheets googleSheet = null;
    private static String spreadsheetId = null;
    private static String worksheetName = null;
    private static String deviceAddress;
    private static boolean editProtocol = false;
    private static Context context;
    private static final String DATABASE_FILE_NAME = "ble_database.txt";
    private static HashMap<String, ArrayList<String>> ServicesMap = new HashMap<>();
    private static HashMap<String, String> UUIDNamesMap = new HashMap<>();
    private static List<String> markedCharacteristics = new ArrayList<>();
    private static Map<String, String> linkedServices = new HashMap<>();
    private static Map<String, String> foundDevices = new HashMap<>();
    private static List<Object> markedReadings = new ArrayList<>();
    private static List<List<Object>> toWritetoSpreadsheet = new ArrayList<>();
    private static List<CharacteristicSetting> characteristicsToWrite = new ArrayList<>();

    // Static getter and setter methods for variables
    public static String[] getSheetsInfo(){
        String[] toExport = new String[2];
        toExport[0] = spreadsheetId;
        toExport[1] = worksheetName;
        return toExport;
    }

    public static Sheets getServiceAccount(){
        return googleSheet;
    }

    public static String getDeviceAddress() {
        return deviceAddress;
    }

    public static String getDatabaseFileName() {
        return DATABASE_FILE_NAME;
    }

    public static Context getContext() {
        return context;
    }

    public static HashMap<String, ArrayList<String>> getServicesMap() {
        return ServicesMap;
    }

    public static HashMap<String, String> getUUIDNamesMap() {
        return UUIDNamesMap;
    }

    public static List<String> getMarkedCharacteristics() {
        return markedCharacteristics;
    }

    public static Map<String, String> getLinkedServices() {
        return linkedServices;
    }

    public static Map<String, String> getFoundDevices() {
        return foundDevices;
    }

    public static List<Object> getMarkedReadings() {
        return markedReadings;
    }

    public static List<List<Object>> getToWriteToSpreadsheet() {
        return toWritetoSpreadsheet;
    }

    public static List<CharacteristicSetting> getCharacteristicsToWrite() {
        return characteristicsToWrite;
    }

    public static void setServiceAccount(Sheets googleSheet, String spreadsheetId, String worksheetName){
        GlobalDataHub.googleSheet = googleSheet;
        GlobalDataHub.spreadsheetId = spreadsheetId;
        GlobalDataHub.worksheetName = worksheetName;
    }

    public static void setDeviceAddress(String deviceAddress) {
        GlobalDataHub.deviceAddress = deviceAddress;
    }

    public static void setContext(Context context){
        GlobalDataHub.context = context;
    }

    public static void setMarkedCharacteristics(List<String> markedCharacteristics){
        GlobalDataHub.markedCharacteristics = markedCharacteristics;
    }

    public static void setCharacteristicsToWrite(List<CharacteristicSetting> characteristicsToWrite){
        GlobalDataHub.characteristicsToWrite = characteristicsToWrite;
    }


    // Adder and remover methods for maps/lists
    public static void addToServicesMap(String key, ArrayList<String> value) {
        ServicesMap.put(key, value);
    }

    public static void removeFromServicesMap(String key) {
        ServicesMap.remove(key);
    }
    public static void clearServicesMap(){
        linkedServices.clear();
    }

    public static void addToUUIDNamesMap(String key, String value) {
        UUIDNamesMap.put(key, value);
    }

    public static void removeFromUUIDNamesMap(String key) {
        UUIDNamesMap.remove(key);
    }
    public static void clearUUIDNamesMap(){
        linkedServices.clear();
    }

    public static void addMarkedCharacteristic(String characteristic) {
        markedCharacteristics.add(characteristic);
    }

    public static void removeMarkedCharacteristic(String characteristic) {
        markedCharacteristics.remove(characteristic);
    }

    public static void clearMarkedCharacteristics(){
        linkedServices.clear();
    }

    public static void addLinkedService(String characteristicUuid, String serviceUuid) {
        linkedServices.put(characteristicUuid, serviceUuid);
    }

    public static void removeLinkedService(String characteristicUuid) {
        linkedServices.remove(characteristicUuid);
    }

    public static void clearLinkedServices(){
        linkedServices.clear();
    }

    public static void addFoundDevice(String deviceAddress, String deviceName) {
        foundDevices.put(deviceAddress, deviceName);
    }

    public static void removeFoundDevice(String deviceAddress) {
        foundDevices.remove(deviceAddress);
    }
    public static void clearFoundDevices(){
        foundDevices.clear();
    }

    public static void addMarkedReading(Object reading) {
        markedReadings.add(reading);
    }

    public static void clearMarkedReadings() {
        markedReadings.clear();
    }

    public static void addToWriteToSpreadsheet(List<Object> row) {
        toWritetoSpreadsheet.add(row);
    }

    public static void clearWriteToSpreadsheet() {
        toWritetoSpreadsheet.clear();
    }

    public static void addCharacteristicToWrite(CharacteristicSetting setting) {
        characteristicsToWrite.add(setting);
    }

    public static void removeCharacteristicToWrite(CharacteristicSetting setting) {
        characteristicsToWrite.remove(setting);
    }

    public static boolean isCharacteristicMarked(String characteristicUUID){
        return markedCharacteristics.contains(characteristicUUID);
    }

    public static void readDatabaseFromFile() {
        try {
            FileInputStream inputStream = context.openFileInput(DATABASE_FILE_NAME);
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder stringBuilder = new StringBuilder();
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }

            bufferedReader.close();
            inputStreamReader.close();
            inputStream.close();

            // Convert the JSON string from the file to a JSONObject
            JSONObject databaseJSON = new JSONObject(stringBuilder.toString());

            // Clear the existing data from UUIDNamesMap before populating it
            UUIDNamesMap.clear();

            // Extract UUIDs and names from the JSON and populate the UUIDNamesMap
            Iterator<String> keys = databaseJSON.keys();
            while (keys.hasNext()) {
                String uuid = keys.next();
                String name = databaseJSON.getString(uuid);
                UUIDNamesMap.put(uuid, name);
            }

            Log.d("DEBUG", "Database loaded from " + DATABASE_FILE_NAME);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateDatabase() {
        try {
            JSONObject updatedDatabaseJSON = new JSONObject(UUIDNamesMap);

            // Save the updated database JSON to the file
            String databaseContent = updatedDatabaseJSON.toString();
            FileOutputStream outputStream = context.openFileOutput(DATABASE_FILE_NAME, Context.MODE_PRIVATE);
            outputStream.write(databaseContent.getBytes());
            outputStream.close();
            Log.d("DEBUG", "Database updated and saved to " + DATABASE_FILE_NAME);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void updateServiceName(String serviceUUID, String newName) {
        UUIDNamesMap.put(serviceUUID, newName);
        updateDatabase(); // Update the database with the new service name
    }

    public static void updateCharacteristicName(String characteristicUUID, String newName) {
        UUIDNamesMap.put(characteristicUUID, newName);
        updateDatabase(); // Update the database with the new characteristic name
    }

    public static int getNumMarkedCharacteristics(){
        return markedCharacteristics.size();
    }


    public static void setProtocolStatus(boolean editProtocol){GlobalDataHub.editProtocol = editProtocol;}
    public static boolean isEditingProtocol(){return editProtocol;}

    public static void markCharacteristic(String characteristicUUID){
        if(markedCharacteristics.contains(characteristicUUID)){
            markedCharacteristics.remove(characteristicUUID);
        } else{
            markedCharacteristics.add(characteristicUUID);
        }
    }

}