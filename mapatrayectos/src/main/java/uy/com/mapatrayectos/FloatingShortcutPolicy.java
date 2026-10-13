package uy.com.mapatrayectos;

/**
 * R23.1 standalone policy: the floating shortcut is governed by app visibility,
 * overlay permission and whether the shortcut already exists, NOT by journey state.
 * Location collection is allowed only with an active, unpaused journey.
 */
public final class FloatingShortcutPolicy {
    private FloatingShortcutPolicy(){}
    public static boolean canDisplay(boolean appVisible,boolean overlayGranted,boolean alreadyShown){
        return !appVisible&&overlayGranted&&!alreadyShown;
    }
    /** Offer once per visible Activity launch, not once forever across APK upgrades.
     * Only Android's actual permission grant suppresses future prompts. */
    public static boolean shouldOfferPermissionIntro(boolean overlayGranted,
                                                     boolean alreadyOffered,boolean activityFocused){
        return activityFocused&&!overlayGranted&&!alreadyOffered;
    }
    public static boolean prearmBeforeLeaving(boolean shiftActive,boolean overlayGranted){
        return !shiftActive&&overlayGranted;
    }
    public static boolean maintainIdleService(boolean appVisible,boolean overlayGranted,boolean shiftActive){
        return shiftActive||(!appVisible&&overlayGranted);
    }
    public static boolean collectLocation(boolean shiftActive,boolean shiftPaused){
        return shiftActive&&!shiftPaused;
    }
}
