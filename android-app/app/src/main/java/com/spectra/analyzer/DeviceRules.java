package com.spectra.analyzer;

import org.json.JSONObject;
import java.util.Locale;

/** Indicators, never a confirmed identity. OUI alone is deliberately low confidence. */
final class DeviceRules {
    private static final String OUIS = "70:c9:4e,3c:91:80,d8:f3:bc,80:30:49,b8:35:32,14:5a:fc,74:4c:a1,08:3a:88,9c:2f:9d,c0:35:32,94:08:53,e4:aa:ea,f4:6a:dd,e0:0a:f6,24:b2:b9,00:f4:8d,d0:39:57,e8:d0:fc,e0:4f:43,b8:1e:a4,70:08:94,58:8e:81,ec:1b:bd,3c:71:bf,58:00:e3,90:35:ea,5c:93:a2,64:6e:69,48:27:ea,a4:cf:12,14:b5:cd,82:6b:f2";
    static String address(JSONObject item) { return item.optString("bssid", item.optString("mac", "")); }
    static String name(JSONObject item, boolean wifi) {
        String name = item.optString("ssid", item.optString("name", "")).trim();
        if (!name.isEmpty()) return name;
        String mac = address(item);
        return (wifi ? "Wi-Fi · " : "BLE · ") + (mac.length() > 8 ? mac.substring(mac.length()-8) : mac);
    }
    static String match(JSONObject item, String custom) {
        String name = item.optString("ssid", item.optString("name", "")).toLowerCase(Locale.ROOT);
        String address = address(item).toLowerCase(Locale.ROOT);
        String services = item.optString("services").toLowerCase(Locale.ROOT);
        for (String token : custom.toLowerCase(Locale.ROOT).split("[,;\n]")) {
            token = token.trim();
            if (token.length() >= 3 && (name.contains(token) || address.startsWith(token) || services.contains(token)))
                return "İzleme kuralı: " + token;
        }
        if (name.startsWith("flock") || name.startsWith("fs ext battery") || name.startsWith("fs+ext+battery"))
            return "Flock ad imzası · kimlik doğrulanmadı";
        if (address.length() >= 8 && ("," + OUIS + ",").contains("," + address.substring(0,8) + ","))
            return "Araştırma OUI eşleşmesi · düşük güven; kamera kanıtı değil";
        return "";
    }
    static boolean shouldNotify(String match) { return !match.isEmpty() && !match.startsWith("Araştırma OUI"); }
    static String bytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format(Locale.US,"%.1f KiB", bytes / 1024.0);
        return String.format(Locale.US,"%.2f MiB", bytes / 1048576.0);
    }
}
