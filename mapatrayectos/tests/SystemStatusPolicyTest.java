import uy.com.mapatrayectos.SystemStatusPolicy;

public final class SystemStatusPolicyTest {
    private static void assertEq(Object expected,Object actual,String name){
        if(!expected.equals(actual))throw new AssertionError(name+" expected "+expected+" got "+actual);
    }

    public static void main(String[] args){
        assertEq(11,SystemStatusPolicy.percent(55000000L,500000000L),"project usage");
        assertEq(100,SystemStatusPolicy.percent(600000000L,500000000L),"cap overflows");
        assertEq(-1,SystemStatusPolicy.percent(-1,500000000L),"missing value");
        assertEq(-1,SystemStatusPolicy.percent(100,0),"invalid quota");
        assertEq("HEALTHY",SystemStatusPolicy.level(320000000L,500000000L),"healthy");
        assertEq("WATCH",SystemStatusPolicy.level(325000000L,500000000L),"watch");
        assertEq("CRITICAL",SystemStatusPolicy.level(425000000L,500000000L),"critical");
        assertEq(445000000L,SystemStatusPolicy.free(55000000L,500000000L),"free space");
        assertEq(0L,SystemStatusPolicy.free(550000000L,500000000L),"zero space");
        assertEq("Sin datos",SystemStatusPolicy.mb(-1),"missing size");
        assertEq("0,0 MB",SystemStatusPolicy.mb(0),"zero size locale");
        System.out.println("SystemStatusPolicyTest PASS");
    }
}
