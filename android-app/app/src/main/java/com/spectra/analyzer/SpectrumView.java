package com.spectra.analyzer;

import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

public final class SpectrumView extends View {
    private static final int CHANNELS=126, HISTORY=SpectrumHistory.CAPACITY;
    private final Bitmap bitmap=Bitmap.createBitmap(CHANNELS,HISTORY,Bitmap.Config.ARGB_8888);
    private final int[] pixels=new int[CHANNELS*HISTORY], colors=new int[101];
    private final byte[] current=new byte[CHANNELS], peaks=new byte[CHANNELS];
    private boolean peakHold;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG), bitmapPaint=new Paint();
    private final Path line=new Path();
    private int visibleRows=SpectrumHistory.VISIBLE;
    private boolean expanded;
    private float rowHeight, plotWidth, plotBottom, compactRowHeight, compactWidth, compactCurveHeight;
    private final SpectrumHistory history = new SpectrumHistory();
    private float downX, downY;
    private int downOffset;
    private boolean dragging;
    private long lastTap;
    private boolean demo, paused;
    private long rateWindowStart, last;
    private int rateWindowFrames;
    private float rate;
    public SpectrumView(Context c){this(c,null);}
    public SpectrumView(Context c,AttributeSet a){
        super(c,a);
        int[] stops={0xff040915,0xff102879,0xff006de5,0xff00d8ec,0xff40e478,0xffffe34b,0xffff4c31,0xffffebed};
        for(int v=0;v<=100;v++){
            float part=v*(stops.length-1)/100f; int i=Math.min(stops.length-2,(int)part); float t=part-i;
            colors[v]=Color.rgb((int)(Color.red(stops[i])*(1-t)+Color.red(stops[i+1])*t),
                (int)(Color.green(stops[i])*(1-t)+Color.green(stops[i+1])*t),
                (int)(Color.blue(stops[i])*(1-t)+Color.blue(stops[i+1])*t));
        }
        bitmap.eraseColor(colors[0]);
        java.util.Arrays.fill(pixels, colors[0]);
        setClickable(true);
        setContentDescription("Waterfall: yatay sürükleyerek ölçüm geçmişini incele, çift dokunarak canlıya dön");
    }
    public void setDemo(boolean value){demo=value; invalidate();}
    public void setPaused(boolean value){paused=value; invalidate();}
    public boolean isPaused(){return paused;}
    // Fullscreen adds history rows, never magnifies the compact measurement cells.
    public void setExpanded(boolean value){
        if(value==expanded)return;
        if(value){
            compactRowHeight=rowHeight;
            compactWidth=plotWidth;
            compactCurveHeight=getHeight()-plotBottom;
        }
        expanded=value;updateViewport();invalidate();
    }
    int visibleRowCapacity(){return visibleRows;}
    float cellHeight(){return rowHeight;}
    float cellWidth(){return plotWidth/CHANNELS;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);updateViewport();
    }
    private void updateViewport(){
        float top=dp(25), height=getHeight();
        if(expanded && compactRowHeight>0 && compactWidth>0){
            rowHeight=compactRowHeight;
            plotWidth=Math.min(getWidth(),compactWidth);
            plotBottom=Math.max(top+rowHeight,height-compactCurveHeight);
            visibleRows=Math.max(1,Math.min(HISTORY,(int)Math.floor((plotBottom-top)/rowHeight)));
        }else{
            visibleRows=SpectrumHistory.VISIBLE;
            plotWidth=getWidth();plotBottom=Math.max(top+1,height*.72f);
            rowHeight=(plotBottom-top)/visibleRows;
        }
        history.setVisibleRows(visibleRows);rebuildBitmap();
    }
    public void goLive(){history.seek(0);rebuildBitmap();invalidate();}
    public void setPeakHold(boolean value){peakHold=value;java.util.Arrays.fill(peaks,(byte)0);invalidate();}
    public void clear(){history.clear(); rateWindowStart=last=0; rateWindowFrames=0; rate=0; bitmap.eraseColor(colors[0]); java.util.Arrays.fill(pixels,colors[0]); java.util.Arrays.fill(current,(byte)0); java.util.Arrays.fill(peaks,(byte)0); invalidate();}
    public synchronized void push(byte[] values){
        if(paused || values==null || values.length!=CHANNELS)return;
        long now=SystemClock.elapsedRealtime();
        if(rateWindowStart==0) { rateWindowStart=now; rateWindowFrames=0; }
        else ++rateWindowFrames;
        if(now-rateWindowStart>=1000) {
            rate=rateWindowFrames*1000f/(now-rateWindowStart);
            rateWindowStart=now; rateWindowFrames=0;
        }
        last=now;
        history.push(values, now);
        for(int i=0;i<CHANNELS;i++)peaks[i]=(byte)Math.max(peaks[i]&255,values[i]&255);
        rebuildBitmap();
        postInvalidateOnAnimation();
    }
    private void rebuildBitmap() {
        for(int r=0;r<visibleRows;r++) {
            byte[] values=history.row(r);
            for(int i=0;i<CHANNELS;i++)pixels[r*CHANNELS+i]=colors[values==null?0:Math.min(100,values[i]&255)];
        }
        byte[] selected=history.row(0);
        if(selected!=null)System.arraycopy(selected,0,current,0,CHANNELS);
        bitmap.setPixels(pixels,0,CHANNELS,0,0,CHANNELS,visibleRows);
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=event.getX();downY=event.getY();downOffset=history.offset();dragging=false;return true;
            case MotionEvent.ACTION_MOVE:
                float dx=event.getX()-downX,dy=event.getY()-downY;
                if(!dragging && Math.abs(dx)>ViewConfiguration.get(getContext()).getScaledTouchSlop() && Math.abs(dx)>Math.abs(dy)) {
                    dragging=true;getParent().requestDisallowInterceptTouchEvent(true);
                }
                if(dragging){history.seek(downOffset+Math.round(dx/Math.max(1,plotWidth)*visibleRows));rebuildBitmap();invalidate();}
                return true;
            case MotionEvent.ACTION_UP:
                if(!dragging){
                    long now=SystemClock.elapsedRealtime();
                    if(now-lastTap<350){history.seek(0);rebuildBitmap();invalidate();lastTap=0;}else lastTap=now;
                    performClick();
                }
                getParent().requestDisallowInterceptTouchEvent(false);return true;
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);dragging=false;return true;
            default:return super.onTouchEvent(event);
        }
    }
    @Override protected synchronized void onDraw(Canvas c){
        super.onDraw(c);
        float w=plotWidth,h=getHeight(), top=dp(25), bottom=plotBottom;
        c.drawColor(colors[0]); paint.setStyle(Paint.Style.FILL);
        c.save();c.translate((getWidth()-w)/2,0);
        c.drawBitmap(bitmap,new Rect(0,0,CHANNELS,visibleRows),new RectF(0,top,w,top+visibleRows*rowHeight),bitmapPaint);
        paint.setColor(0x304e6b93); paint.setStrokeWidth(dp(.5f));
        for(int i=1;i<5;i++) c.drawLine(w*i/5,top,w*i/5,h-dp(18),paint);
        for(int i=1;i<4;i++) c.drawLine(0,top+(bottom-top)*i/4,w,top+(bottom-top)*i/4,paint);
        if(peakHold && history.offset()==0){
            line.reset();
            for(int i=0;i<CHANNELS;i++){
                float x=i*w/(CHANNELS-1),y=h-dp(23)-(peaks[i]&255)/100f*(h-bottom-dp(30));
                if(i==0)line.moveTo(x,y);else line.lineTo(x,y);
            }
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0xffede178);c.drawPath(line,paint);
        }
        line.reset();
        for(int i=0;i<CHANNELS;i++){
            float x=i*w/(CHANNELS-1), y=h-dp(23)-(current[i]&255)/100f*(h-bottom-dp(30));
            if(i==0)line.moveTo(x,y);else line.lineTo(x,y);
        }
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1.8f));paint.setColor(0xff75e9ff);
        c.drawPath(line,paint);paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dp(9));paint.setColor(0xff96abc6);
        String[] labels={"2400","2425","2450","2475","2500","2525"};
        for(int i=0;i<6;i++){
            float x=w*i/5;if(i==5)x-=paint.measureText(labels[i]);else if(i>0)x-=paint.measureText(labels[i])/2;
            c.drawText(labels[i],x,h-dp(5),paint);
        }
        paint.setColor(0xffa5c9e9);paint.setTextSize(dp(10));
        int rows=Math.min(visibleRows,history.size()-history.offset());
        String state=paused?"DURAKLATILDI":rows==0?"ÖLÇÜM BEKLENİYOR":
            SystemClock.elapsedRealtime()-last>3000?"VERİ AKIŞI KESİLDİ":
            String.format(java.util.Locale.US,"%.1f kare/sn · %d/%d satır · %d kayıt",rate,rows,visibleRows,history.size());
        if(demo)state="DEMO · "+state;
        if(history.offset()>0)state=String.format(java.util.Locale.US,"%sGEÇMİŞ · %.1f sn önce · çift dokun: canlı",demo?"DEMO · ":"",(SystemClock.elapsedRealtime()-history.time())/1000f);
        c.drawText(state,dp(5),dp(16),paint);
        c.restore();
        if(isShown())postInvalidateDelayed(1000);
    }
    private float dp(float v){return v*getResources().getDisplayMetrics().density;}
}
