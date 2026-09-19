package com.spectra.analyzer;

public final class PerformanceSelfTest {
    static int checks;
    static void check(boolean ok,String text){checks++;if(!ok)throw new AssertionError(text);}
    public static void main(String[] args){
        StreamStats s=new StreamStats();
        check(s.accept(65534,10000,"wifi"),"first");
        check(s.accept(65535,10200,"wifi"),"next");
        check(s.accept(0,10400,"wifi"),"sequence wrap");
        check(s.gaps==0,"wrap no gap");
        check(!s.accept(0,10400,"wifi"),"duplicate");
        check(!s.accept(65535,10200,"wifi"),"out of order");
        check(s.accept(4,11200,"wifi")&&s.gaps==3,"three missing measurements");
        check(s.accept(9,12200,"ble")&&s.gaps==3,"fallback baseline without artificial gaps");
        check(s.accept(1,100,"ble")&&s.received==1&&s.gaps==0,"restart clears session");
        s.reset();check(s.accept(2,0xfffffff0L,"wifi"),"clock near wrap");
        check(s.accept(3,40,"wifi")&&s.gaps==0,"32bit uptime wrap");
        check(s.received==2,"received only accepted");
        String[] keys=new String[16];float[] r=new float[16];
        for(int i=0;i<16;i++){keys[i]="test-mac-"+i;r[i]=90;}
        float[][] p=RadarLayout.place(keys,r,22);
        for(int i=0;i<16;i++){
            check(Math.abs(Math.hypot(p[i][0],p[i][1])-90)<.001,"RSSI radius preserved");
            for(int j=0;j<i;j++)check(Math.hypot(p[i][0]-p[j][0],p[i][1]-p[j][1])>=18,"marker overlap");
        }
        String[] reverse=new String[16];for(int i=0;i<16;i++)reverse[i]=keys[15-i];
        float[][] q=RadarLayout.place(reverse,r,22);
        for(int i=0;i<16;i++)check(p[i][0]==q[15-i][0]&&p[i][1]==q[15-i][1],"list reorder stability");
        check(RadarLayout.place(new String[0],new float[0],22).length==0,"empty inventory");
        String[] crowded=new String[60];float[] small=new float[60];
        for(int i=0;i<60;i++){crowded[i]="crowded"+i;small[i]=8;}
        for(float[] point:RadarLayout.place(crowded,small,22))check(Float.isFinite(point[0])&&Math.hypot(point[0],point[1])<8.001,"dense inventory bounded");
        System.out.println("[OK] "+checks+" stream/radar checks");
    }
}
