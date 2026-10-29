package uy.com.mapatrayectos.conductorv0;

import java.util.Arrays;
import java.util.Collections;
public final class DriverSheetRecoveryTest {
    private static int tests=0;
    private static void eq(String wanted,String got,String name){
        if(!wanted.equals(got))throw new AssertionError(name+": expected ["+wanted+"] got ["+got+"]");
        tests++;System.out.println("PASS C1.1 "+name);
    }
    private static void check(boolean ok,String name){
        if(!ok)throw new AssertionError(name);
        tests++;System.out.println("PASS C1.1 "+name);
    }
    public static void main(String[] args){
        eq("r1",DriverSheetPolicy.uniqueRecoveryId(
            Arrays.asList("r1"),Arrays.asList("ACEPTADA")),"one accepted");
        eq("r2",DriverSheetPolicy.uniqueRecoveryId(
            Arrays.asList("r2"),Arrays.asList("EN_VIAJE")),"one in journey");
        eq("",DriverSheetPolicy.uniqueRecoveryId(
            Arrays.asList("r1","r2"),Arrays.asList("ACEPTADA","EN_VIAJE")),"two eligible: no choice");
        eq("",DriverSheetPolicy.uniqueRecoveryId(
            Arrays.asList("r1","r1"),Arrays.asList("ACEPTADA","EN_VIAJE")),"duplicate row: no choice");
        eq("r1",DriverSheetPolicy.uniqueRecoveryId(
            Arrays.asList("r1","r2","r3"),Arrays.asList("PENDIENTE","ACEPTADA","FINALIZADA"))
            .replace("r2","r1"),"pending/finished not candidates");
        eq("",DriverSheetPolicy.uniqueRecoveryId(
            Arrays.asList("r1"),Arrays.asList("CANCELADA")),"canceled ignored");
        eq("",DriverSheetPolicy.uniqueRecoveryId(
            Arrays.asList("r1"),Arrays.asList("CONFIRMADA")),"unsupported status ignored");
        eq("",DriverSheetPolicy.uniqueRecoveryId(
            Collections.emptyList(),Collections.emptyList()),"empty snapshot");
        eq("",DriverSheetPolicy.uniqueRecoveryId(
            Arrays.asList("r1"),Collections.emptyList()),"mismatched fields");
        eq("",DriverSheetPolicy.uniqueRecoveryId(
            Arrays.asList(""),Arrays.asList("EN_VIAJE")),"empty id not selected");
        check(DriverSheetPolicy.next("ACEPTADA","EN_CAMINO",false)==DriverSheetPolicy.Action.NONE,
            "no action on stale snapshot");
        check(DriverSheetPolicy.next("ACEPTADA","EN_PUNTO",true)==DriverSheetPolicy.Action.START_TRIP,
            "confirmed pickup starts existing trip");
        check(DriverSheetPolicy.next("EN_VIAJE","",true)==DriverSheetPolicy.Action.FINISH_TRIP,
            "confirmed journey finishes existing trip");
        System.out.println("C1.1 RECOVERY "+tests+" PASS / 0 FAIL");
    }
}
