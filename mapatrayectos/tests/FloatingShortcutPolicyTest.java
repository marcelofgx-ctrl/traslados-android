import uy.com.mapatrayectos.FloatingShortcutPolicy;

public final class FloatingShortcutPolicyTest {
    private static void require(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        require(FloatingShortcutPolicy.canDisplay(false,true,false),"Idle minimizes to bubble");
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
