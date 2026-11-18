package dev.mobilecodex.app.p1;
import java.nio.file.Files;
import java.nio.file.Path;
public final class ProbeChecksHarness {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("probe-test-");
        Path file=root.resolve("synthetic");
        try {
            Files.writeString(file,"synthetic-marker");
            check("matched",ProbeChecks.readOutcome(file,"synthetic-marker"));
            check("mismatched",ProbeChecks.readOutcome(file,"other-marker"));
            check("error",ProbeChecks.readOutcome(root.resolve("missing"),"synthetic-marker"));
            check("error",ProbeChecks.readOutcome(root,"synthetic-marker"));
            check("abc",new String(ProbeChecks.readPrefix(new java.io.ByteArrayInputStream("abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8)),3),java.nio.charset.StandardCharsets.UTF_8));
            check("ab",new String(ProbeChecks.readPrefix(new java.io.ByteArrayInputStream("ab".getBytes(java.nio.charset.StandardCharsets.UTF_8)),3),java.nio.charset.StandardCharsets.UTF_8));
            check("denied",ProbeChecks.errnoOutcome(13));
            check("denied",ProbeChecks.errnoOutcome(1));
            check("error",ProbeChecks.errnoOutcome(2));
            check("error",ProbeChecks.errnoOutcome(5));
            check("error",ProbeChecks.errnoOutcome(22));
            System.out.println("ProbeChecks: 11 assertions passed");
        } finally {Files.deleteIfExists(file);Files.deleteIfExists(root);}
    }
    private static void check(String expected,String actual) {
        if(!expected.equals(actual))throw new AssertionError("expected "+expected+" got "+actual);
    }
}
