package com.spectra.analyzer;

import android.content.Context;
import android.content.Intent;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local, bounded journal; no payloads, credentials or continuous packet logging. */
final class EventJournal {
    static synchronized JSONArray read(Context c) {
        try{return new JSONArray(c.getSharedPreferences("event_journal",0).getString("entries","[]"));}
        catch(Exception e){return new JSONArray();}
    }
    static synchronized void clear(Context c) { c.getSharedPreferences("event_journal",0).edit().remove("entries").apply(); }
    static synchronized boolean record(Context c,String kind,Intent data,boolean demo) {
        String title="",detail=data.getStringExtra("message"),level="info";
        try {
            switch(kind) {
                case "connection":
                    title=data.getBooleanExtra("connected",false)?"Bağlantı kuruldu":"Bağlantı bekleniyor";
                    detail=data.getStringExtra("transport");
                    if("auto".equals(detail))detail="Akıllı bağlantı";
                    else if("wifi".equals(detail))detail="Wi-Fi veri kanalı";
                    else if("ble".equals(detail))detail="Bluetooth LE";
                    else if("demo".equals(detail))detail="Sentetik test kaynağı";
                    break;
                case "error":title="İşlem tamamlanamadı";level="warning";break;
                case "watch":title="Cihaz kuralı eşleşti";level="warning";break;
                case "saved":title="ESP32 ayarları kaydedildi";break;
                case "name_scan":title="BLE ad sorgulaması";detail="Tek seferlik aktif tarama başlatılacak";break;
                case "alert":
                    JSONObject alert=new JSONObject(data.getStringExtra("json"));
                    boolean test=alert.optBoolean("test");
                    title=test?"Alarm zinciri testi":alert.optBoolean("active")?"Geniş bant RF değişimi":"RF uyarısı sona erdi";
                    detail=test?"TEST · RF algılama testi değildir":alert.optInt("affected")+" kanal · anomali puanı "+alert.optInt("confidence");
                    level=test?"test":alert.optBoolean("active")?"alert":"info";break;
                default:return false;
            }
            if(detail==null)detail="";
            if(demo)title="DEMO · "+title;
            JSONArray old=read(c), next=new JSONArray();
            long now=System.currentTimeMillis();
            for(int i=0;i<Math.min(8,old.length());i++){
                JSONObject prev=old.optJSONObject(i);
                if(prev!=null&&title.equals(prev.optString("title"))&&detail.equals(prev.optString("detail"))
                        &&now-prev.optLong("time")<30000)return false;
            }
            next.put(new JSONObject().put("title",title).put("detail",detail).put("level",level).put("time",now));
            for(int i=0;i<Math.min(99,old.length());i++)next.put(old.get(i));
            c.getSharedPreferences("event_journal",0).edit().putString("entries",next.toString()).apply();
            return true;
        }catch(Exception e){return false;}
    }
}
