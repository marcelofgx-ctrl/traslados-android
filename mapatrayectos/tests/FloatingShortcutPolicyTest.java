import uy.com.mapatrayectos.FloatingShortcutPolicy;

public final class FloatingShortcutPolicyTest {
    private static void require(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        require(FloatingShortcutPolicy.canDisplay(false,true,false),"Idle minimizes to bubble");
        require(FloatingShortcutPolicy.offerOverlaySettingsOnLeave(false,false,false,false),
                "Home without grant opens Settings");
        require(!FloatingShortcutPolicy.offerOverlaySettingsOnLeave(true,false,false,false),
                "Granted overlay should show bubble without Settings");
        require(!FloatingShortcutPolicy.offerOverlaySettingsOnLeave(false,true,false,false),
                "No Settings loop while already requesting permission");
        require(!FloatingShortcutPolicy.offerOverlaySettingsOnLeave(false,false,true,false),
                "Do not interfere with initial Android runtime permission");
        require(!FloatingShortcutPolicy.offerOverlaySettingsOnLeave(false,false,false,true),
                "Internal navigation must not trigger permission Settings");
        require(FloatingShortcutPolicy.prearmBeforeLeaving(false,true),"Prepare idle foreground while visible");
        require(FloatingShortcutPolicy.shouldOfferPermissionIntro(false,false,true),"First app open offers overlay without starting shift");
        require(!FloatingShortcutPolicy.shouldOfferPermissionIntro(true,false,true),"No prompt if permission already granted");
        require(!FloatingShortcutPolicy.shouldOfferPermissionIntro(false,true,true),"No repeated prompts within one Activity");
        require(FloatingShortcutPolicy.shouldOfferPermissionIntro(false,false,true),
            "Next Activity relaunch must offer again even if an older version persisted an old flag");
        require(!FloatingShortcutPolicy.shouldOfferPermissionIntro(false,false,false),"Wait for runtime permission dialogs to close");
        require(!FloatingShortcutPolicy.prearmBeforeLeaving(false,false),"Never prepare without overlay permission");
        require(!FloatingShortcutPolicy.prearmBeforeLeaving(true,true),"Active journey is already foreground");
        require(!FloatingShortcutPolicy.collectLocation(false,false),"Prepared idle service must never collect GPS");
        require(!FloatingShortcutPolicy.canDisplay(true,true,false),"Open app must not overlay");
        require(!FloatingShortcutPolicy.canDisplay(false,false,false),"No overlay permission must block");
        require(!FloatingShortcutPolicy.canDisplay(false,true,true),"No duplicate floating shortcuts");
        require(FloatingShortcutPolicy.maintainIdleService(false,true,false),"Keep idle foreground alive");
        require(!FloatingShortcutPolicy.maintainIdleService(true,true,false),"Stop idle foreground after reopening");
        require(!FloatingShortcutPolicy.maintainIdleService(false,false,false),"No idle service without permission");
        require(FloatingShortcutPolicy.maintainIdleService(false,false,true),"Active journey preserved");
        require(!FloatingShortcutPolicy.collectLocation(false,false),"Never use GPS without journey");
        require(!FloatingShortcutPolicy.collectLocation(true,true),"No GPS during paused journey");
        require(FloatingShortcutPolicy.collectLocation(true,false),"Active journey still collects GPS");
        System.out.println("R23.1 idle floating shortcut PASS: visible, minimized, permission, GPS separation");
    }
}
