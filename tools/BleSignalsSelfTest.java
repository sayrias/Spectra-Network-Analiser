package com.spectra.analyzer;

public final class BleSignalsSelfTest {
    private static int count;
    private static void check(boolean b,String reason){count++;if(!b)throw new AssertionError(reason);}
    public static void main(String[] args){
        check(BleSignals.classify(76,"4c001219",29,"",0)==1,"Find My header");
        check(BleSignals.classify(76,"4C001219",29,"",60000)==1,"case insensitive");
        check(BleSignals.classify(76,"4c001219",28,"",0)==0,"truncated payload rejected");
        check(BleSignals.classify(76,"4c001219",29,"",60001)==0,"expired header rejected");
        check(BleSignals.classify(76,"4c001219",29,"",-1)==0,"negative age rejected");
        check(BleSignals.classify(6,"4c001219",29,"",0)==0,"company mismatch rejected");
        check(BleSignals.classify(76,"",0,"",0)==0,"Apple alone not AirTag");
        check(BleSignals.classify(76,"4c001005",9,"",0)==0,"other Apple traffic not AirTag");
        check(BleSignals.classify(76,"4c000215",25,"",0)==2,"iBeacon header");
        check(BleSignals.classify(76,"4c000215",24,"",0)==0,"truncated iBeacon rejected");
        check(BleSignals.classify(0,"",0,"180d",0)==3,"heart rate service");
        check(BleSignals.classify(0,"",0,"0000181a-0000-1000-8000-00805f9b34fb",0)==3,"environmental service");
        check(BleSignals.classify(0,"",0,"0181afff-0000-1000-8000-00805f9b34fb",0)==0,"no service substring guessing");
        check(BleSignals.classify(0,null,0,null,0)==0,"legacy missing metadata");
        System.out.println("[OK] "+count+" passive BLE signature checks");
    }
}
