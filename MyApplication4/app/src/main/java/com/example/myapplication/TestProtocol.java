package com.example.myapplication;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class TestProtocol implements Serializable {
    private String protocolName;
    private List<CharacteristicSetting> characteristicSettings;
    private List<String> markedCharacteristics;

    public TestProtocol(String protocolName) {
        this.protocolName = protocolName;
        this.characteristicSettings = new ArrayList<>();
        this.markedCharacteristics = new ArrayList<>();
    }

    private int containsSetting(CharacteristicSetting setting){
        for(int i = 0; i < characteristicSettings.size(); i++){
            if(characteristicSettings.get(i).getUUID().equals(setting.getUUID())){
                return i;
            }
        }
        return -1;
    }

    public String getName(){return protocolName;}
    public List<CharacteristicSetting> getSettings(){return new ArrayList<>(characteristicSettings);}
    public List<String> getMarkedCharacteristics(){return new ArrayList<>(markedCharacteristics);}
    public void setName(String name){this.protocolName = name;}
    public void addSetting(CharacteristicSetting setting){
        int index = containsSetting(setting);
        if(index != -1){
            characteristicSettings.set(index, setting);
        } else{
            characteristicSettings.add(setting);
        }
    }
    public boolean containsMarked(String uuid){
        for(int i = 0; i < markedCharacteristics.size(); i++){
            if(markedCharacteristics.get(i).equals(uuid)){
                return true;
            }
        }
        return false;
    }
    public void markCharacteristic(String uuid){
        for(int i = 0; i < markedCharacteristics.size(); i++){
            if(markedCharacteristics.get(i).equals(uuid)){
                markedCharacteristics.remove(i);
                return;
            }
        }
        markedCharacteristics.add(uuid);
    }
    public void removeSetting(CharacteristicSetting setting){
        int index = containsSetting(setting);
        if(index != -1) characteristicSettings.remove(index);
    }

    public String toString(){
        return protocolName;
    }

}
