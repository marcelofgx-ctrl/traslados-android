package lab.quicktrip;

import java.util.UUID;

/**
 * Pure Java design-only policy. This does NOT verify JWT signatures.
 * A real Android client must use the maintained Supabase Auth library,
 * and SQL must trust only claims verified by the Supabase gateway.
 */
public final class AuthContract {
    private AuthContract() {}
    public enum Reason {
        ALLOWED, NO_SESSION, UNVERIFIED, EXPIRED, REVOKED, ANONYMOUS,
        INACTIVE_DRIVER, OTHER_DRIVER, INVALID_RESERVATION
    }
    public static final class Session {
        public final UUID userId;
        public final boolean signatureVerified, revoked, anonymous;
        public final long expiresAt;
        public Session(UUID userId, boolean signatureVerified, boolean revoked,
                boolean anonymous, long expiresAt) {
            this.userId=userId;
            this.signatureVerified=signatureVerified;
            this.revoked=revoked;
            this.anonymous=anonymous;
            this.expiresAt=expiresAt;
        }
    }
    public static Reason canDecide(Session session, UUID authorizedDriverId,
            boolean driverActive, UUID reservationDriverId,
            long currentEpochSeconds) {
        if (session==null || session.userId==null) return Reason.NO_SESSION;
        if (!session.signatureVerified) return Reason.UNVERIFIED;
        if (session.expiresAt<=currentEpochSeconds) return Reason.EXPIRED;
        if (session.revoked) return Reason.REVOKED;
        if (session.anonymous) return Reason.ANONYMOUS;
        if (authorizedDriverId==null || !authorizedDriverId.equals(session.userId)
                || !driverActive) return Reason.INACTIVE_DRIVER;
        if (reservationDriverId==null) return Reason.INVALID_RESERVATION;
        if (!session.userId.equals(reservationDriverId)) return Reason.OTHER_DRIVER;
        return Reason.ALLOWED;
    }
    public static boolean isImmediateFixed(String mode, String fareMode,
            String quoteStatus, String status, double amount, double routeKm) {
        if (!"fixed".equals(fareMode) || !"PRECIO_FIJO".equals(quoteStatus)
             || !"PENDIENTE".equals(status)
             || !Double.isFinite(amount) || amount<=0
             || !Double.isFinite(routeKm) || routeKm<=0) return false;
        if ("now".equals(mode)) return true;
        if (mode==null || !mode.matches("^([1-9]|[1-9][0-9]|1[01][0-9]|120)$"))
            return false;
        return true;
    }
}
