import dev.mobilecodex.app.core.*;
import org.json.*;
public class AndroidVerificationHarness {
 static void check(boolean v){if(!v)throw new AssertionError();}
 public static void main(String[] args)throws Exception{
  var c=new DeveloperBuild.Config("/jdk","/sdk","/aapt2","assembleDebug");
  check(AndroidVerification.command(c,"local",new JSONObject()).contains("assembleDebug lintDebug testDebugUnitTest"));
  var o=new JSONObject().put("serial","abc").put("api",29).put("appApk","app/build/app.apk").put("testApk","app/build/test.apk").put("runner","com.test/androidx.test.runner.AndroidJUnitRunner");
  check(AndroidVerification.command(c,"adb",o).contains("getprop ro.build.version.sdk"));
  try{AndroidVerification.command(c,"adb",new JSONObject(o.toString()).put("appApk","../bad.apk"));throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(!AndroidVerification.summarize("adb",o,0,"", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("passed"));
  check(AndroidVerification.summarize("adb",o,0,"MC_API=29\nOK (2 tests)\nMC_INSTRUMENT_EXIT=0\n", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("passed"));
  check(!AndroidVerification.summarize("adb",o,0,"MC_API=28\nOK (2 tests)\nMC_INSTRUMENT_EXIT=0\n", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("passed"));
  check(AndroidVerification.summarize("devices",o,0,"device", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("discovered"));
  var imported=AndroidVerification.importEvidence(new JSONObject().put("schema",1).put("kind","adb").put("status","passed").put("api",29).put("device","abc").put("fingerprint","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").put("completedAt",1),"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
  check(imported.getString("evidence").equals("imported"));
  check(!AndroidVerification.matrix(new JSONArray().put(29),new JSONArray().put(imported),"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").getJSONObject(0).getString("status").equals("passed"));
  check(!AndroidVerification.summarize("adb",o,0,"MC_API=29\nOK (0 tests)\nMC_INSTRUMENT_EXIT=0\n", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("passed"));
  check(!AndroidVerification.summarize("adb",o,0,"MC_API=29\nOK (2 tests)\nFAILURES!!!\nMC_INSTRUMENT_EXIT=0\n", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("passed"));
  check(AndroidVerification.importEvidence(new JSONObject().put("schema",1).put("kind","adb").put("status","passed").put("api",29).put("device","abc").put("fingerprint","bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb").put("completedAt",1),"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").getString("status").equals("stale"));
  var cloud=new JSONObject(o.toString()).put("model","Pixel2").put("project","valid-project");
  check(AndroidVerification.command(c,"cloud",cloud).contains("--format=json"));
  check(AndroidVerification.summarize("cloud",cloud,0,"{}","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("unverified"));
  check(AndroidVerification.summarize("connected",o,0,"BUILD SUCCESSFUL","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).isNull("api"));
  try{AndroidVerification.command(c,"adb",new JSONObject(o.toString()).put("api","29"));throw new AssertionError();}catch(IllegalArgumentException expected){}
  try{AndroidVerification.importEvidence(new JSONObject(imported.toString()).put("status","passed").put("completedAt",System.currentTimeMillis()+100000),"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");throw new AssertionError();}catch(IllegalArgumentException expected){}
  check(AndroidVerification.externalPlan(new JSONArray().put(29),o,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").getJSONArray("targets").getJSONObject(0).getString("adbCommand").contains("am instrument"));
  var dir=java.nio.file.Files.createTempDirectory("verification-fixture");
  try{
   var adb=dir.resolve("adb");java.nio.file.Files.writeString(adb,"#!/bin/sh\ncase \"$*\" in *getprop*) echo 29;; *instrument*) echo 'OK (2 tests)';; *install*) echo Success;; esac\n");adb.toFile().setExecutable(true);
   var process=new ProcessBuilder("/bin/sh","-c",AndroidVerification.command(c,"adb",o)).directory(dir.toFile()).redirectErrorStream(true);process.environment().put("PATH",dir+":"+System.getenv("PATH"));var run=process.start();String log=new String(run.getInputStream().readAllBytes());int code=run.waitFor();check(AndroidVerification.summarize("adb",o,code,log,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("passed"));
   java.nio.file.Files.writeString(adb,"#!/bin/sh\ncase \"$*\" in *getprop*) echo 28;; *install*) echo unexpected-install; exit 9;; esac\n");var mismatch=process.start();String rejected=new String(mismatch.getInputStream().readAllBytes());check(mismatch.waitFor()!=0&&!rejected.contains("unexpected-install"));
  }finally{java.nio.file.Files.deleteIfExists(dir.resolve("adb"));java.nio.file.Files.deleteIfExists(dir);}
  var artifact=AndroidVerification.summarize("adb",o,0,"MC_API=29\nOK (2 tests)\nMC_INSTRUMENT_EXIT=0\n","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1);
  check(!artifact.getBoolean("sourceVerified"));
  check(AndroidVerification.matrix(new JSONArray().put(29),new JSONArray().put(artifact),"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").getJSONObject(0).getString("status").equals("artifact-passed"));
  artifact.remove("sourceVerified");
  check(AndroidVerification.matrix(new JSONArray().put(29),new JSONArray().put(artifact),"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").getJSONObject(0).getString("status").equals("artifact-passed"));
  check(AndroidVerification.summarize("local",o,0,"BUILD SUCCESSFUL","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getBoolean("sourceVerified"));
  var cloudRows="[{\"test_axis_value\":\"Pixel2-29-en-portrait\",\"outcome\":\"Passed\"}]";
  check(AndroidVerification.summarize("cloud",cloud,0,cloudRows,"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("passed"));
  check(!AndroidVerification.summarize("cloud",cloud,0,cloudRows.replace("29","28"),"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",1).getString("status").equals("passed"));
  for(String status:new String[]{"running","cancelled","unverified","failed","passed"}){
   var record=new JSONObject().put("api",29).put("kind","adb").put("status",status).put("evidence","device").put("sourceVerified",true).put("completedAt",1).put("fingerprint","aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
   check(AndroidVerification.matrix(new JSONArray().put(29),new JSONArray().put(record),"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").getJSONObject(0).getString("status").equals(status));
  }
  var unbound=AndroidVerification.summarize("local",o,0,"BUILD SUCCESSFUL","",1);
  check(!unbound.getBoolean("sourceVerified"));
  check(unbound.getString("status").equals("unverified"));
  check(unbound.getString("executionStatus").equals("passed"));
  var direct=new JSONObject().put("api",29).put("kind","connected").put("status","passed").put("evidence","device").put("sourceVerified",true).put("completedAt",1).put("fingerprint","a".repeat(64));
  var simulated=new JSONObject(direct.toString()).put("kind","mock").put("evidence","simulation").put("status","mock-passed").put("completedAt",2);
  check(AndroidVerification.matrix(new JSONArray().put(29),new JSONArray().put(direct).put(simulated),"a".repeat(64)).getJSONObject(0).getString("status").equals("passed"));
  System.out.println("AndroidVerification harness passed (strict envelopes, matrix, cloud rows, fake ADB execution)");
 }
}
