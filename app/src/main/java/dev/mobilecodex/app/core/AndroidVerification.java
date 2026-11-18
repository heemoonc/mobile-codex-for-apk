package dev.mobilecodex.app.core;

import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Conservative command builder and evidence labels. Imported reports are never local proof. */
public final class AndroidVerification {
 private AndroidVerification(){}
 private static String text(JSONObject o,String key,String pattern){String s=o.optString(key,"");if(s.length()>512||!s.matches(pattern))throw new IllegalArgumentException("Invalid "+key);return s;}
 private static boolean integer(Object v){return v instanceof Byte||v instanceof Short||v instanceof Integer||v instanceof Long;}
 private static int api(JSONObject o){if(!integer(o.opt("api")))throw new IllegalArgumentException("Numeric API required");long n=o.optLong("api",0);if(n<16||n>100)throw new IllegalArgumentException("Invalid API");return (int)n;}
 private static String apk(JSONObject o,String key){String s=text(o,key,"[A-Za-z0-9_./ -]+\\.apk");if(s.startsWith("/")||Arrays.stream(s.split("/",-1)).anyMatch(p->p.isEmpty()||p.equals("..")||p.equals(".")))throw new IllegalArgumentException("Project APK required");return DeveloperBuild.quote("./"+s);}
 public static String command(DeveloperBuild.Config config,String kind,JSONObject options){
  JSONObject o=options==null?new JSONObject():options;
  if(kind.equals("devices"))return "set -eu\ncommand -v adb\nadb devices -l\n";
  if(kind.equals("cloud")){
   String model=text(o,"model","[A-Za-z0-9_-]+");int n=api(o);
   String project=o.optString("project","");if(!project.isEmpty())text(o,"project","[a-z][a-z0-9-]{4,62}");
   return "set -eu\ncommand -v gcloud\ngcloud firebase test android run --format=json --type instrumentation --app "+apk(o,"appApk")+" --test "+apk(o,"testApk")+" --device "+DeveloperBuild.quote("model="+model+",version="+n+",locale=en,orientation=portrait")+(project.isEmpty()?"":" --project "+DeveloperBuild.quote(project))+"\n";
  }
  if(kind.equals("adb")){
   String serial=text(o,"serial","[A-Za-z0-9_.:-]+");int n=api(o);String runner=text(o,"runner","[A-Za-z_][A-Za-z0-9_.]*/[A-Za-z_][A-Za-z0-9_.]*");String a="adb -s "+DeveloperBuild.quote(serial);
   return "set -eu\ncommand -v adb\ncommand -v timeout\nactual=$("+a+" shell getprop ro.build.version.sdk | tr -d '\\r')\nprintf 'MC_API=%s\\n' \"$actual\"\n[ \"$actual\" = "+DeveloperBuild.quote(""+n)+" ] || { echo 'API mismatch'; exit 3; }\n"+a+" install -r "+apk(o,"appApk")+"\n"+a+" install -r "+apk(o,"testApk")+"\nset +e\ntimeout 600 "+a+" shell am instrument -w "+DeveloperBuild.quote(runner)+"\nresult=$?\nprintf 'MC_INSTRUMENT_EXIT=%s\\n' \"$result\"\nexit \"$result\"\n";
  }
  if(!Set.of("local","connected").contains(kind))throw new IllegalArgumentException("Unknown verification kind");
  String connectedProbe=kind.equals("connected")?"command -v adb\nserials=$(adb devices | awk 'NR>1 && $2==\"device\" {print $1}')\n[ \"$(printf '%s\\n' \"$serials\" | awk 'NF {n++} END {print n+0}')\" = 1 ] || { echo 'Exactly one connected device required'; exit 3; }\nexport ANDROID_SERIAL=\"$serials\"\nprintf 'MC_SERIAL=%s\\n' \"$ANDROID_SERIAL\"\nactual=$(adb -s \"$ANDROID_SERIAL\" shell getprop ro.build.version.sdk | tr -d '\\r')\nprintf 'MC_API=%s\\n' \"$actual\"\n":"";
  return DeveloperBuild.command(config,false)+connectedProbe+"[ -f ./gradlew ] || exit 2\n/bin/sh ./gradlew "+(kind.equals("local")?"assembleDebug lintDebug testDebugUnitTest":"connectedDebugAndroidTest")+" --no-daemon --max-workers=1 --console=plain "+DeveloperBuild.quote("-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8")+" \"-Pandroid.aapt2FromMavenOverride=$AAPT2\"\n";
 }
 public static JSONObject summarize(String kind,JSONObject options,int exitCode,String output,String fingerprint,long completedAt) throws JSONException{
  if(!Set.of("local","connected","devices","adb","cloud").contains(kind))throw new IllegalArgumentException("Unknown verification kind");
  JSONObject o=options==null?new JSONObject():options;String out=output==null?"":output;Integer actual=null;
  Matcher m=Pattern.compile("(?m)^MC_API=(\\d+)\\s*$").matcher(out);if(m.find()){try{int n=Integer.parseInt(m.group(1));if(n>=16&&n<=100)actual=n;}catch(NumberFormatException ignored){}}
  boolean failed=Pattern.compile("(?i)(FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|Process crashed|shortMsg=|FAILURE:|BUILD FAILED|timed out)").matcher(out).find();
  String device=kind.equals("adb")?o.optString("serial",""):kind.equals("cloud")?o.optString("model",""):"";
  Matcher serial=Pattern.compile("(?m)^MC_SERIAL=([A-Za-z0-9_.:-]+)$").matcher(out);if(kind.equals("connected")&&serial.find())device=serial.group(1);
  String status=exitCode==0&&!failed?"unverified":"failed";
  if(kind.equals("devices")&&exitCode==0)status="discovered";
  if(kind.equals("local")&&exitCode==0&&!failed&&out.contains("BUILD SUCCESSFUL"))status="passed";
  if(kind.equals("adb")&&exitCode==0&&!failed&&actual!=null&&actual==o.optInt("api",-1)&&Pattern.compile("(?m)^OK \\([1-9]\\d* tests?\\)\\s*$").matcher(out).find()&&out.contains("MC_INSTRUMENT_EXIT=0"))status="passed";
  if(kind.equals("connected")&&exitCode==0&&!failed&&actual!=null&&!device.isEmpty()&&out.contains("BUILD SUCCESSFUL")&&Pattern.compile("(?i)([1-9]\\d* tests? completed|Starting [1-9]\\d* tests? on)").matcher(out).find())status="passed";
  if(kind.equals("cloud")&&exitCode==0&&!failed){
   // gcloud synchronous run renders result rows. Never interpret an empty/submitted matrix as success.
   try{int start=out.indexOf('['),end=out.lastIndexOf(']');if(start>=0&&end>start){JSONArray rows=new JSONArray(out.substring(start,end+1));if(rows.length()==1){JSONObject row=rows.getJSONObject(0);String axis=row.optString("test_axis_value",row.optString("testAxisValue",""));String outcome=row.optString("outcome","");String expected=o.optString("model")+"-"+o.optInt("api")+"-en-portrait";if(axis.equals(expected)&&outcome.equalsIgnoreCase("Passed")){actual=o.optInt("api");status="passed";}}}}catch(Exception ignored){}
  }
  String executionStatus=status;
  boolean sourceVerified=(kind.equals("local")||kind.equals("connected"))&&fingerprint!=null&&fingerprint.matches("[a-fA-F0-9]{64}");
  if(!sourceVerified&&(kind.equals("local")||kind.equals("connected"))&&status.equals("passed"))status="unverified";
  return new JSONObject().put("executionStatus",executionStatus).put("schema",1).put("kind",kind).put("status",status).put("exitCode",exitCode).put("sourceVerified",sourceVerified).put("api",actual==null?JSONObject.NULL:actual).put("device",device).put("completedAt",completedAt).put("fingerprint",fingerprint).put("evidence",kind.equals("local")?"local":kind.equals("cloud")?"cloud":"device").put("summary",kind+": "+status+" (exit "+exitCode+")");
 }
 public static JSONObject importEvidence(JSONObject value,String expectedFingerprint) throws JSONException{
  if(value==null||value.optInt("schema",0)!=1||!Set.of("local","connected","adb","cloud").contains(value.optString("kind"))||!Set.of("passed","failed","unverified").contains(value.optString("status"))||!(value.opt("fingerprint") instanceof String)||!value.optString("fingerprint").matches("[a-fA-F0-9]{64}")||!integer(value.opt("completedAt"))||value.optLong("completedAt",0)<1||value.optLong("completedAt")>System.currentTimeMillis()||!(value.opt("device") instanceof String)||value.optString("device").length()>512||!value.has("api"))throw new IllegalArgumentException("Invalid evidence envelope");
  if(!value.isNull("api")&&(!integer(value.opt("api"))||value.optLong("api",0)<16||value.optLong("api",0)>100))throw new IllegalArgumentException("Invalid API");
  return new JSONObject().put("schema",1).put("kind",value.getString("kind")).put("status",value.getString("fingerprint").equals(expectedFingerprint)?"imported":"stale").put("reportedStatus",value.getString("status")).put("api",value.get("api")).put("device",value.getString("device")).put("fingerprint",value.getString("fingerprint")).put("completedAt",value.getLong("completedAt")).put("evidence","imported").put("summary","Externally supplied report; not locally verified");
 }
 public static JSONArray matrix(JSONArray requestedApis,JSONArray records,String currentFingerprint) throws JSONException{
  JSONArray result=new JSONArray();Set<Integer> seen=new LinkedHashSet<>();
  for(int i=0;i<requestedApis.length();i++){if(!integer(requestedApis.opt(i)))continue;int n=requestedApis.optInt(i,0);if(n<16||n>100||!seen.add(n))continue;String state="unverified";JSONObject latest=null;
   for(int j=0;j<records.length();j++){JSONObject r=records.optJSONObject(j);if(r==null||!(r.optString("evidence").equals("imported")&&Set.of("local","connected","adb","cloud").contains(r.optString("kind"))||Set.of("connected","adb","cloud").contains(r.optString("kind"))&&Set.of("device","cloud").contains(r.optString("evidence")))||!integer(r.opt("api"))||r.optInt("api",0)!=n||!integer(r.opt("completedAt"))||r.optLong("completedAt")<1||r.optLong("completedAt")>System.currentTimeMillis())continue;if(latest==null||r.optLong("completedAt")>latest.optLong("completedAt"))latest=r;}
   if(latest!=null){if(!currentFingerprint.equals(latest.optString("fingerprint")))state="stale";else if(latest.optString("evidence").equals("imported"))state="imported";else if(Set.of("adb","connected","cloud").contains(latest.optString("kind"))&&Set.of("device","cloud").contains(latest.optString("evidence"))&&Set.of("passed","failed","unverified","running","cancelled").contains(latest.optString("status")))state=latest.optString("status");if(state.equals("passed")&&Set.of("adb","cloud").contains(latest.optString("kind"))&&!latest.optBoolean("sourceVerified",false))state="artifact-passed";}
   result.put(new JSONObject().put("api",n).put("status",state));
  }return result;
 }
 public static JSONObject externalPlan(JSONArray apis,JSONObject options,String fingerprint) throws JSONException{
  JSONObject o=options==null?new JSONObject():options;JSONArray targets=new JSONArray();
  for(int i=0;i<apis.length();i++){if(!integer(apis.opt(i)))continue;int n=apis.optInt(i);if(n<16||n>100)continue;JSONObject target=new JSONObject().put("api",n).put("status","unverified");
   JSONObject selected=new JSONObject(o.toString()).put("api",n);
   for(String kind:List.of("adb","cloud")){try{target.put(kind+"Command",command(null,kind,selected));}catch(IllegalArgumentException ex){target.put(kind+"Requirements",kind.equals("adb")?"serial, appApk, testApk, runner":"model, appApk, testApk; existing gcloud project/login");}}
   targets.put(target);
  }
  return new JSONObject().put("schema",1).put("fingerprint",fingerprint).put("matrix",matrix(apis,new JSONArray(),fingerprint)).put("targets",targets).put("instructions","Run only on an explicitly selected test device or authenticated cloud project. Commands install project APKs on the selected device; cloud runs may incur charges. No tests have run. Import reports as external evidence, never as local proof.");
 }
}
