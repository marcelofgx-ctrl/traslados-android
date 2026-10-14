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
        System.out.println("TripPlatformChoice tests passed");
    }
}
