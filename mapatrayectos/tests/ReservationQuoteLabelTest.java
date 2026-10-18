import uy.com.traslados.conductor.ReservationQuoteLabel;

/** STATUS-001: no passenger/driver UI should call fixed-fare acceptance a quote.
 * Synthetic inputs. No backend, no Android context, no reservations. */
public class ReservationQuoteLabelTest {
  private static void check(boolean ok,String message){
    if(!ok)throw new AssertionError(message);
  }
  public static void main(String[] args){
    check("PRECIO CERRADO".equals(ReservationQuoteLabel.title("fixed","PRECIO_FIJO")),
      "Fixed fare pending must show agreed price");
    check("PRECIO CONFIRMADO".equals(ReservationQuoteLabel.title("fixed","ACEPTADO")),
      "Fixed fare accepted by driver must not display 'PRESUPUESTO ACEPTADO'");
    check("PRESUPUESTO ENVIADO".equals(ReservationQuoteLabel.title("schedule","ENVIADO")),
      "Legacy scheduled quotes must retain quote wording");
    check("PRESUPUESTO ACEPTADO".equals(ReservationQuoteLabel.title("schedule","ACEPTADO")),
      "Legacy accepted quotes must retain quote wording");
    check(ReservationQuoteLabel.title("schedule","SIN_PRESUPUESTO")==null,
      "No phantom budget on pending scheduled request");
    check(ReservationQuoteLabel.title("fixed","RECHAZADO")==null,
      "Rejected fixed fare needs no accepted-price detail");
    System.out.println("PASS STATUS-001 Android: fixed price vs legacy quote labels");
  }
}
