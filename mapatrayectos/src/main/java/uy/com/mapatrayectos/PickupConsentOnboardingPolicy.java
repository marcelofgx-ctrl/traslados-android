package uy.com.mapatrayectos;

/** Decides whether to offer an explicit, one-time commercial GPS consent.
 * It never grants consent and must not appear while moving or in a trip.
 * Android permissions and authenticated driver identity are separate gates. */
public final class PickupConsentOnboardingPolicy {
    private PickupConsentOnboardingPolicy(){}
    public static boolean shouldOffer(boolean linkedDriver,boolean commercialConsentEnabled,
            boolean alreadyOffered,boolean anotherPermissionDialog,boolean tripActive,
            boolean vehicleMoving){
        return linkedDriver&&!commercialConsentEnabled&&!alreadyOffered
            &&!anotherPermissionDialog&&!tripActive&&!vehicleMoving;
    }
}
