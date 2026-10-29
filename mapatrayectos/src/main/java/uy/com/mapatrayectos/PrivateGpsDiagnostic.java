package uy.com.mapatrayectos;

/**
 * In-memory, allowlisted diagnostic for the PRIVATE reservation GPS RPC only.
 * Does not persist or log coordinates, device/booking IDs, tokens, network
 * responses, exception text, or any other user data. Not an upload mechanism.
 */
public final class PrivateGpsDiagnostic {
    public enum Code {
        NO_ATTEMPT, ACKNOWLEDGED,
        NO_UNIQUE_RESERVATION, UNAUTHORIZED,
        GPS_UNAVAILABLE, STALE_GPS, SERVER_REJECTED,
        PRIVATE_TRANSPORT_ERROR, PUBLIC_TRANSPORT_ERROR,
        PUBLIC_REJECTED, CONDITIONS_NOT_MET
    }

    private static volatile Code last = Code.NO_ATTEMPT;

    private PrivateGpsDiagnostic() {}

    public static Code lastCode() { return last; }

    /** Never retain the raw JSON or reason. Match known server codes only. */
    public static void recordRpcResult(boolean ok, String reason) {
        if (ok) { last = Code.ACKNOWLEDGED; return; }
        if ("no_unique_active_reservation".equals(reason))
            last = Code.NO_UNIQUE_RESERVATION;
        else if ("unauthorized".equals(reason) || "not_authorized".equals(reason))
            last = Code.UNAUTHORIZED;
        else if ("gps_unavailable".equals(reason))
            last = Code.GPS_UNAVAILABLE;
        else if ("stale_gps".equals(reason))
            last = Code.STALE_GPS;
        else last = Code.SERVER_REJECTED;
    }

    /** Never pass the exception object or response body to this API. */
    public static void privateTransportError() { last = Code.PRIVATE_TRANSPORT_ERROR; }
    public static void publicTransportError() { last = Code.PUBLIC_TRANSPORT_ERROR; }
    public static void publicRejected() { last = Code.PUBLIC_REJECTED; }
    public static void skippedConditions() { last = Code.CONDITIONS_NOT_MET; }

    public static String message(boolean consentEnabled) {
        if (!consentEnabled) return "Recogida desactivada: no se publica ubicación.";
        switch (last) {
            case ACKNOWLEDGED: return "Última RPC privada: aceptada por el servidor. No confirma la posición en el pasajero.";
            case NO_UNIQUE_RESERVATION: return "GPS privado detenido: no existe una única reserva elegible.";
            case UNAUTHORIZED: return "GPS privado rechazado: autorización del dispositivo.";
            case GPS_UNAVAILABLE: return "GPS privado rechazado: precisión, ubicación o jornada.";
            case STALE_GPS: return "GPS privado rechazado: muestra GPS vencida.";
            case SERVER_REJECTED: return "GPS privado rechazado: otro motivo del servidor.";
            case PRIVATE_TRANSPORT_ERROR: return "GPS privado: error de comunicación (sin detalles sensibles).";
            case PUBLIC_TRANSPORT_ERROR: return "Presencia pública: error de comunicación; GPS privado no intentado.";
            case PUBLIC_REJECTED: return "Presencia pública rechazada; GPS privado no intentado.";
            case CONDITIONS_NOT_MET: return "GPS privado no intentado: faltan condiciones de presencia o GPS.";
            default: return "GPS privado: sin intentos observados en esta ejecución.";
        }
    }
}
