package com.spectra.analyzer;
import java.util.Arrays;
/** Display-only azimuth: avoid marker collisions without changing RSSI radius. */
final class RadarLayout {
    static float[][] place(String[] keys, float[] radius, float spacing) {
        int n=keys.length;float[][] points=new float[n][2];
        Integer[] order=new Integer[n];for(int i=0;i<n;i++)order[i]=i;
        Arrays.sort(order,(a,b)->keys[a].compareTo(keys[b]));
        for(int k=0;k<n;k++) {
            int i=order[k];double base=(keys[i].hashCode()&0x7fffffff)%360;
            float best=-1,bx=0,by=0;
            for(int trial=0;trial<72;trial++) {
                int offset=trial==0?0:((trial+1)/2)*5*(trial%2==1?1:-1);
                double angle=Math.toRadians(base+offset);
                float x=(float)Math.cos(angle)*radius[i],y=(float)Math.sin(angle)*radius[i],nearest=Float.MAX_VALUE;
                for(int j=0;j<k;j++){int prev=order[j];nearest=Math.min(nearest,(float)Math.hypot(x-points[prev][0],y-points[prev][1]));}
                if(nearest>best){best=nearest;bx=x;by=y;}
                if(nearest>=spacing)break;
            }
            points[i][0]=bx;points[i][1]=by;
        }
        return points;
    }
}
