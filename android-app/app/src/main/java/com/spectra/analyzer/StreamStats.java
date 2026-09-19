package com.spectra.analyzer;

/** Sequence gaps are skipped measurements, not proof of network packet loss. */
final class StreamStats {
    long received,gaps,invalid,duplicates;
    private int sequence=-1;
    private long uptime;
    private String transport="";
    void reset(){received=gaps=invalid=duplicates=0;sequence=-1;uptime=0;transport="";}
    boolean accept(int next,long clock,String source){
        if(!source.equals(transport)){sequence=-1;transport=source;}
        if(sequence>=0){
            long elapsed=(clock-uptime)&0xffffffffL;
            // A large backwards uptime jump is a restart, not billions of missing frames.
            if(elapsed>0x80000000L && uptime-clock>3000){reset();transport=source;}
            else {
                int delta=(next-sequence)&0xffff;
                if(delta==0 || delta>32768 || elapsed>0x80000000L){duplicates++;return false;}
                gaps+=delta-1;
            }
        }
        sequence=next;uptime=clock;received++;return true;
    }
}
