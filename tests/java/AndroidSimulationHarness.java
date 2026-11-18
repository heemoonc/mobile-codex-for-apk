import dev.mobilecodex.app.core.*;
import org.json.*;
import java.nio.file.*;
public class AndroidSimulationHarness {
 static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
 static String fp="a".repeat(64);
 static String log="MC_ROBOLECTRIC_API=29\nMC_MOCK_TESTS=2 MC_MOCK_FAILED=0 MC_MOCK_SKIPPED=0\nBUILD SUCCESSFUL\n";
 public static void main(String[] args)throws Exception {
  check(AndroidSimulation.summarize(29,0,log,fp,1).getString("status").equals("mock-passed"),"complete baseline evidence");
  for(String bad:new String[]{"BUILD SUCCESSFUL",log.replace("API=29","API=30"),log.replace("TESTS=2","TESTS=0"),log.replace("SKIPPED=0","SKIPPED=2"),log.replace("SKIPPED=0","SKIPPED=1"),log.replace("TESTS=2","TESTS=1"),log.replace("FAILED=0","FAILED=1")})check(!AndroidSimulation.summarize(29,0,bad,fp,1).getString("status").equals("mock-passed"),"fail closed evidence");
  check(AndroidSimulation.summarize(29,0,log,"",1).isNull("api"),"unbound evidence");
  Path root=Files.createTempDirectory("simulation");Files.createDirectories(root.resolve("app"));Files.writeString(root.resolve("settings.gradle"),"include ':app'\n");String gradle="plugins { id 'com.android.application' }\nandroid {\n compileSdk 35\n defaultConfig { minSdk 23; targetSdk 35 }\n}\ndependencies {\n}\n";Files.writeString(root.resolve("app/build.gradle"),gradle);
  JSONObject plan=AndroidSimulation.plan(root);check(plan.getBoolean("canApply"),plan.toString());check(plan.getJSONArray("changes").length()==2,"two changes");
  Path backup=Files.createTempDirectory("simulation-backup");AndroidSimulation.apply(root,plan.getString("fingerprint"),backup);check(Files.readString(root.resolve("app/build.gradle")).contains("robolectric:4.18"),"pinned dependency");check(!AndroidSimulation.plan(root).getBoolean("canApply"),"repeat blocked");
  Files.writeString(root.resolve("app/build.gradle"),gradle+"\nandroid { testOptions {} }\n");check(!AndroidSimulation.plan(root).getBoolean("canApply"),"existing options blocked");
  try{AndroidSimulation.command(new DeveloperBuild.Config("/jdk","/sdk","/aapt2",":app:assembleDebug"),36);throw new AssertionError("unsupported API");}catch(IllegalArgumentException expected){}
  String command=AndroidSimulation.command(new DeveloperBuild.Config("/jdk","/sdk","/aapt2",":app:assembleDebug"),29);check(command.contains("robolectric.enabledSdks")&&command.contains("ApiSimulationTest")&&command.contains("--rerun-tasks"),"command isolates actual tests");
  check(AndroidSimulation.matrix(new JSONArray().put(29),new JSONArray().put(AndroidSimulation.summarize(29,0,log,fp,1)),fp).getJSONObject(0).getString("status").equals("mock-passed"),"separate matrix");
  JSONObject forged=new JSONObject().put("kind","mock").put("evidence","simulation").put("scope","baseline").put("requestedApi",29).put("api",29).put("status","mock-passed").put("fingerprint",fp).put("completedAt",1);
  check(!AndroidSimulation.matrix(new JSONArray().put(29),new JSONArray().put(forged),fp).getJSONObject(0).getString("status").equals("mock-passed"),"matrix rejects incomplete envelope");
  Path fresh=Files.createTempDirectory("simulation-stale");Files.createDirectories(fresh.resolve("app"));Files.writeString(fresh.resolve("settings.gradle"),"include ':app'\n");Files.writeString(fresh.resolve("app/build.gradle"),gradle);JSONObject preview=AndroidSimulation.plan(fresh);Files.writeString(fresh.resolve("gradle.properties"),"changed=true\n");try{AndroidSimulation.apply(fresh,preview.getString("fingerprint"),backup);throw new AssertionError("stale apply");}catch(java.io.IOException expected){}check(Files.readString(fresh.resolve("app/build.gradle")).equals(gradle),"stale apply leaves source");
  Files.writeString(fresh.resolve("settings.gradle"),"include ':app', ':other'\n");check(!AndroidSimulation.plan(fresh).getBoolean("canApply"),"multi-module rejected");
  Files.writeString(fresh.resolve("settings.gradle"),"include ':app'\n");Files.writeString(fresh.resolve("app/build.gradle"),gradle.replace("dependencies {","dependencies {\n testImplementation group: 'junit', name: 'junit', version: '4.12'"));check(!AndroidSimulation.plan(fresh).getBoolean("canApply"),"map dependency conflict rejected");
  Files.writeString(fresh.resolve("app/build.gradle"),gradle.replace("dependencies {","dependencies {\n implementation 'example:lib:1.+'"));check(!AndroidSimulation.plan(fresh).getBoolean("canApply"),"dynamic dependency rejected");
  Files.writeString(fresh.resolve("app/build.gradle"),gradle.replace("android {","android {\n testOptions { unitTests { includeAndroidResources=true } }"));check(!AndroidSimulation.plan(fresh).getBoolean("canApply"),"existing testOptions alone rejected");
  Files.writeString(fresh.resolve("app/build.gradle"),gradle.replace("plugins { id 'com.android.application' }",""));check(!AndroidSimulation.plan(fresh).getBoolean("canApply"),"non-Android module rejected");
  JSONObject partial=AndroidSimulation.summarize(29,0,log,fp,1).put("skipped",1);
  check(!AndroidSimulation.matrix(new JSONArray().put(29),new JSONArray().put(partial),fp).getJSONObject(0).getString("status").equals("mock-passed"),"matrix rejects partially skipped baseline");
  partial.put("skipped",0).put("tests",1);check(!AndroidSimulation.matrix(new JSONArray().put(29),new JSONArray().put(partial),fp).getJSONObject(0).getString("status").equals("mock-passed"),"matrix requires both baseline checks");
  Files.writeString(fresh.resolve("app/build.gradle"),gradle);
  Path linkParent=Files.createTempDirectory("simulation-backup-link"),outside=Files.createTempDirectory("simulation-outside");
  Path backupLink=linkParent.resolve("redirect");Files.createSymbolicLink(backupLink,outside);
  try {AndroidSimulation.apply(fresh,AndroidSimulation.plan(fresh).getString("fingerprint"),backupLink.resolve("child"));throw new AssertionError("backup child link accepted");}catch(java.io.IOException expected){check(expected.getMessage().contains("링크"),"backup link guard preserved");}
  check(Files.readString(fresh.resolve("app/build.gradle")).equals(gradle),"rejected backup leaves source unchanged");
  System.out.println("AndroidSimulation harness passed");
 }
}
