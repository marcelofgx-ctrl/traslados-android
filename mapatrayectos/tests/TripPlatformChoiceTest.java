import uy.com.mapatrayectos.TripPlatformChoice;

public final class TripPlatformChoiceTest {
    private static void expect(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }
    public static void main(String[] args){
        expect(TripPlatformChoice.shouldPrompt(null),"first run prompts for category");
        expect(TripPlatformChoice.shouldPrompt(""),"empty choice prompts");
        expect(TripPlatformChoice.shouldPrompt("incorrect"),"invalid choice prompts");
        expect(!TripPlatformChoice.shouldPrompt("uber"),"Uber preselection is valid");
        expect(!TripPlatformChoice.shouldPrompt("cabify"),"Cabify preselection is valid");
        expect(!TripPlatformChoice.shouldPrompt("personal"),"Personal preselection is valid");
        expect(!TripPlatformChoice.shouldPrompt("other"),"Other preselection is valid");
        expect("cabify".equals(TripPlatformChoice.canonical(" CABIFY ")),"normalizes whitespace and case");
        expect("ELEGIR".equals(TripPlatformChoice.forBadge("",false,"uber")),"idle badge must NOT imply Uber");
        expect("CABIFY".equals(TripPlatformChoice.forBadge("cabify",false,"uber")),"planned badge shows selection");
        expect("UBER".equals(TripPlatformChoice.forBadge("cabify",true,"uber")),"active badge ALWAYS uses recorded trip type");
        expect("PERSONAL".equals(TripPlatformChoice.forBadge("other",true,"personal")),"active personal type wins");
        expect("OTRO".equals(TripPlatformChoice.display("other")),"other clearly labeled");
        expect("ELEGIR".equals(TripPlatformChoice.badgeLabel("",false,"uber")),"idle badge must not fake Uber");
        expect("PERS.".equals(TripPlatformChoice.badgeLabel("personal",false,"uber")),"personal is legible in a 48dp badge");
        expect("UBER".equals(TripPlatformChoice.badgeLabel("personal",true,"uber")),"badge uses actual active trip");
        expect("CABIFY".equals(TripPlatformChoice.badgeLabel("uber",true,"cabify")),"cabify active badge");
        expect("OTRO".equals(TripPlatformChoice.badgeLabel("other",true,"other")),"other remains visible");
        System.out.println("TripPlatformChoice tests passed");
    }
}
