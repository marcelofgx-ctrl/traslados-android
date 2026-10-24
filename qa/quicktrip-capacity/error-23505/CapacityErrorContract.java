package qt.qa.errors;

import java.util.Locale;

/**
 * EXCLUSIVELY fictitious QA/reference. Not bundled into Android, no APK.
 * Caller must supply PostgreSQL SQLSTATE separately from the constraint text.
 */
public final class CapacityErrorContract {
    public static final String BUSY_MESSAGE =
        "Conductor ocupado: terminá o cancelá el servicio anterior antes de aceptar otro.";
    private CapacityErrorContract(){}

    public static boolean isCapacityConflict(String code, String backendMessage) {
        if (!"23505".equals(code)) return false;
        String body = backendMessage == null ? "" : backendMessage.toLowerCase(Locale.ROOT);
        return body.contains("constraint \"qt_one_active_service_uq\"")
            || body.contains("index \"qt_one_active_service_uq\"");
    }

    public static String capacityMessageOrNull(String code, String backendMessage) {
        return isCapacityConflict(code, backendMessage) ? BUSY_MESSAGE : null;
    }
}
