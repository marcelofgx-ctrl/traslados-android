package uy.com.traslados.conductor;

/**
 * STATUS-001: presentation-only vocabulary for the embedded Conductor.
 * Server quote_status ACEPATADO can also represent direct acceptance of a
 * pre-agreed fixed-price trip; fare_mode resolves that ambiguity.
 * No Android context, persistence, network or business state changes.
 */
public final class ReservationQuoteLabel {
    private ReservationQuoteLabel() { }

    public static String title(String fareMode, String quoteStatus) {
        boolean fixed="fixed".equalsIgnoreCase(fareMode);
        if(fixed){
            if("PRECIO_FIJO".equals(quoteStatus))return "PRECIO CERRADO";
            if("ACEPTADO".equals(quoteStatus))return "PRECIO CONFIRMADO";
            return null;
        }
        if("ENVIADO".equals(quoteStatus))return "PRESUPUESTO ENVIADO";
        if("ACEPTADO".equals(quoteStatus))return "PRESUPUESTO ACEPTADO";
        return null;
    }
}
