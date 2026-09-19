package com.spectra.analyzer;

import java.util.Arrays;
import java.nio.charset.StandardCharsets;

public final class ProtocolSelfTest {
    private static int checks;
    private static void check(boolean passed, String message) {
        ++checks;
        if (!passed) throw new AssertionError(message);
    }
    private static void checksum(byte[] p) {
        int crc = WireProtocol.crc16(p, 142);
        p[142] = (byte) crc; p[143] = (byte) (crc >> 8);
    }
    public static void main(String[] args) {
        check(WireProtocol.crc16("123456789".getBytes(StandardCharsets.US_ASCII), 9) == 0x29b1,
                "CRC-16/CCITT-FALSE known vector");
        byte[] p = new byte[144];
        p[0]='S'; p[1]='P'; p[2]='2'; p[3]='4'; p[4]=2; p[14]=125; p[15]=126;
        for (int i=16;i<142;i++) p[i]=(byte)(i%101);
        checksum(p);
        check(WireProtocol.validSpectrum(p,144),"Valid v2 spectrum");
        check(!WireProtocol.validSpectrum(null,144),"Null input");
        check(!WireProtocol.validSpectrum(new byte[10],144),"Truncated buffer");
        check(!WireProtocol.validSpectrum(p,143),"Wrong length");
        for(int i=0;i<p.length;i++) {
            byte[] corrupt=p.clone(); corrupt[i]^=1;
            check(!WireProtocol.validSpectrum(corrupt,144),"Bit corruption at "+i);
        }
        for(int field:new int[]{12,13,14,15,30}) {
            byte[] invalid=p.clone(); invalid[field]=(byte)255; checksum(invalid);
            check(!WireProtocol.validSpectrum(invalid,144),"Invalid range "+field);
        }
        WireProtocol.FragmentAssembler assembler=new WireProtocol.FragmentAssembler((byte)'S');
        for(int chunk:new int[]{16,60,180}) {
            int count=(p.length+chunk-1)/chunk;
            for(int i=count-1;i>=0;i--) {
                int len=Math.min(chunk,p.length-i*chunk);
                byte[] fragment=new byte[len+4];fragment[0]='S';fragment[1]=(byte)chunk;
                fragment[2]=(byte)i;fragment[3]=(byte)count;
                System.arraycopy(p,i*chunk,fragment,4,len);
                byte[] result=assembler.add(fragment);
                check(i==0?Arrays.equals(p,result):result==null,"Out-of-order MTU "+chunk+" fragment "+i);
            }
        }
        check(assembler.add(new byte[]{'J',1,0,1,1})==null,"Wrong marker");
        check(assembler.add(new byte[]{'S',1,1,1,1})==null,"Invalid fragment index");
        check(assembler.add(new byte[]{'S',1,0,0,1})==null,"Empty count");
        check(assembler.add(new byte[]{'S',1,0,10,1})==null,"Oversize spectrum");
        assembler.add(new byte[]{'S',1,0,2,1});
        check(Arrays.equals(assembler.add(new byte[]{'S',2,0,1,2}),new byte[]{2}),"New frame replaces incomplete old frame");
        System.out.println("[OK] "+checks+" production protocol checks");
    }
}
