package com.spectra.analyzer;

import android.app.Instrumentation;
import android.os.Bundle;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import org.json.JSONObject;
import org.json.JSONArray;

/** No extra test dependencies. Run only on the dedicated emulator. */
public final class ModelTests extends Instrumentation {
    private int checks;
    private void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private Object field(Object object,String name)throws Exception {
        java.lang.reflect.Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();Context c=getTargetContext();
        SharedPreferences journal=c.getSharedPreferences("event_journal",0);
        String original=journal.getString("entries","[]");
        boolean passed=false;
        try{
            check(c.checkSelfPermission("android.permission.CHANGE_NETWORK_STATE")==PackageManager.PERMISSION_GRANTED,"Missing network request permission");
            DeviceIdentity names=new DeviceIdentity(c);
            JSONObject ap=new JSONObject().put("bssid","02:00:00:ff:00:01").put("ssid","KendiAgi");
            JSONObject station=new JSONObject().put("mac","02:00:00:ff:00:01").put("name","");
            JSONObject other=new JSONObject().put("bssid","02:00:00:ff:00:02").put("ssid","");
            names.learn(new JSONArray().put(ap),true,true);
            check(names.name(station,true,true).equals("KendiAgi"),"Same address name cache");
            check(!names.name(other,true,true).equals("KendiAgi"),"Never merge neighbouring hidden BSSIDs");
            check(!names.name(station,true,false).equals("KendiAgi"),"DEMO/live isolation");
            names.setAlias(ap,true,true,"Test takma ad");
            check(new DeviceIdentity(c).name(ap,true,true).equals("Test takma ad"),"Persistent alias");
            check(names.source(ap,true,true).contains("Kullanıcı"),"Alias provenance");
            names.setAlias(ap,true,true,"");
            check(names.name(ap,true,true).equals("KendiAgi"),"Alias removal");
            check(names.type(new JSONObject(),false).equals("BLE"),"Unknown device is not assumed phone");
            check(names.type(new JSONObject().put("appearance",64),false).contains("Telefon"),"BLE phone self-report");
            JSONObject anonymous=new JSONObject().put("mac","5a:06:fb:ec:25:f3").put("manufacturer",76);
            check(names.name(anonymous,false,true).contains("Apple, Inc. kimlikli yayın"),"Company evidence not invented device name");
            check(names.type(anonymous,false).equals("BLE"),"Apple identifier alone is not iPhone");
            EventJournal.clear(c);
            Intent event=new Intent().putExtra("message","test");
            check(EventJournal.record(c,"error",event,true),"Record error");
            check(!EventJournal.record(c,"error",event,true),"Debounce repeated error");
            check(EventJournal.read(c).getJSONObject(0).getString("title").startsWith("DEMO"),"DEMO label");
            check(!EventJournal.record(c,"spectrum",event,true),"No per-frame logging");
            for(int i=0;i<110;i++)EventJournal.record(c,"error",new Intent().putExtra("message","test-"+i),true);
            check(EventJournal.read(c).length()==100,"Bounded journal");
            check(EventJournal.read(c).getJSONObject(0).getString("detail").equals("test-109"),"Newest first");
            JSONObject generic=new JSONObject().put("mac","a4:cf:12:00:00:01");
            check(!DeviceRules.shouldNotify(DeviceRules.match(generic,"")),"OUI alone must not alarm");
            check(DeviceRules.shouldNotify(DeviceRules.match(ap,"KendiAgi")),"Custom name rule");
            check(c.checkSelfPermission("android.permission.VIBRATE")==PackageManager.PERMISSION_GRANTED,"Vibration permission");
            MainActivity activity=(MainActivity)startActivitySync(new Intent(c,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("simulator",true));
            final Throwable[] uiFailure={null};
            final android.view.ViewParent[] restoredParent={null};
            final int[] restoredCount={0};
            runOnMainSync(()->{
                try {
                    java.lang.reflect.Method apply=MainActivity.class.getDeclaredMethod("applyProfile",int.class);apply.setAccessible(true);
                    android.widget.Button[] buttons=(android.widget.Button[])field(activity,"profileButtons");
                    for(int selected=0;selected<3;selected++) {
                        apply.invoke(activity,selected);
                        for(int i=0;i<3;i++)check(buttons[i].isSelected()==(i==selected),"Only chosen preset highlighted");
                    }
                    java.lang.reflect.Method sync=MainActivity.class.getDeclaredMethod("applyDeviceSettings",JSONObject.class);sync.setAccessible(true);
                    sync.invoke(activity,new JSONObject().put("threshold",30).put("channels",28).put("hold",4000).put("cooldown",30000));
                    check(buttons[2].isSelected(),"Late hello cannot overwrite unsaved Sakin selection");
                    check(((android.widget.TextView)field(activity,"profileSummary")).getText().toString().contains("Sakin"),"Text confirms selected profile");
                    java.lang.reflect.Method update=MainActivity.class.getDeclaredMethod("handleUpdate",Intent.class);update.setAccessible(true);
                    JSONArray fixtures=new JSONArray()
                        .put(new JSONObject().put("mac","02:00:00:00:00:11").put("manufacturer",76).put("mfgPrefix","4c001219").put("mfgLength",29).put("signatureAgeMs",0))
                        .put(new JSONObject().put("mac","02:00:00:00:00:12").put("manufacturer",76))
                        .put(new JSONObject().put("mac","02:00:00:00:00:13").put("manufacturer",76).put("mfgPrefix","4c000215").put("mfgLength",25).put("signatureAgeMs",0))
                        .put(new JSONObject().put("mac","02:00:00:00:00:14").put("services","181a"));
                    update.invoke(activity,new Intent().putExtra("kind","ble").putExtra("json",new JSONObject().put("devices",fixtures).toString()));
                    java.lang.reflect.Method choose=MainActivity.class.getDeclaredMethod("selectDeviceCategory",int.class);choose.setAccessible(true);
                    java.lang.reflect.Method visible=MainActivity.class.getDeclaredMethod("visibleDevices");visible.setAccessible(true);
                    for(int category=3;category<=5;category++){
                        choose.invoke(activity,category);JSONArray filtered=(JSONArray)visible.invoke(activity);
                        check(filtered.length()==1,"Each specialized category has exactly one matching fixture");
                        check(!filtered.getJSONObject(0).optString("mac").endsWith("12"),"Apple company alone excluded from specialized categories");
                    }
                    choose.invoke(activity,1);check(((JSONArray)visible.invoke(activity)).length()==4,"BLE still shows all advertisements");
                    choose.invoke(activity,0);
                    java.lang.reflect.Field pending=MainActivity.class.getDeclaredField("pendingScanSamples");pending.setAccessible(true);pending.setInt(activity,12);
                    java.lang.reflect.Method confirm=MainActivity.class.getDeclaredMethod("confirmScanSamples",int.class);confirm.setAccessible(true);
                    confirm.invoke(activity,4);check(pending.getInt(activity)==12,"Old sample acknowledgement cannot clear newer choice");
                    confirm.invoke(activity,12);check(pending.getInt(activity)==0,"Matching sample acknowledgement completes pending choice");
                    check(((android.widget.Spinner)field(activity,"scanSpinner")).getSelectedItemPosition()==2,"Confirmed sample count updates picker");
                    SpectrumView source=(SpectrumView)field(activity,"homeSpectrum");
                    android.view.ViewParent oldParent=source.getParent();
                    int oldCount=((SpectrumHistory)field(source,"history")).size();
                    restoredParent[0]=oldParent;
                    restoredCount[0]=Math.min(512,oldCount+1);
                    java.lang.reflect.Method expand=MainActivity.class.getDeclaredMethod("expandSpectrum",SpectrumView.class,String.class);expand.setAccessible(true);
                    expand.invoke(activity,source,"Waterfall");
                    check(source.getParent()!=oldParent,"Fullscreen reparents original view");
                    source.push(new byte[126]);
                    check(((SpectrumHistory)field(source,"history")).size()==Math.min(512,oldCount+1),"Fullscreen continues same history");
                    ((android.app.Dialog)field(activity,"expandedDialog")).dismiss();
                    ((android.widget.SeekBar)field(activity,"thresholdBar")).setProgress(33);
                    for(android.widget.Button button:buttons)check(!button.isSelected(),"Custom values clear preset highlight");
                    SpectrumView view=new SpectrumView(c);view.layout(0,0,400,400);
                    for(int i=0;i<220;i++)view.push(new byte[126]);
                    long now=android.os.SystemClock.uptimeMillis();
                    android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,30,160,0);
                    android.view.MotionEvent move=android.view.MotionEvent.obtain(now,now+200,2,300,165,0);
                    android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+250,1,300,165,0);
                    android.widget.FrameLayout container=new android.widget.FrameLayout(c);container.addView(view);
                    view.onTouchEvent(down);view.onTouchEvent(move);view.onTouchEvent(up);
                    SpectrumHistory history=(SpectrumHistory)field(view,"history");
                    check(history.offset()>0,"Horizontal drag browses real history");
                    down.recycle();move.recycle();up.recycle();
                    view.clear();check(history.size()==0,"Clear also clears browsable history");
                    for(int i=0;i<512;i++){byte[] frame=new byte[126];frame[0]=(byte)(i%101);view.push(frame);}
                    float cellH=view.cellHeight(),cellW=view.cellWidth();
                    check(view.visibleRowCapacity()==96,"Card shows 96 rows");
                    view.setExpanded(true);view.layout(0,0,440,900);
                    check(Math.abs(view.cellHeight()-cellH)<.001f,"Fullscreen preserves exact row height");
                    check(Math.abs(view.cellWidth()-cellW)<.001f,"Fullscreen preserves channel cell width");
                    check(view.visibleRowCapacity()>96 && view.visibleRowCapacity()<=512,"Extra height adds older measurements");
                    check(history.size()==512 && history.offset()==0,"Expand retains live ring without resetting");
                    android.graphics.Bitmap rendered=(android.graphics.Bitmap)field(view,"bitmap");
                    int[] palette=(int[])field(view,"colors");
                    check(rendered.getPixel(0,96)==palette[(511-96)%101],"Row beyond original viewport is actual older measurement");
                    int expandedRows=view.visibleRowCapacity();
                    view.onTouchEvent(android.view.MotionEvent.obtain(now,now,0,30,160,0));
                    view.onTouchEvent(android.view.MotionEvent.obtain(now,now+200,2,300,165,0));
                    view.onTouchEvent(android.view.MotionEvent.obtain(now,now+250,1,300,165,0));
                    check(history.offset()>0 && history.offset()<=512-expandedRows,"Expanded history gesture respects full viewport");
                    view.setExpanded(false);view.layout(0,0,400,400);
                    check(view.visibleRowCapacity()==96 && Math.abs(view.cellHeight()-cellH)<.001f,"Compact restore keeps original density");
                    check(history.size()==512,"Compact restore retains entire ring");
                    view.setExpanded(true);view.layout(0,0,440,900);view.clear();view.push(new byte[126]);
                    check(history.size()==1 && rendered.getPixel(0,100)==palette[0],"Expanded blank history is not duplicated data");
                    float curveHeight=900-(Float)field(view,"plotBottom");
                    view.layout(0,0,440,2400);
                    check(view.visibleRowCapacity()==512,"Very tall viewport respects ring capacity");
                    check(Math.abs((2400-(Float)field(view,"plotBottom"))-curveHeight)<.001f,"Extra space beyond ring never stretches curve");
                    check(Math.abs(view.cellHeight()-cellH)<.001f,"Very tall viewport never stretches measurements");
                }catch(Throwable failure){uiFailure[0]=failure;}
            });
            // Dialog dismiss listeners are queued on the UI looper, not synchronous.
            waitForIdleSync();
            runOnMainSync(()->{
                try {
                    SpectrumView source=(SpectrumView)field(activity,"homeSpectrum");
                    check(source.getParent()==restoredParent[0],"Dismiss restores view to its card");
                    check(((SpectrumHistory)field(source,"history")).size()>=restoredCount[0],"Dismiss preserves history while live data may arrive");
                }catch(Throwable failure){if(uiFailure[0]==null)uiFailure[0]=failure;}
                finally{activity.finish();}
            });
            if(uiFailure[0]!=null)throw new AssertionError("UI regression",uiFailure[0]);
            result.putString("result","PASS: "+checks+" Android model and permission checks");
            passed=true;
        }catch(Throwable e){android.util.Log.e("SpectraTests","Regression failure",e);result.putString("result","FAIL: "+e+" / "+e.getCause());}
        finally{journal.edit().putString("entries",original).commit();}
        finish(passed?-1:0,result);
    }
}
