package com.spectra.analyzer;

public final class HistorySelfTest {
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    public static void main(String[] args) {
        SpectrumHistory h=new SpectrumHistory();
        check(h.row(0)==null && h.time()==0,"empty, no fabricated measurements");
        h.seek(50);check(h.offset()==0,"empty clamp");
        h.push(null,1);h.push(new byte[125],1);check(h.size()==0,"invalid frame ignored");
        byte[] frame=new byte[126];
        for(int i=0;i<200;i++){frame[0]=(byte)(i%101);h.push(frame,i*170L);}
        frame[0]=99;check((h.row(0)[0]&255)==98,"defensive copy");
        check(h.size()==200 && h.maxOffset()==104,"viewport retains 96 rows");
        h.seek(70);long anchor=h.time();byte value=h.row(0)[0];
        h.push(new byte[126],34000);
        check(h.offset()==71 && h.time()==anchor && h.row(0)[0]==value,"history stays anchored during live arrivals");
        h.seek(9999);check(h.offset()==105 && h.row(95)!=null,"end clamp and complete viewport");
        h.seek(-1);check(h.offset()==0,"live return");
        for(int i=201;i<900;i++)h.push(new byte[126],i*170L);
        check(h.size()==512 && h.maxOffset()==416,"bounded ring wrap");
        h.seek(416);check(h.time()==483*170L,"correct wrapped frame");
        h.push(new byte[126],900*170L);check(h.offset()==416 && h.time()==484*170L,"oldest history expires honestly");
        h.clear();check(h.size()==0 && h.offset()==0 && h.row(0)==null,"clear removes history");
        for(int i=0;i<512;i++)h.push(new byte[126],i);
        h.seek(416);h.setVisibleRows(300);
        check(h.maxOffset()==212 && h.offset()==212,"expanded viewport clamps to full older page");
        check(h.row(299)!=null && h.row(300)==null,"expanded page ends at oldest real frame");
        h.setVisibleRows(96);check(h.offset()==212 && h.maxOffset()==416,"compact return retains selected anchor");
        h.setVisibleRows(9999);check(h.maxOffset()==0 && h.row(511)!=null,"capacity clamp shows whole ring");
        h.clear();h.push(new byte[126],1000);
        check(h.row(0)!=null && h.row(1)==null,"expanded start has no fabricated older rows");
        h.setVisibleRows(0);check(h.maxOffset()==0,"invalid zero viewport bounded");
        System.out.println("[OK] "+checks+" waterfall history checks");
    }
}
