package za.co.focigroup.focitrace;

public final class SelfTest {
    public static void main(String[] args) {
        check("T01 missing quotation", new TraceabilityEngine.Input(false,false,true,false,"Pending"), "-", "Incomplete");
        check("T02 missing supplier evidence", new TraceabilityEngine.Input(true,false,true,false,"Pending"), "-", "Incomplete");
        check("T03 missing specification", new TraceabilityEngine.Input(false,true,false,false,"Pending"), "DSG-312-201", "Clarification Required");
        check("T04 pending technical review", new TraceabilityEngine.Input(true,true,true,true,"Pending"), "DSG-315-200", "Pending Review");
        check("T05 complete record", new TraceabilityEngine.Input(true,true,true,true,"Reviewed"), "DSG-315-200", "Complete");
        System.out.println("FOCI TRACE self-tests: 5/5 PASS");
    }
    private static void check(String name, TraceabilityEngine.Input input, String spec, String expected) {
        String actual = TraceabilityEngine.evaluate(input, spec).overallStatus();
        if (!expected.equals(actual)) throw new AssertionError(name + " expected=" + expected + " actual=" + actual);
    }
}
