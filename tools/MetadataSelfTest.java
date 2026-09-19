package com.spectra.analyzer;
public final class MetadataSelfTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(DeviceMetadata.manufacturer(76).contains("Apple"),"Decode company 76");
        check(DeviceMetadata.manufacturer(76).contains("0x004C"),"Keep original numeric evidence");
        check(DeviceMetadata.company(0).isEmpty(),"Old firmware zero is ambiguous, not Ericsson");
        check(DeviceMetadata.manufacturer(65534).contains("yerel sözlükte"),"Unknown company stays unknown");
        check(DeviceMetadata.appearance(0).contains("bildirilmedi"),"Zero appearance is not a phone");
        check(DeviceMetadata.appearance(64).contains("Telefon"),"Phone self report");
        check(DeviceMetadata.services("").contains("yayınlanmadı"),"No blank service detail");
        check(DeviceMetadata.services("0x180f").contains("Pil"),"Short service UUID");
        check(DeviceMetadata.services("0000180f-0000-1000-8000-00805f9b34fb").contains("Pil"),"Bluetooth base UUID");
        check(!DeviceMetadata.services("0000180f-1111-1000-8000-00805f9b34fb").contains("Pil"),"No false match on private UUID");
        check(DeviceMetadata.services("180a,180d").contains("\n"),"Multiple service labels");
        check(DeviceMetadata.wifiAddress("BE:07:1D:30:76:93").contains("Yerel"),"Local MAC cannot identify vendor");
        check(DeviceMetadata.wifiAddress("FF:FF:FF:FF:FF:FF").contains("Grup"),"Broadcast not a device");
        System.out.println("[OK] "+checks+" metadata decoding checks");
    }
}
