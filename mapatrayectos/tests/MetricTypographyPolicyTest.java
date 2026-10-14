import uy.com.mapatrayectos.MetricTypographyPolicy;

/** Regression for 4 time cards on narrow Samsung displays. */
public final class MetricTypographyPolicyTest {
  private static void between(float got,float low,float high,String message){
    if(got<low||got>high)throw new AssertionError(message+": "+got);
  }
  public static void main(String[] args){
    between(MetricTypographyPolicy.fitSp(80,64,14,8.5f),10.7f,11.0f,
      "00:00:18 must fit 64px instead of losing final digit");
    between(MetricTypographyPolicy.fitSp(50,66,14,8.5f),14,14,
      "short values retain premium size");
    between(MetricTypographyPolicy.fitSp(96,60,14,8.5f),8.5f,8.5f,
      "long hours use safe fallback");
    between(MetricTypographyPolicy.fitSp(80,70,14,8.5f),11.7f,12f,
      "small font differences should not overflow");
    between(MetricTypographyPolicy.fitSp(0,0,14,8.5f),8.5f,8.5f,
      "invalid bounds must not divide by zero");
    System.out.println("PASS: responsive HH:MM:SS cards, no clipped final digit");
  }
}
