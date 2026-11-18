import dev.mobilecodex.app.core.AndroidEmulatorProbe;
public class AndroidEmulatorProbeHarness {
 public static void main(String[] args)throws Exception {
  String c=AndroidEmulatorProbe.command();
  if(!c.contains("-accel tcg")||!c.contains("timeout 12")||!c.contains("-S")||c.contains("pacman")||c.contains("curl"))throw new AssertionError();
  var r=AndroidEmulatorProbe.summarize(0,"MC_EMULATOR_TCG=ready",1);
  if(!r.getString("status").equals("readiness-only")||r.getBoolean("androidBootVerified")||!r.isNull("api"))throw new AssertionError();
  if(!AndroidEmulatorProbe.summarize(0,"MC_EMULATOR_TOOL=missing",1).getString("status").equals("unavailable"))throw new AssertionError();
  if(args.length==1)java.nio.file.Files.writeString(java.nio.file.Path.of(args[0]),c);
  System.out.println("PASS emulator readiness never establishes Android boot");
 }
}
