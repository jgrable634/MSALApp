package com.example.myapplication;

import java.io.Serializable;

public class CharacteristicSetting implements Serializable {
    private String characteristicUUID;
    private String value;

    public CharacteristicSetting(String characteristicUUID, String value) {
        this.characteristicUUID = characteristicUUID;
        this.value = value;
    }

    public String getValue(){return value;}
    public String getUUID(){return characteristicUUID;}
    public void setValue(String value){this.value = value;}
    public void setUUID(String uuid){this.characteristicUUID = uuid;}

}
