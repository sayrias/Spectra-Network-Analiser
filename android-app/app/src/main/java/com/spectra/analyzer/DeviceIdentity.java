package com.spectra.analyzer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Never merges different MACs or guesses a hidden SSID from its neighbour. */
final class DeviceIdentity {
    private final SharedPreferences aliases;
    private final LinkedHashMap<String,String> observed = new LinkedHashMap<>();
    DeviceIdentity(Context context) { aliases=context.getSharedPreferences("device_aliases",Context.MODE_PRIVATE); }
    String key(JSONObject d, boolean wifi) {
        return (wifi?"wifi:":"ble:")+DeviceRules.address(d).toLowerCase(Locale.ROOT);
    }
    void learn(JSONArray list, boolean wifi, boolean demo) {
        for(int i=0;i<list.length();i++) {
            JSONObject d=list.optJSONObject(i); if(d==null)continue;
            String name=d.optString("ssid",d.optString("name","")).trim();
            if(!name.isEmpty())observed.put((demo?"demo:":"live:")+key(d,wifi),name);
        }
        while(observed.size()>256)observed.remove(observed.keySet().iterator().next());
    }
    String name(JSONObject d, boolean wifi, boolean demo) {
        String alias=aliases.getString((demo?"demo:":"live:")+key(d,wifi),"");
        if(!alias.isEmpty())return alias;
        String advertised=d.optString("ssid",d.optString("name","")).trim();
        if(!advertised.isEmpty())return advertised;
        String known=observed.get((demo?"demo:":"live:")+key(d,wifi));
        if(known!=null)return known;
        String mac=DeviceRules.address(d);
        String company=wifi?"":DeviceMetadata.company(d.optInt("manufacturer",-1));
        if(!company.isEmpty())return company+" kimlikli yayın "+(mac.length()>8?mac.substring(mac.length()-8):mac);
        return "Adsız cihaz "+(mac.length()>8?mac.substring(mac.length()-8):mac);
    }
    String type(JSONObject d, boolean wifi) {
        if(wifi)return d.has("bssid")?"Wi-Fi erişim noktası":"Wi-Fi";
        // Bluetooth SIG Appearance categories; these are device self-reports.
        switch(d.optInt("appearance",0)>>6) {
            case 1:return "Telefon (BLE beyanı)";
            case 2:return "Bilgisayar (BLE beyanı)";
            case 3:return "Saat (BLE beyanı)";
            default:return "BLE";
        }
    }
    String title(JSONObject d, boolean wifi, boolean demo) { return name(d,wifi,demo)+" — "+type(d,wifi); }
    String source(JSONObject d, boolean wifi, boolean demo) {
        if(!aliases.getString((demo?"demo:":"live:")+key(d,wifi),"").isEmpty())return "Kullanıcı takma adı · gerçek yayın adı değildir";
        if(!d.optString("ssid",d.optString("name","")).trim().isEmpty())return "Yayında gözlenen ad";
        if(observed.containsKey((demo?"demo:":"live:")+key(d,wifi)))return "Bu oturumda aynı adreste daha önce gözlenen ad";
        if(!wifi && !DeviceMetadata.company(d.optInt("manufacturer",-1)).isEmpty())return "Şirket kimliği çözüldü · gerçek cihaz adı/modeli yayınlanmadı";
        return "Ad yayınlanmadı · farklı adresler birleştirilmez";
    }
    void setAlias(JSONObject d, boolean wifi, boolean demo, String name) {
        String k=(demo?"demo:":"live:")+key(d,wifi);
        SharedPreferences.Editor e=aliases.edit();
        if(name.trim().isEmpty())e.remove(k);else e.putString(k,name.trim().substring(0,Math.min(80,name.trim().length())));
        e.apply();
    }
}
