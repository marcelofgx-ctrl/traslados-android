import uy.com.mapatrayectos.QuickReservationLogic;

public class QuickReservationLogicTest {
    private static void verify(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args){
        verify(QuickReservationLogic.active("PENDIENTE"),"pending active");
        verify(QuickReservationLogic.active("EN_VIAJE"),"trip active");
        verify(!QuickReservationLogic.active("FINALIZADA"),"finished hidden");
        verify(!QuickReservationLogic.active("CANCELADA"),"cancelled hidden");
        verify(QuickReservationLogic.confirmed("ACEPTADA"),"accepted upcoming");
        verify(!QuickReservationLogic.confirmed("PENDIENTE"),"pending not confirmed");
        long at=QuickReservationLogic.atMillis("2026-10-15","09:30");
        verify(at>0,"valid pickup");
        verify(QuickReservationLogic.atMillis("2026-10-15","26:30")==-1,"invalid pickup");
        verify(QuickReservationLogic.conflicts(at,45,at+30L*60000,25),"overlap alert");
        verify(!QuickReservationLogic.conflicts(at,20,at+180L*60000,25),"no false conflict");
        verify(QuickReservationLogic.bucket("2026-10-15","2026-10-15","2026-10-16")==0,"today");
        verify(QuickReservationLogic.bucket("2026-10-16","2026-10-15","2026-10-16")==1,"tomorrow");
        verify(QuickReservationLogic.stale(1000L,51001L),"stale");
        verify(!QuickReservationLogic.stale(1000L,30000L),"fresh");
        System.out.println("R24.2 quick reservation logic PASS: dates, statuses, conflicts, staleness");
    }
}
