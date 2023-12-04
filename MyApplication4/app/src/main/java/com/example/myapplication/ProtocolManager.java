package com.example.myapplication;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ProtocolManager implements Serializable {
    private transient Context context;
    private List<TestProtocol> allProtocols;

    public ProtocolManager(Context context) {
        this.context = context.getApplicationContext();
        this.allProtocols = new ArrayList<>();
        loadAllProtocols();
    }

    public String getNewName(){
        int numDefaultNames = 0;
        for(TestProtocol protocol : allProtocols){
            if(protocol.getName().contains("New Protocol")) numDefaultNames++;
        }
        return "New Protocol" + (numDefaultNames+1);
    }


    // Load a test protocol from JSON file in internal storage
    private TestProtocol loadProtocol(String fileName) throws IOException {
        Gson gson = new Gson();
        FileInputStream fis = context.openFileInput(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        Type type = new TypeToken<TestProtocol>() {}.getType();
        TestProtocol protocol = gson.fromJson(reader, type);
        reader.close();
        return protocol;
    }

    // Save a test protocol to a JSON file in internal storage
    private void saveProtocol(String fileName, TestProtocol protocol) throws IOException {
        Gson gson = new Gson();
        FileOutputStream fos = context.openFileOutput(fileName, Context.MODE_PRIVATE);
        String json = gson.toJson(protocol);
        fos.write(json.getBytes());
        fos.close();
    }

    // Load all protocols from internal storage and populate the allProtocols list
    private void loadAllProtocols() {
        try {
            String[] fileList = context.fileList();
            for (String fileName : fileList) {
                try{
                    if(fileName.equals("ble_database.txt")) continue;
                    if(fileName.equals("profileInstalled")) continue;
                    if(fileName.equals("null.json")) deleteProtocol(null, context);
                    Log.d("DEBUG", "Found protocol from " + fileName);
                    TestProtocol protocol = loadProtocol(fileName);
                    allProtocols.add(protocol);
                } catch(com.google.gson.JsonSyntaxException e){
                    Log.d("ERROR", "Non-protocol file found: "+fileName);
                }
            }
            if(allProtocols.size() == 0) loadPredefinedProtocols();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean checkRename(String newName){
        if(containsProtocol(newName) != -1) return false;
        return true;
    }

    private int containsProtocol(String protocolName){
        for(int i = 0; i < allProtocols.size(); i++){
            if(allProtocols.get(i).getName().equals(protocolName)) return i;
        }
        return -1;
    }

    // Add a new protocol to the allProtocols list and save it to internal storage
    public void addProtocol(TestProtocol protocol) {
        int index = containsProtocol(protocol.getName());
        if(index != -1) allProtocols.remove(index);
        allProtocols.add(protocol);
        try {
            String fileName = protocol.getName() + ".json";
            saveProtocol(fileName, protocol);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean deleteProtocol(TestProtocol protocol, Context context){
        String fileName;
        if(protocol != null){
            fileName = protocol.getName() + ".json";
            int index = containsProtocol(protocol.getName());
            if(index != -1) allProtocols.remove(index);
        } else fileName = "null.json";
        Log.d("DEBUG", "Attempting deletion of " + fileName);
        File fileToDelete = new File(context.getFilesDir(), fileName);
        if (fileToDelete.exists()) {
            return fileToDelete.delete();
        } else {
            Log.d("DEBUG", "File does not exist: " + fileToDelete.getAbsolutePath());
            return false;
        }
    }

    // Get all loaded protocols
    public List<TestProtocol> getAllProtocols() {
        return allProtocols;
    }
        // Load predefined protocols from assets and save them to internal storage
    public void loadPredefinedProtocols() {
                Log.d("DEBUG", "Loading predefined protocols...");
                Gson gson = new Gson();
                String[] predefinedProtocolFiles = new String[0];
                try {
                    predefinedProtocolFiles = context.getAssets().list("");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if(predefinedProtocolFiles.length == 0) Log.d("DEBUG", "No predefined protocols?");
                if (predefinedProtocolFiles != null) {
                    for (String fileName : predefinedProtocolFiles) {
                        try {
                            if (fileName.equals("credentials.json")) continue;
                            Log.d("DEBUG", "Found predefined protocol " + fileName);
                            InputStream inputStream = context.getAssets().open(fileName);
                            InputStreamReader reader = new InputStreamReader(inputStream);
                            Type type = new TypeToken<TestProtocol>() {
                            }.getType();
                            TestProtocol protocol = gson.fromJson(reader, type);
                            reader.close();
                            if (protocol.getName() != null) {
                                protocol.setName("PREDEFINED "+protocol.getName());
                                addProtocol(protocol); // Save the protocol to internal storage
                            } else Log.d("DEBUG", fileName + " is not a protocol.");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else Log.d("DEBUG", "No predefined protocols?");
    }

    public void setContext(Context context) {
        this.context = context;
    }

    // Other methods to manage protocols

        // Serialization
        private void writeObject(ObjectOutputStream out) throws IOException {
            out.defaultWriteObject();
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            in.defaultReadObject();
        }
    }

