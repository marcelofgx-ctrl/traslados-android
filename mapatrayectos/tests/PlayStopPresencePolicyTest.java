import uy.com.mapatrayectos.PlayStopPresencePolicy;
public final class PlayStopPresencePolicyTest {
    static void require(boolean value,String reason){
        if(!value)throw new AssertionError(reason);
    }
    public static void main(String[] args){
        require(PlayStopPresencePolicy.canEnable(true,true),"play after link");
        require(!PlayStopPresencePolicy.canEnable(false,true),"no play if shift closed");
        require(!PlayStopPresencePolicy.canEnable(true,false),"unlinked cannot publish");
        require(PlayStopPresencePolicy.shouldRetry(true,true,false,true),"retry when unavailable");
        require(!PlayStopPresencePolicy.shouldRetry(true,false,false,true),"manual off prevents retry");
        require(!PlayStopPresencePolicy.shouldRetry(false,true,false,true),"stop prevents retry");
        require(!PlayStopPresencePolicy.shouldRetry(true,true,true,true),"already on does not reauthorize");
        require(PlayStopPresencePolicy.mustRevoke(true),"stop revokes");
        require(!PlayStopPresencePolicy.mustRevoke(false),"active shift remains permitted");
        System.out.println("PASS PLAY/STOP: linked device only; no repeated PIN; stop revokes");
    }
}
