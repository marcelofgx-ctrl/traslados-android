package uy.com.mapatrayectos;

/** Single rule for user-entered completed trip amounts, including historical corrections. */
public final class TripClosePolicy {
    private TripClosePolicy(){}
    public static boolean validCompletedAmount(double amountUyu){
        return Double.isFinite(amountUyu)&&amountUyu>0;
    }
    public static boolean mayFinish(String status,double amountUyu){
        if("cancelled".equals(status))return true;
        return "completed".equals(status)&&validCompletedAmount(amountUyu);
    }
}
