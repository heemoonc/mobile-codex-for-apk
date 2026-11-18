package dev.mobilecodex.app;

import android.app.Application;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.nio.file.*;
import java.util.concurrent.*;
import static dev.mobilecodex.app.core.Json.obj;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@Config(sdk=29, application=Application.class)
public class EngineAndroidAssistantTest {
 private static void write(Path p,String text) throws Exception {Files.write(p,text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private Engine engine;
 private String key;
 @Before public void setup() throws Exception {
  engine=new Engine(RuntimeEnvironment.getApplication());
  key=request("state",obj()).getJSONObject("workspace").getString("key");
  Path project=Paths.get(request("state",obj()).getString("cwd"));
  Files.createDirectories(project.resolve("app"));
  write(project.resolve("app/build.gradle"),"plugins { id 'com.android.application' }\nandroid { compileSdk 35; defaultConfig { minSdk 29; targetSdk 35 } }\ndependencies {}\n");
 }
 @After public void cleanup(){if(engine!=null)engine.io.shutdownNow();}
 private JSONObject request(String action,JSONObject args) throws Exception {
  CompletableFuture<JSONObject> result=new CompletableFuture<>();
  engine.handle(action,args,(value,error)->{if(error!=null)result.completeExceptionally(error);else result.complete(value);});
  return result.get(20,TimeUnit.SECONDS);
 }
 @Test public void setupInspectionNeedsNoProjectOrLoginAndCannotBorrowAnotherTerminal() throws Exception {
  JSONObject setup=request("android.setup.status",obj());assertTrue(setup.has("linux"));assertFalse(setup.getBoolean("executionVerified"));
  assertFalse(request("android.setup.result",obj("id","other")).getBoolean("verified"));
  assertThrows(ExecutionException.class,()->request("android.setup.check",obj()));
  request("permissions.set",obj("mode","read-only"));
  assertTrue(request("android.setup.status",obj()).has("tools"));
  assertThrows(ExecutionException.class,()->request("android.setup.linux",obj()));
  assertFalse(request("state",obj()).getJSONObject("terminal").optBoolean("running"));
 }
 @Test public void publicToolDownloadNeedsConsentAndCannotBorrowTerminalEvidence() throws Exception {
  JSONObject info=request("android.setup.download.info",obj());
  assertTrue(info.getString("licenseText").contains("Software Development Kit License Agreement"));
  assertEquals(4,info.getJSONArray("packages").length());
  assertEquals(64,info.getString("licenseSha256").length());
  assertThrows(ExecutionException.class,()->request("android.setup.download",obj("profileId",info.getString("profileId"),"acceptedLicense","")));
  assertFalse(request("android.setup.download.result",obj("id","other")).getBoolean("filesInstalled"));
  request("permissions.set",obj("mode","read-only"));
  assertThrows(ExecutionException.class,()->request("android.setup.download",obj("profileId",info.getString("profileId"),"acceptedLicense",info.getString("licenseSha256"))));
  assertFalse(request("state",obj()).getJSONObject("terminal").optBoolean("running"));
 }
 @Test public void inspectDoesNotRunCommandsAndReturnsScopedUnverifiedMatrix() throws Exception {
  JSONObject value=request("android.inspect",obj("workspaceKey",key));
  assertEquals(key,value.getString("workspaceKey"));assertTrue(value.has("sdk"));
  assertEquals(0,value.getJSONObject("validation").getJSONArray("records").length());
  assertFalse(request("state",obj()).getJSONObject("terminal").optBoolean("running"));
 }
 @Test public void workflowInspectionIncludesConfigurationAndPreparationWithoutExecution() throws Exception {
  JSONObject value=request("android.inspect",obj("workspaceKey",key));
  assertTrue(value.getJSONObject("workflow").getString("configuration").matches("[a-f0-9]{64}"));
  assertFalse(value.getJSONObject("simulation").getBoolean("prepared"));
  for(String kind:new String[]{"workflow-environment","workflow-checks"})
   assertThrows(ExecutionException.class,()->request("android.verify",obj("workspaceKey",key,"kind",kind)));
  assertEquals(0,request("android.inspect",obj("workspaceKey",key)).getJSONObject("validation").getJSONArray("records").length());
 }
 @Test public void staleWorkspaceCannotReadOrEditProjectConfiguration() throws Exception {
  ExecutionException e=assertThrows(ExecutionException.class,()->request("android.inspect",obj("workspaceKey","not-current")));
  assertTrue(e.getCause().getMessage().contains("프로젝트"));
 }
 @Test public void readOnlyAllowsInspectionButRefusesApplyAndExecution() throws Exception {
  request("permissions.set",obj("mode","read-only"));
  assertTrue(request("android.inspect",obj("workspaceKey",key)).has("sdk"));
  assertTrue(request("android.preview",obj("workspaceKey",key,"request",obj("libraryId","okhttp"))).getBoolean("canApply"));
  assertThrows(ExecutionException.class,()->request("android.apply",obj("workspaceKey",key,"request",obj("libraryId","okhttp"),"fingerprint","a".repeat(64))));
  assertThrows(ExecutionException.class,()->request("android.verify",obj("workspaceKey",key,"kind","local","options",obj())));
 }
 @Test public void linuxUnavailableNeverStartsValidationAndLeavesNoPassRecord() throws Exception {
  assertThrows(ExecutionException.class,()->request("android.verify",obj("workspaceKey",key,"kind","local","options",obj())));
  JSONObject value=request("android.inspect",obj("workspaceKey",key));
  assertEquals(0,value.getJSONObject("validation").getJSONArray("records").length());
 }
 @Test public void saveMatrixValidatesApisAndPlanDoesNotExecute() throws Exception {
  JSONObject value=request("android.matrix.save",obj("workspaceKey",key,"apis",new JSONArray("[29,35]")));
  assertEquals("[29,35]",value.getJSONObject("validation").getJSONArray("requestedApis").toString());
  JSONObject plan=request("android.plan",obj("workspaceKey",key,"apis",new JSONArray("[29,35]"),"options",obj()));
  assertEquals(1,plan.getInt("schema"));assertFalse(request("state",obj()).getJSONObject("terminal").optBoolean("running"));
 }
 @Test public void simulationPreviewIsScopedAndDoesNotWriteOrExecute() throws Exception {
  JSONObject plan=request("android.simulation.preview",obj("workspaceKey",key));
  assertTrue(plan.has("changes"));assertTrue(plan.has("fingerprint"));
  assertFalse(request("state",obj()).getJSONObject("terminal").optBoolean("running"));
  JSONObject snapshot=request("android.inspect",obj("workspaceKey",key));
  assertTrue(snapshot.has("simulation"));assertTrue(snapshot.getJSONObject("validation").has("mockMatrix"));
 }
 @Test public void readOnlySimulationPreviewAllowedButPreparationRefused() throws Exception {
  request("permissions.set",obj("mode","read-only"));
  assertTrue(request("android.simulation.preview",obj("workspaceKey",key)).has("fingerprint"));
  assertThrows(ExecutionException.class,()->request("android.simulation.apply",obj("workspaceKey",key,"fingerprint","a".repeat(64))));
 }
 @Test public void wrongScopeCannotPrepareSimulation() throws Exception {
  assertThrows(ExecutionException.class,()->request("android.simulation.preview",obj("workspaceKey","wrong")));
 }
 @Test public void appPrivateStorageAliasDoesNotBlockSimulationBackup() throws Exception {
  engine.io.shutdownNow();
  android.content.Context base=RuntimeEnvironment.getApplication();
  Path links=Files.createTempDirectory("android-storage-alias");
  Path alias=links.resolve("files");Files.createSymbolicLink(alias,base.getFilesDir().toPath());
  try {
   engine=new Engine(new android.content.ContextWrapper(base){@Override public java.io.File getFilesDir(){return alias.toFile();}});
   JSONObject state=request("state",obj());key=state.getJSONObject("workspace").getString("key");
   Path project=Paths.get(state.getString("cwd"));Files.createDirectories(project.resolve("app"));
   write(project.resolve("settings.gradle"),"include ':app'\n");
   write(project.resolve("app/build.gradle"),"plugins { id 'com.android.application' }\nandroid { compileSdk 35; defaultConfig { minSdk 29; targetSdk 35 } }\ndependencies {}\n");
   JSONObject plan=request("android.simulation.preview",obj("workspaceKey",key));assertTrue(plan.toString(),plan.getBoolean("canApply"));
   JSONObject result=request("android.simulation.apply",obj("workspaceKey",key,"fingerprint",plan.getString("fingerprint")));
   assertTrue(result.getJSONObject("simulationApplied").getBoolean("applied"));
   assertTrue(Files.exists(project.resolve("app/src/test/java/dev/mobilecodex/checks/ApiSimulationTest.java")));
  } finally {Files.deleteIfExists(alias);Files.deleteIfExists(links);}
 }
}
