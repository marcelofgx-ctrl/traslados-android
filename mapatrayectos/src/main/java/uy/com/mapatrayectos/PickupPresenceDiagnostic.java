package uy.com.mapatrayectos;

/** PRESENCE-001: finite, privacy-safe publication status. No coordinates, secrets or PIN. */
public final class PickupPresenceDiagnostic {
    private PickupPresenceDiagnostic(){}

    public static String response(boolean ok,String reason,boolean eligible,
            boolean active,boolean paused,boolean inTrip,boolean busy,boolean validGps){
        if(!ok)return "not_authorized".equals(reason)?"not_authorized":"server_rejected";
        if(!active)return "shift_inactive";
        if(paused)return "shift_paused";
        if(inTrip)return "trip_active";
        if(busy)return "manually_busy";
        if(!validGps)return "gps_not_usable";
        return eligible?"published":"server_not_eligible";
    }

    /** Do not expose raw exception messages; HTTP bodies may be sensitive. */
    public static String failure(Exception error){
        String m=error==null?"":String.valueOf(error.getMessage());
        if(m.startsWith("HTTP 401")||m.startsWith("HTTP 403"))return "http_authorization";
        if(m.startsWith("HTTP 5"))return "http_server";
        if(error instanceof java.net.SocketTimeoutException)return "timeout";
        return "connection_error";
    }

    public static String display(String code){
        if(code==null)return "Sin intentos registrados";
        switch(code){
            case "driver_not_linked":return "Play iniciado: falta vincular este teléfono una sola vez";
            case "consent_failed":return "Play iniciado: el servidor rechazó activar recogidas";
            case "consent_ready":return "Play: ubicación comercial autorizada; esperando GPS válido";
            case "sending":return "Enviando…";
            case "published":return "Servidor confirmó GPS válido para recogidas";
            case "shift_inactive":return "Envío recibido, pero sin jornada activa";
            case "shift_paused":return "Envío recibido: jornada en pausa";
            case "trip_active":return "Envío recibido: conductor en un viaje";
            case "manually_busy":return "Envío recibido: recogidas marcadas ocupadas";
            case "gps_not_usable":return "Envío recibido, pero el GPS comercial no es válido";
            case "server_not_eligible":return "Servidor respondió, sin confirmar posición comercial";
            case "not_authorized":return "Servidor no reconoce este teléfono: reautorizar recogidas";
            case "server_rejected":return "El servidor rechazó el envío";
            case "http_authorization":return "Falló la autorización HTTP del envío";
            case "http_server":return "El servidor devolvió un error HTTP";
            case "timeout":return "Se agotó el tiempo de espera del envío";
            case "connection_error":return "No se completó el envío al servidor";
            default:return "Sin resultado confirmado";
        }
    }
}
