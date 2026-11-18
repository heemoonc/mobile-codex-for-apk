import dev.mobilecodex.app.core.*;
import org.json.*;
import java.nio.file.*;
public class AndroidWorkflowChecksHarness {
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception {
  var c=new DeveloperBuild.Config("/jdk","/sdk","/aapt2","assembleDebug");String fp="a".repeat(64);
  check(AndroidWorkflowChecks.configuration(c).matches("[a-f0-9]{64}"),"configuration hash");
  check(!AndroidWorkflowChecks.configuration(c).equals(AndroidWorkflowChecks.configuration(new DeveloperBuild.Config("/other","/sdk","/aapt2","assembleDebug"))),"config invalidation");
  check(AndroidWorkflowChecks.command(c,"workflow-checks").contains("outputs.upToDateWhen"),"tests execute instead of reusing results");
  check(AndroidWorkflowChecks.summarize("workflow-environment",0,"MC_GUIDED_ENV=passed\n",fp,1).getString("status").equals("checked"),"explicit environment proof");
  check(!AndroidWorkflowChecks.summarize("workflow-environment",0,"",fp,1).getString("status").equals("checked"),"no exit-only environment pass");
  String good="BUILD SUCCESSFUL in 2s\nMC_GUIDED_TESTS=3 MC_GUIDED_FAILED=0 MC_GUIDED_SKIPPED=0\n";
  check(!AndroidWorkflowChecks.summarize("workflow-checks",0,good+"MC_GUIDED_TASK=:app:lintDebug MC_GUIDED_OUTCOME=SKIPPED\n",fp,1).getString("status").equals("passed"),"reject skipped required lint task even with passing tests");
  String outcomes="MC_GUIDED_TASK=:app:assembleDebug MC_GUIDED_OUTCOME=SUCCESS\nMC_GUIDED_TASK=:app:lintDebug MC_GUIDED_OUTCOME=SUCCESS\nMC_GUIDED_TASK=:app:testDebugUnitTest MC_GUIDED_OUTCOME=SUCCESS\n";
  check(!AndroidWorkflowChecks.summarize("workflow-checks",0,good+outcomes.replace(":app:lintDebug MC_GUIDED_OUTCOME=SUCCESS",":app:lintDebug MC_GUIDED_OUTCOME=SKIPPED"),fp,1).getString("status").equals("passed"),"reject skipped lint with all outcomes");
  check(!AndroidWorkflowChecks.summarize("workflow-checks",0,good+outcomes.replace(":app:testDebugUnitTest MC_GUIDED_OUTCOME=SUCCESS",":app:testDebugUnitTest MC_GUIDED_OUTCOME=NO-SOURCE"),fp,1).getString("status").equals("passed"),"reject no-source app test borrowing counts");
  good+=outcomes;
  var result=AndroidWorkflowChecks.summarize("workflow-checks",0,good,fp,1);
  check(result.getString("status").equals("passed")&&result.getLong("tests")==3,"positive test evidence");
  for(String invalid:new String[]{"BUILD SUCCESSFUL",good.replace("TESTS=3","TESTS=0"),good.replace("SKIPPED=0","SKIPPED=1"),good+good,good.replace("TESTS=3","TESTS=9999999999999999999999999")})
   check(!AndroidWorkflowChecks.summarize("workflow-checks",0,invalid,fp,1).getString("status").equals("passed"),"reject missing/zero/skipped/duplicate/overflow counts");
  check(AndroidWorkflowChecks.summarize("workflow-checks",1,good,fp,1).getString("status").equals("failed"),"nonzero exit wins");
  check(AndroidWorkflowChecks.summarize("workflow-checks",0,good,"",1).getString("status").equals("unverified"),"no source-bound pass without fingerprint");
  Path root=Files.createTempDirectory("guided-checks-");
  try{
   Path jdk=root.resolve("jdk/bin"),platform=root.resolve("sdk/platforms/android-35");Files.createDirectories(jdk);Files.createDirectories(platform);Files.writeString(platform.resolve("android.jar"),"");
   for(Path executable:new Path[]{jdk.resolve("java"),jdk.resolve("javac"),root.resolve("aapt2")}){Files.writeString(executable,"#!/bin/sh\nexit 0\n");executable.toFile().setExecutable(true);}
   var cfg=new DeveloperBuild.Config(root.resolve("jdk").toString(),root.resolve("sdk").toString(),root.resolve("aapt2").toString(),"assembleDebug");
   for(int count:new int[]{2,0}){
    Files.writeString(root.resolve("gradlew"),"#!/bin/sh\nprintf '%s' '"+outcomes+"'\necho BUILD SUCCESSFUL\necho 'MC_GUIDED_TESTS="+count+" MC_GUIDED_FAILED=0 MC_GUIDED_SKIPPED=0'\n");
    var p=new ProcessBuilder("/bin/sh","-c",AndroidWorkflowChecks.command(cfg,"workflow-checks")).directory(root.toFile()).redirectErrorStream(true).start();String log=new String(p.getInputStream().readAllBytes());int exit=p.waitFor();
    check(AndroidWorkflowChecks.summarize("workflow-checks",exit,log,fp,1).getString("status").equals(count>0?"passed":"unverified"),"actual generated shell and fake Gradle");
   }
  }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
  System.out.println("AndroidWorkflowChecks harness passed");
 }
}
