import uy.com.mapatrayectos.PickupConsentOnboardingPolicy;

public final class PickupConsentOnboardingPolicyTest {
    private static void check(boolean expected,boolean linked,boolean consent,
        boolean offered,boolean blocked,boolean trip,boolean moving,String caseName){
        boolean actual=PickupConsentOnboardingPolicy.shouldOffer(
            linked,consent,offered,blocked,trip,moving);
        if(actual!=expected)throw new AssertionError(caseName);
    }
    public static void main(String[] args){
        check(true,true,false,false,false,false,false,"linked first launch, missing consent");
        check(false,false,false,false,false,false,false,"unlinked: no commercial prompt");
        check(false,true,true,false,false,false,false,"already authorized");
        check(false,true,false,true,false,false,false,"only once");
        check(false,true,false,false,true,false,false,"Android permission sheet active");
        check(false,true,false,false,false,true,false,"trip in progress");
        check(false,true,false,false,false,false,true,"vehicle moving");
        check(true,true,false,false,false,false,false,"manual consent dialog still required");
        System.out.println("PASS PRESENCE-001: one-time startup offer, no tracking without opt-in");
    }
}
