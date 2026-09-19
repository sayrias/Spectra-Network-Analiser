package com.spectra.analyzer;

import java.util.Locale;

/** Passive advertisement signatures, not authenticated product identities. */
final class BleSignals {
    static final int UNKNOWN=0, FIND_MY=1, IBEACON=2, SENSOR=3;
    static int classify(int company,String prefix,int length,String services,long signatureAgeMs) {
        String p=prefix==null?"":prefix.toLowerCase(Locale.ROOT);
        if(signatureAgeMs>=0 && signatureAgeMs<=60000 && company==76) {
            if(p.equals("4c001219") && length==29)return FIND_MY;
            if(p.equals("4c000215") && length==25)return IBEACON;
        }
        if(hasService(services,"181a") || hasService(services,"180d"))return SENSOR;
        return UNKNOWN;
    }
    static boolean hasService(String services,String uuid) {
        if(services==null)return false;
        for(String value:services.toLowerCase(Locale.ROOT).split(",")) {
            value=value.trim();
            if(value.equals(uuid) || value.equals("0x"+uuid) || value.equals("0000"+uuid+"-0000-1000-8000-00805f9b34fb"))return true;
        }
        return false;
    }
    static String label(int kind) {
        switch(kind) {
            case FIND_MY:return "Find My uyumlu yayın · AirTag olabilir";
            case IBEACON:return "iBeacon uyumlu yayın";
            case SENSOR:return "Sensör servisi yayınlıyor";
            default:return "Desteklenen yayın imzası yok";
        }
    }
    static String caveat(){return "Yayın imzası model veya sahip tespiti değildir; taklit edilebilir. Find My listesi AirTag dışında uyumlu etiketleri ve başka Apple cihazlarını içerebilir. Algılanmaması ortamda etiket olmadığını kanıtlamaz. Adresler değişebilir; farklı adresler birleştirilmez.";}
}
