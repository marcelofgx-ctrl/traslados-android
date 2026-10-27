import uy.com.mapatrayectos.qa.LiveQaPolicy;
public class LiveQaPolicyTest {
 static void ok(boolean value){if(!value)throw new AssertionError("QA policy failed");}
 static void bad(String value){
  try{LiveQaPolicy.endpoint(value);throw new AssertionError("Unsafe endpoint accepted");}
  catch(IllegalArgumentException expected){}
 }
 public static void main(String[] args){
  ok("https://quicktrip-live-qa-demo.cuenta.workers.dev".equals(
    LiveQaPolicy.endpoint("https://quicktrip-live-qa-demo.cuenta.workers.dev/")));
  bad("https://zetaudvvutlouiqxopvg.supabase.co");
  bad("http://quicktrip-live-qa-demo.cuenta.workers.dev");
  bad("https://quicktrip-live-qa-demo.cuenta.workers.dev.evil.test");
  bad("https://quicktrip-live-qa-demo.cuenta.workers.dev/other");
  ok(LiveQaPolicy.valid(true,false,true,true,-34.9,-56.1,10,8000));
  ok(!LiveQaPolicy.valid(true,true,true,true,-34.9,-56.1,10,8000));
  ok(!LiveQaPolicy.valid(true,false,false,true,-34.9,-56.1,10,8000));
  ok(!LiveQaPolicy.valid(true,false,true,true,0,0,10,8000));
  ok(!LiveQaPolicy.valid(true,false,true,true,-34.9,-56.1,90,8000));
  ok(!LiveQaPolicy.valid(true,false,true,true,-34.9,-56.1,10,13000));
  System.out.println("PASS QA Live policy: no production, consent gates, GPS age/accuracy, shift/trip");
 }
}