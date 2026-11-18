package dev.mobilecodex.app.core;

import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Guided checks are local evidence, never an Android OS or installation result. */
public final class AndroidWorkflowChecks {
 private AndroidWorkflowChecks() {}
 public static boolean supports(String kind){return Set.of("workflow-environment","workflow-checks").contains(kind);}
 public static String configuration(DeveloperBuild.Config config) {
  DeveloperBuild.command(config,false); // Validate all paths and the task before hashing.
  try{
   byte[] bytes=MessageDigest.getInstance("SHA-256").digest(String.join("\n",config.javaHome(),config.sdkRoot(),config.aapt2(),config.task()).getBytes(StandardCharsets.UTF_8));
   StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
  }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
 }
 public static String command(DeveloperBuild.Config config,String kind) {
  if(!supports(kind))throw new IllegalArgumentException("Unknown guided check");
  String check=DeveloperBuild.command(config,false)+"printf 'MC_GUIDED_ENV=passed\\n'\n";
  if(kind.equals("workflow-environment"))return check;
  String init="allprojects { tasks.withType(org.gradle.api.tasks.testing.Test).configureEach { if (path == ':app:testDebugUnitTest') { outputs.upToDateWhen { false }; outputs.cacheIf { false }; testLogging { showStandardStreams = true }; afterSuite { desc, result -> if (!desc.parent) println(\"MC_GUIDED_TESTS=${result.testCount} MC_GUIDED_FAILED=${result.failedTestCount} MC_GUIDED_SKIPPED=${result.skippedTestCount}\") } } } }\n"
   +"gradle.taskGraph.afterTask { task, state -> if (task.path in [':app:assembleDebug', ':app:lintDebug', ':app:testDebugUnitTest']) { def outcome = state.failure != null ? 'FAILED' : state.noSource ? 'NO-SOURCE' : state.skipped && state.skipMessage != 'UP-TO-DATE' ? 'SKIPPED' : 'SUCCESS'; println(\"MC_GUIDED_TASK=${task.path} MC_GUIDED_OUTCOME=${outcome}\") } }\n";
  return check+"[ -f ./gradlew ] || { echo '현재 프로젝트 루트에 gradlew가 없습니다.'; exit 2; }\nMC_GUIDE_INIT=$(mktemp)\ntrap 'rm -f \"$MC_GUIDE_INIT\"' EXIT HUP INT TERM\ncat > \"$MC_GUIDE_INIT\" <<'MC_GUIDE_INIT_EOF'\n"+init+"MC_GUIDE_INIT_EOF\n/bin/sh ./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest --no-daemon --max-workers=1 --console=plain -I \"$MC_GUIDE_INIT\" "+DeveloperBuild.quote("-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8")+" \"-Pandroid.aapt2FromMavenOverride=$AAPT2\"\n";
 }
 public static JSONObject summarize(String kind,int exit,String output,String fingerprint,long completedAt)throws JSONException {
  if(!supports(kind))throw new IllegalArgumentException("Unknown guided check");
  String log=output==null?"":output;
  boolean failed=exit!=0||Pattern.compile("(?m)^(?:FAILURE:.*|BUILD FAILED.*)\\s*$").matcher(log).find();
  String status=failed?"failed":"unverified";long tests=-1,failures=-1,skipped=-1;
  boolean bound=fingerprint!=null&&fingerprint.matches("[a-f0-9]{64}");
  if(kind.equals("workflow-environment")){
   Matcher env=Pattern.compile("(?m)^MC_GUIDED_ENV=passed\\s*$").matcher(log);int count=0;while(env.find())count++;
   if(!failed&&count==1)status="checked";
  }else{
   Matcher counts=Pattern.compile("(?m)^MC_GUIDED_TESTS=(\\d+) MC_GUIDED_FAILED=(\\d+) MC_GUIDED_SKIPPED=(\\d+)\\s*$").matcher(log);int count=0;boolean malformed=false;
   while(counts.find()){count++;try{tests=Long.parseLong(counts.group(1));failures=Long.parseLong(counts.group(2));skipped=Long.parseLong(counts.group(3));}catch(NumberFormatException invalid){malformed=true;}}
   boolean tasksPassed=true;
   for(String task:List.of(":app:assembleDebug",":app:lintDebug",":app:testDebugUnitTest")) {
    Matcher outcomes=Pattern.compile("(?m)^MC_GUIDED_TASK="+Pattern.quote(task)+" MC_GUIDED_OUTCOME=([A-Z-]+)\\s*$").matcher(log);
    int seen=0;String outcome="";while(outcomes.find()){seen++;outcome=outcomes.group(1);}
    if(seen!=1||!outcome.equals("SUCCESS"))tasksPassed=false;
   }
   if(failures>0)status="failed";
   else if(tasksPassed&&!failed&&count==1&&!malformed&&tests>0&&failures==0&&skipped==0&&Pattern.compile("(?m)^BUILD SUCCESSFUL(?: in .*)?\\s*$").matcher(log).find())status=bound?"passed":"unverified";
  }
  return new JSONObject().put("schema",1).put("kind",kind).put("scope","workflow").put("evidence",kind.equals("workflow-environment")?"environment":"local").put("status",status).put("exitCode",exit).put("fingerprint",fingerprint==null?"":fingerprint).put("sourceVerified",bound&&kind.equals("workflow-checks")&&status.equals("passed")).put("completedAt",completedAt).put("tests",tests).put("failed",failures).put("skipped",skipped).put("summary",status.equals("checked")?"개발 도구 실행 확인":status.equals("passed")?"빌드·Lint·단위 테스트 통과":status.equals("failed")?"실행 실패 · 로그 확인":"테스트 수·건너뜀·소스 연결을 확인하십시오.");
 }
}
