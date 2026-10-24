package qt.qa.errors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CapacityErrorContractTest {
    private static int checked=0;
    private static void ensure(boolean value,String msg) {
        if(!value) throw new AssertionError(msg);
    }
    public static void main(String[] args) throws Exception {
        List<String> rows=Files.readAllLines(Path.of(args[0]));
        for(String line: rows){
            if(line.isBlank() || line.startsWith("#")) continue;
            String[] parts=line.split("\\t",3);
            ensure(parts.length==3,"fixture shape: "+line);
            String code=parts[0].equals("-")?null:parts[0];
            boolean actual=CapacityErrorContract.isCapacityConflict(code,parts[1]);
            ensure(actual == parts[2].equals("BUSY"),"mismatch: "+line);
            String msg=CapacityErrorContract.capacityMessageOrNull(code,parts[1]);
            ensure(actual?(msg!=null && msg.startsWith("Conductor ocupado")):msg==null,
                "message classification: "+line);
            checked++;
        }
        ensure(!CapacityErrorContract.isCapacityConflict(null,null),"null must not be busy");
        ensure(!CapacityErrorContract.isCapacityConflict("23505",null),"missing constraint is not busy");
        System.out.println("PASS ANDROID QA: "+checked+" shared fixtures; strict SQLSTATE + index, generic errors excluded");
    }
}
