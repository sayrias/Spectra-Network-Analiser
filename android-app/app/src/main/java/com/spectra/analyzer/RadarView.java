package com.spectra.analyzer;
import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import android.text.TextUtils;
import android.text.TextPaint;
import org.json.JSONArray;
import org.json.JSONObject;

/** Stable RSSI diagram. Angles are layout, never bearings. */
final class RadarView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text=new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private JSONArray devices=new JSONArray();
    private DeviceIdentity identities;
    private boolean wifi,demo;
    private int selected;
    private String selectedAddress="";
    private final java.util.Map<String,Float> smooth=new java.util.HashMap<>();
    private final float[] xs=new float[60],ys=new float[60];
    private java.util.function.IntConsumer listener;
    RadarView(Context c){super(c);setContentDescription("Sinyal gücü radarı. Numara listeyle eşleşir. Noktaya dokun: ayrıntı. Yön ve mesafe ölçülmez.");}
    void setOnDeviceSelected(java.util.function.IntConsumer value){listener=value;}
    void update(JSONArray values,DeviceIdentity identity,boolean isWifi,boolean isDemo){
        if(wifi!=isWifi||demo!=isDemo){smooth.clear();selectedAddress="";}
        devices=values;identities=identity;wifi=isWifi;demo=isDemo;selected=0;
        java.util.Set<String> alive=new java.util.HashSet<>();
        for(int i=0;i<devices.length();i++){
            JSONObject item=devices.optJSONObject(i);if(item==null)continue;
            String key=DeviceRules.address(item);alive.add(key);
            if(key.equals(selectedAddress))selected=i;
            float value=item.optInt("rssi",-100),previous=smooth.containsKey(key)?smooth.get(key):value;
            smooth.put(key,previous*.65f+value*.35f);
        }
        smooth.keySet().retainAll(alive);layoutPoints();invalidate();
    }
    private float density(){return getResources().getDisplayMetrics().density;}
    private float radius(){return Math.max(1,Math.min(getWidth()/2f-26*density(),(getHeight()-62*density())/2));}
    private void layoutPoints(){
        int n=Math.min(devices.length(),60);String[] keys=new String[n];float[] distances=new float[n];
        float r=radius(),d=density();
        for(int i=0;i<n;i++){
            JSONObject item=devices.optJSONObject(i);keys[i]=item==null?"missing"+i:DeviceRules.address(item);
            float rssi=smooth.containsKey(keys[i])?smooth.get(keys[i]):-100;
            boolean known=item!=null&&item.optBoolean("rssiKnown",true);
            distances[i]=r*(known?Math.max(.15f,Math.min(.93f,(-rssi-25)/75f)):1);
        }
        float[][] points=RadarLayout.place(keys,distances,22*d);
        for(int i=0;i<n;i++){xs[i]=getWidth()/2f+points[i][0];ys[i]=getHeight()/2f+3*d+points[i][1];}
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){layoutPoints();}
    @Override protected void onDraw(Canvas c){
        float d=density(),w=getWidth(),h=getHeight(),cx=w/2,cy=h/2+3*d,r=radius();
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d);paint.setColor(0xff3c332d);
        for(int i=1;i<=4;i++)c.drawCircle(cx,cy,r*i/4,paint);
        c.drawLine(cx-r,cy,cx+r,cy,paint);c.drawLine(cx,cy-r,cx,cy+r,paint);
        text.setTextSize(8*d);text.setColor(0xff8d8580);text.setTextAlign(Paint.Align.LEFT);
        c.drawText("Güçlü",cx+4*d,cy+12*d,text);c.drawText("Zayıf",cx+r-22*d,cy+12*d,text);
        text.setTextSize(10*d);text.setColor(0xffd8b69b);
        String label=devices.length()==0?"Bu listede henüz gözlem yok":identities.title(devices.optJSONObject(selected),wifi,demo);
        c.drawText(TextUtils.ellipsize(label,text,w-24*d,TextUtils.TruncateAt.END).toString(),12*d,18*d,text);
        for(int i=0;i<Math.min(devices.length(),60);i++){
            JSONObject item=devices.optJSONObject(i);if(item==null)continue;
            boolean known=item.optBoolean("rssiKnown",true);
            if(i==selected){
                paint.setStyle(Paint.Style.FILL);paint.setColor(0x18ff8d3a);c.drawCircle(xs[i],ys[i],17*d,paint);
                paint.setColor(0x30ffab55);c.drawCircle(xs[i],ys[i],12*d,paint);
            }
            paint.setColor(i==selected?0xffffb86b:known?0xffcd6327:0xff64646c);
            paint.setStyle(Paint.Style.FILL);c.drawCircle(xs[i],ys[i],9*d,paint);
            text.setColor(0xff100e0c);text.setTextSize(8*d);text.setTextAlign(Paint.Align.CENTER);
            c.drawText(String.valueOf(i+1),xs[i],ys[i]+3*d,text);
        }
        text.setTextAlign(Paint.Align.LEFT);text.setTextSize(9*d);text.setColor(0xffa7a1a0);
        c.drawText(devices.length()+" cihaz · açı şematik · gri: RSSI bilinmiyor",12*d,h-10*d,text);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN)return true;
        if(e.getAction()==MotionEvent.ACTION_UP){
            float best=24*density();int found=-1;
            for(int i=0;i<Math.min(devices.length(),60);i++){
                float dist=(float)Math.hypot(e.getX()-xs[i],e.getY()-ys[i]);if(dist<best){best=dist;found=i;}
            }
            if(found>=0){selected=found;selectedAddress=DeviceRules.address(devices.optJSONObject(found));invalidate();performClick();if(listener!=null)listener.accept(found);}
            return true;
        }
        return super.onTouchEvent(e);
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
