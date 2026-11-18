package dev.mobilecodex.app.core;

import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Generated baseline mock checks in Robolectric, never proof of app or OS compatibility. */
public final class AndroidSimulation {
 private AndroidSimulation() {}
 private static final String TEST="app/src/test/java/dev/mobilecodex/checks/ApiSimulationTest.java";
 private static final int MAX=512*1024;
 private static final String SOURCE="package dev.mobilecodex.checks;\n"
  +"import android.app.Application;\nimport android.content.Context;\nimport org.junit.Test;\nimport org.junit.runner.RunWith;\nimport static org.junit.Assert.*;\nimport org.robolectric.RobolectricTestRunner;\nimport org.robolectric.RuntimeEnvironment;\nimport org.robolectric.annotation.*;\n"
  +"/** Generated baseline mock tests; not full application compatibility evidence. */\n"
  +"@RunWith(RobolectricTestRunner.class)\n@Config(sdk={29,30,31,32,33,34,35}, application=Application.class)\n@ConscryptMode(ConscryptMode.Mode.OFF)\n@GraphicsMode(GraphicsMode.Mode.LEGACY)\npublic class ApiSimulationTest {\n"
  +" @Test public void preferencesRoundTrip() {\n  Application app=RuntimeEnvironment.getApplication();\n  assertNotNull(app);\n  android.content.SharedPreferences prefs=app.getSharedPreferences(\"mc_baseline\",Context.MODE_PRIVATE);\n  assertTrue(prefs.edit().clear().putString(\"baseline\",\"simulation\").commit());\n  assertEquals(\"simulation\",prefs.getString(\"baseline\",null));\n  System.out.println(\"MC_ROBOLECTRIC_API=\"+RuntimeEnvironment.getApiLevel());\n }\n"
  +" @Test public void applicationAndFrameworkResources() {\n  Application app=RuntimeEnvironment.getApplication();\n  assertSame(app,app.getApplicationContext());\n  assertNotNull(app.getResources());\n  assertFalse(app.getResources().getString(android.R.string.ok).isEmpty());\n  System.out.println(\"MC_ROBOLECTRIC_API=\"+RuntimeEnvironment.getApiLevel());\n }\n}\n";
 private static Path safe(Path root,String relative)throws IOException {
  Path rel=Paths.get(relative);if(rel.isAbsolute()||relative.contains("\\"))throw new IOException("상대 경로가 필요합니다.");
  Path result=root;for(Path part:rel){if(part.toString().equals("..")||part.toString().equals("."))throw new IOException("상위 경로를 거부합니다.");result=result.resolve(part);if(Files.isSymbolicLink(result))throw new IOException("링크 경로를 거부합니다.");}return result;
 }
 private static Path root(Path input)throws IOException {
  Path r=input.toAbsolutePath().normalize();for(Path p=r;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("링크 프로젝트를 거부합니다.");if(!Files.isDirectory(r))throw new IOException("프로젝트 폴더가 없습니다.");return r.toRealPath();
 }
 private static String read(Path r,String rel)throws IOException {Path p=safe(r,rel);if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return "";if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>MAX)throw new IOException("지원하지 않는 파일: "+rel);byte[] bytes=Files.readAllBytes(p);if(bytes.length>MAX)throw new IOException("파일 크기 상한 초과");return new String(bytes,StandardCharsets.UTF_8);}
 private static void issue(JSONArray issues,String message)throws JSONException {issues.put(new JSONObject().put("severity","error").put("code","simulation-manual-review").put("message",message));}
 // Conservative lexer: comments and quoted strings are blanked without changing offsets.
 private static String mask(String text)throws IOException {
  StringBuilder out=new StringBuilder(text);char quote=0;boolean line=false,block=false,escape=false;int braces=0;
  for(int i=0;i<text.length();i++){char c=text.charAt(i),next=i+1<text.length()?text.charAt(i+1):0;
   if(line){if(c=='\n')line=false;else out.setCharAt(i,' ');continue;}
   if(block){out.setCharAt(i,' ');if(c=='*'&&next=='/'){out.setCharAt(++i,' ');block=false;}continue;}
   if(quote!=0){out.setCharAt(i,' ');if(escape)escape=false;else if(c=='\\')escape=true;else if(c==quote)quote=0;continue;}
   if(c=='/'&&next=='/'){out.setCharAt(i,' ');out.setCharAt(++i,' ');line=true;continue;}if(c=='/'&&next=='*'){out.setCharAt(i,' ');out.setCharAt(++i,' ');block=true;continue;}
   if(c=='\''||c=='"'){quote=c;out.setCharAt(i,' ');continue;}if(c=='{')braces++;if(c=='}'&&--braces<0)throw new IOException("DSL 괄호가 잘못되었습니다.");
  }if(quote!=0||block||braces!=0)throw new IOException("닫히지 않은 DSL입니다.");return out.toString();
 }
 private static int block(String masked,String name)throws IOException {Matcher m=Pattern.compile("\\b"+name+"\\s*\\{").matcher(masked);if(!m.find())throw new IOException(name+" 블록이 없습니다.");int start=m.start(),end=m.end(),depth=0;for(int i=0;i<start;i++){if(masked.charAt(i)=='{')depth++;if(masked.charAt(i)=='}')depth--;}if(depth!=0||m.find())throw new IOException("단일 최상위 "+name+" 블록만 지원합니다.");return end;}
 public static JSONObject plan(Path input)throws IOException,JSONException {
  Path r=root(input);JSONObject info=AndroidProjectDoctor.inspect(r);JSONArray issues=new JSONArray(),changes=new JSONArray();String before=read(r,"app/build.gradle"),after=before;
  try {
   if(!info.getString("modulePath").equals("app/build.gradle")||Files.exists(safe(r,"settings.gradle.kts")))throw new IOException("기존 단일 app/build.gradle Groovy 모듈만 지원합니다.");
   String settings=read(r,"settings.gradle");String stripped=settings.replaceAll("(?m)^\\s*include\\s*(?:\\(\\s*)?['\"]:app['\"]\\s*\\)?\\s*;?\\s*$","");
   if(Pattern.compile("\\b(include|includeBuild|projectDir|versionCatalogs)\\b").matcher(mask(stripped)).find()||!Pattern.compile("(?m)^\\s*include\\s*(?:\\(\\s*)?['\"]:app['\"]\\s*\\)?\\s*;?\\s*$").matcher(settings).find())throw new IOException("정적인 단일 :app include만 지원합니다.");
   JSONObject sdk=info.getJSONObject("sdk");if(sdk.isNull("min")||sdk.isNull("target")||sdk.isNull("compile"))throw new IOException("정적 SDK 리터럴을 확인할 수 없습니다.");
   JSONArray declared=info.getJSONArray("dependencies");for(int i=0;i<declared.length();i++)if(declared.getJSONObject(i).optBoolean("dynamic"))throw new IOException("동적 의존성은 수동 확인이 필요합니다.");
   if(!Pattern.compile("\\bid\\s*(?:\\(\\s*)?['\"]com\\.android\\.application['\"]").matcher(before).find())throw new IOException("명시적인 Android application 플러그인이 필요합니다.");
   String code=mask(before);int android=block(code,"android"),deps=block(code,"dependencies");
   if(Pattern.compile("\\b(testOptions|unitTests|includeAndroidResources|apply|evaluate|subprojects|allprojects)\\b").matcher(code).find()||before.contains("libs.")||before.contains("alias(")||Pattern.compile("\\bjunit\\b").matcher(before).find()||before.contains("robolectric")||before.contains("$"))throw new IOException("기존 테스트 설정·의존성·동적 DSL은 수동 검토가 필요합니다.");
   if(Files.exists(safe(r,TEST),LinkOption.NOFOLLOW_LINKS))throw new IOException("생성 대상 테스트가 이미 있습니다. 덮어쓰지 않습니다.");
   String options="\n    // Generated baseline mock simulation configuration\n    testOptions { unitTests { includeAndroidResources = true } }\n";
   String dependencies="\n    testImplementation 'junit:junit:4.13.2'\n    testImplementation 'org.robolectric:robolectric:4.18'\n";
   if(android>deps)after=before.substring(0,android)+options+before.substring(android);else after=before.substring(0,deps)+dependencies+before.substring(deps);
   if(android>deps)after=after.substring(0,deps)+dependencies+after.substring(deps);else after=after.substring(0,android)+options+after.substring(android);
   changes.put(new JSONObject().put("path","app/build.gradle").put("before",before).put("after",after));changes.put(new JSONObject().put("path",TEST).put("before",JSONObject.NULL).put("after",SOURCE));
  }catch(IOException blocked){issue(issues,blocked.getMessage());}
  return new JSONObject().put("fingerprint",info.getString("fingerprint")).put("canApply",issues.length()==0&&changes.length()==2).put("changes",changes).put("issues",issues).put("supportedApis",new JSONArray(Arrays.asList(29,30,31,32,33,34,35))).put("summary","생성된 기준 모의 테스트용 Robolectric 시뮬레이션입니다. 실제 OS·전체 앱 호환성 증명이 아닙니다.");
 }
 public static synchronized JSONObject apply(Path input,String expectedFingerprint,Path backupRoot)throws IOException,JSONException {
  Path r=root(input);JSONObject p=plan(r);if(!p.getString("fingerprint").equals(expectedFingerprint))throw new IOException("미리보기 이후 프로젝트가 변경되었습니다.");if(!p.getBoolean("canApply"))throw new IOException("자동 적용할 수 없습니다.");
  Path b=backupRoot.toAbsolutePath().normalize();for(Path q=b;q!=null;q=q.getParent())if(Files.isSymbolicLink(q))throw new IOException("백업 링크를 거부합니다.");if(b.startsWith(r))throw new IOException("백업은 프로젝트 외부에 저장하십시오.");Files.createDirectories(b);b=Files.createTempDirectory(b,"android-simulation-");
  JSONArray changes=p.getJSONArray("changes");List<Path> written=new ArrayList<>();
  for(int i=0;i<changes.length();i++){JSONObject c=changes.getJSONObject(i);if(!c.isNull("before")){Path backup=b.resolve(c.getString("path"));Files.createDirectories(backup.getParent());Files.write(backup,c.getString("before").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);}}
  try {
   if(!plan(r).getString("fingerprint").equals(expectedFingerprint))throw new IOException("적용 직전 프로젝트가 변경되었습니다.");
   for(int i=0;i<changes.length();i++){JSONObject c=changes.getJSONObject(i);String rel=c.getString("path");Path target=safe(r,rel);Files.createDirectories(target.getParent());safe(r,rel);
    boolean fresh=c.isNull("before");if(fresh?Files.exists(target,LinkOption.NOFOLLOW_LINKS):!read(r,rel).equals(c.getString("before")))throw new IOException("동시 편집을 감지했습니다.");
    Path temp=Files.createTempFile(target.getParent(),".simulation-",".tmp");try{Files.write(temp,c.getString("after").getBytes(StandardCharsets.UTF_8));safe(r,rel);if(fresh){Files.move(temp,target);}else Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);written.add(target);}finally{Files.deleteIfExists(temp);}
   }
  }catch(IOException failure){for(int i=written.size()-1;i>=0;i--){JSONObject c=changes.getJSONObject(i);Path target=written.get(i);try{safe(r,c.getString("path"));if(!read(r,c.getString("path")).equals(c.getString("after")))continue;if(c.isNull("before"))Files.delete(target);else{Path temp=Files.createTempFile(target.getParent(),".simulation-recover-",".tmp");try{Files.write(temp,c.getString("before").getBytes(StandardCharsets.UTF_8));Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}}catch(IOException recovery){failure.addSuppressed(recovery);}}throw failure;}
  return new JSONObject().put("applied",true).put("backupId",b.getFileName().toString()).put("fingerprint",AndroidProjectDoctor.inspect(r).getString("fingerprint"));
 }
 private static void api(int api){if(api<29||api>35)throw new IllegalArgumentException("Robolectric 기준 모의 시뮬레이션은 API 29~35만 지원합니다. API 36은 지원하지 않습니다.");}
 public static boolean prepared(Path input){try{Path r=root(input);return !read(r,TEST).isBlank()&&read(r,"app/build.gradle").contains("org.robolectric:robolectric:");}catch(IOException unsafe){return false;}}
 public static String command(DeveloperBuild.Config config,int api) {
  api(api);String preflight=DeveloperBuild.command(config,false);
  String init="allprojects { tasks.withType(org.gradle.api.tasks.testing.Test).configureEach { systemProperty 'robolectric.enabledSdks', '"+api+"'; testLogging { showStandardStreams = true }; afterSuite { desc, result -> if (!desc.parent) println(\"MC_MOCK_TESTS=${result.testCount} MC_MOCK_FAILED=${result.failedTestCount} MC_MOCK_SKIPPED=${result.skippedTestCount}\") } } }\n";
  return preflight+"echo 'Generated baseline mock simulation only; not OS or full app compatibility proof.'\nMC_SIM_INIT=$(mktemp)\ntrap 'rm -f \"$MC_SIM_INIT\"' EXIT HUP INT TERM\ncat > \"$MC_SIM_INIT\" <<'MC_SIM_INIT_EOF'\n"+init+"MC_SIM_INIT_EOF\n/bin/sh ./gradlew :app:testDebugUnitTest --tests dev.mobilecodex.checks.ApiSimulationTest --rerun-tasks --no-daemon --max-workers=1 --console=plain -I \"$MC_SIM_INIT\" "+DeveloperBuild.quote("-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8")+" \"-Pandroid.aapt2FromMavenOverride=$AAPT2\"\n";
 }
 public static JSONObject summarize(int requestedApi,int exitCode,String output,String fingerprint,long completedAt)throws JSONException {
  api(requestedApi);String log=output==null?"":output;Matcher runtime=Pattern.compile("(?m)^\\s*MC_ROBOLECTRIC_API=(\\d+)\\s*$").matcher(log);boolean seen=false,mismatch=false;while(runtime.find()){seen=true;if(!runtime.group(1).equals(String.valueOf(requestedApi)))mismatch=true;}
  Matcher counts=Pattern.compile("(?m)^\\s*MC_MOCK_TESTS=(\\d+) MC_MOCK_FAILED=(\\d+) MC_MOCK_SKIPPED=(\\d+)\\s*$").matcher(log);long tests=-1,failed=-1,skipped=-1;boolean duplicate=false;while(counts.find()){if(tests!=-1)duplicate=true;try{tests=Long.parseLong(counts.group(1));failed=Long.parseLong(counts.group(2));skipped=Long.parseLong(counts.group(3));}catch(NumberFormatException bad){duplicate=true;}}
  boolean success=exitCode==0&&seen&&!mismatch&&!duplicate&&tests>=2&&skipped==0&&failed==0&&Pattern.compile("(?m)^BUILD SUCCESSFUL(?: in .*)?\\s*$").matcher(log).find();String execution=success?"mock-passed":exitCode!=0||failed>0?"mock-failed":"unverified";boolean bound=fingerprint!=null&&fingerprint.matches("[a-f0-9]{64}");String status=bound?execution:"unverified";
  return new JSONObject().put("schema",1).put("kind","mock").put("evidence","simulation").put("scope","baseline").put("sourceVerified",false).put("requestedApi",requestedApi).put("api",status.equals("mock-passed")?requestedApi:JSONObject.NULL).put("status",status).put("executionStatus",execution).put("fingerprint",fingerprint==null?"":fingerprint).put("completedAt",completedAt).put("exitCode",exitCode).put("tests",tests).put("failed",failed).put("skipped",skipped).put("summary","생성된 기준 모의 테스트 시뮬레이션 결과입니다. 실제 OS·전체 앱 호환성 결과가 아닙니다.");
 }
 public static JSONArray matrix(JSONArray requestedApis,JSONArray records,String fingerprint)throws JSONException {
  JSONArray rows=new JSONArray();for(int i=0;i<requestedApis.length();i++){int api=requestedApis.getInt(i);JSONObject chosen=null;for(int j=0;j<records.length();j++){JSONObject r=records.optJSONObject(j);if(r==null||!r.optString("kind").equals("mock")||!r.optString("evidence").equals("simulation")||!r.optString("scope").equals("baseline")||r.optInt("requestedApi",r.optInt("api",-1))!=api)continue;if(chosen==null||r.optLong("completedAt")>=chosen.optLong("completedAt"))chosen=r;}String status="unverified";if(chosen!=null&&fingerprint!=null&&fingerprint.matches("[a-f0-9]{64}")){if(!fingerprint.equals(chosen.optString("fingerprint")))status="stale";else if(chosen.optString("status").equals("mock-failed"))status="mock-failed";else if(chosen.optString("status").equals("mock-passed")&&chosen.optInt("api",-1)==api&&chosen.optLong("tests",-1)>=2&&chosen.optLong("failed",-1)==0&&chosen.optLong("skipped",-1)==0&&chosen.optInt("exitCode",-1)==0&&chosen.optLong("completedAt",0)>0)status="mock-passed";}rows.put(new JSONObject().put("api",api).put("status",status).put("kind","mock").put("evidence","simulation").put("scope","baseline").put("sourceVerified",false));}return rows;
 }
}
