package com.spectra.analyzer;

import java.util.Locale;

/** Small offline Bluetooth SIG dictionary. A company identifier is not a model/name. */
final class DeviceMetadata {
    static String company(int id) {
        switch(id) {
            case 0x004c:return "Apple, Inc.";
            case 0x0006:return "Microsoft";
            case 0x0002:return "Intel Corp.";
            case 0x000d:return "Texas Instruments Inc.";
            default:return "";
        }
    }
    static String manufacturer(int id) {
        // Older firmware uses zero for both an absent value and the valid ID 0.
        if(id<=0)return "Yayınlanmadı / eski firmware alanı belirsiz";
        String name=company(id), hex=String.format(Locale.ROOT,"0x%04X",id);
        return name.isEmpty()?hex+" · yerel sözlükte karşılığı yok":name+" · "+hex+" ("+id+")";
    }
    static String appearance(int code) {
        if(code==0)return "Tür bildirilmedi (0); telefon olduğu çıkarılamaz";
        String category;
        switch(code>>6) {
            case 1:category="Telefon";break;
            case 2:category="Bilgisayar";break;
            case 3:category="Saat";break;
            default:category="Yerel sözlükte bulunmayan tür";
        }
        return category+" · kod "+code+" · cihazın yayın beyanı";
    }
    static String services(String raw) {
        if(raw==null||raw.trim().isEmpty())return "Reklam paketinde servis UUID'si yayınlanmadı";
        StringBuilder result=new StringBuilder();
        for(String part:raw.split(",")) {
            String uuid=part.trim().toLowerCase(Locale.ROOT), key=uuid;
            if(key.startsWith("0x"))key=key.substring(2);
            if(key.length()==36 && key.startsWith("0000") && key.endsWith("-0000-1000-8000-00805f9b34fb"))key=key.substring(4,8);
            String name;
            switch(key) {
                case "1800":name="Genel erişim (GAP)";break;
                case "1801":name="Genel öznitelikler (GATT)";break;
                case "180a":name="Cihaz bilgileri";break;
                case "180d":name="Kalp atış hızı";break;
                case "180f":name="Pil servisi (pil yüzdesi okunmadı)";break;
                case "1812":name="İnsan arayüz cihazı (HID)";break;
                case "181a":name="Çevresel algılama";break;
                default:name="Özel / yerel sözlükte olmayan servis";
            }
            if(result.length()>0)result.append('\n');
            result.append(name).append(" · ").append(uuid);
        }
        return result.toString();
    }
    static String wifiAddress(String mac) {
        try {
            int first=Integer.parseInt(mac.substring(0,2),16);
            if((first&1)!=0)return "Grup/yayın adresi; tek bir cihazı tanımlamaz";
            if((first&2)!=0)return "Yerel yönetilen adres; MAC önekinden üretici belirlenemez";
            return "Evrensel adres; bu sürümde Wi-Fi üretici öneki sözlüğü yok";
        } catch(Exception ignored){return "Geçerli bir MAC adresi alınmadı";}
    }
}
