package dev.mobilecodex.app.core;
import org.json.*;
import org.junit.Test;
import java.nio.file.*;
import static org.junit.Assert.*;
public class AndroidValidationStoreTest {
 private static void write(Path p,String text) throws Exception {Files.write(p,text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 @Test public void fileBoundariesCannotCollideWithEmbeddedNulBytes() throws Exception {
  Path one=Files.createTempDirectory("validation-one"),two=Files.createTempDirectory("validation-two");
  Files.write(one.resolve("a"),new byte[]{'X',0,'b',0,'Y'});
  Files.write(two.resolve("a"),new byte[]{'X'});Files.write(two.resolve("b"),new byte[]{'Y'});
  assertNotEquals(AndroidValidationStore.sourceFingerprint(one),AndroidValidationStore.sourceFingerprint(two));
 }
 @Test public void sourceChangesInvalidateEvidenceButBuildOutputsDoNot() throws Exception {
  Path root=Files.createTempDirectory("validation-source");write(root.resolve("build.gradle"),"plugins {}\n");
  String first=AndroidValidationStore.sourceFingerprint(root);
  Files.createDirectories(root.resolve("app/build/outputs"));write(root.resolve("app/build/outputs/generated.txt"),"generated");
  assertEquals(first,AndroidValidationStore.sourceFingerprint(root));
  Files.createDirectories(root.resolve("app/src"));write(root.resolve("app/src/Main.kt"),"fun main() {}\n");
  assertNotEquals(first,AndroidValidationStore.sourceFingerprint(root));
 }
 @Test public void refusesSourceLinksInsteadOfClaimingCoveredSources() throws Exception {
  Path root=Files.createTempDirectory("validation-link");Path outside=Files.createTempFile("outside",".kt");Files.createSymbolicLink(root.resolve("source.kt"),outside);
  assertThrows(java.io.IOException.class,()->AndroidValidationStore.sourceFingerprint(root));
 }
 @Test public void historiesAreProjectScopedAndCopyIsDefensive() throws Exception {
  AndroidValidationStore store=new AndroidValidationStore(Files.createTempDirectory("validation-store"));
  JSONObject r=new JSONObject().put("status","passed").put("kind","local").put("fingerprint","a".repeat(64));
  store.add("A",r);r.put("status","failed");
  assertEquals("passed",store.read("A").getJSONArray("records").getJSONObject(0).getString("status"));
  assertEquals(0,store.read("B").getJSONArray("records").length());
  store.read("A").getJSONArray("records").getJSONObject(0).put("status","failed");
  assertEquals("passed",store.read("A").getJSONArray("records").getJSONObject(0).getString("status"));
 }
 @Test public void interruptedRunsNeverReopenAsRunningOrPassed() throws Exception {
  Path dir=Files.createTempDirectory("validation-reopen");AndroidValidationStore one=new AndroidValidationStore(dir);
  one.add("A",new JSONObject().put("id","run").put("status","running").put("kind","local"));
  AndroidValidationStore two=new AndroidValidationStore(dir);
  assertEquals("interrupted",two.read("A").getJSONArray("records").getJSONObject(0).getString("status"));
 }
 @Test public void apiTargetsRejectInvalidValuesAndKeepPriorSelection() throws Exception {
  AndroidValidationStore store=new AndroidValidationStore(Files.createTempDirectory("validation-api"));
  store.setApis("A",new JSONArray("[29,35,29]"));assertEquals("[29,35]",store.read("A").getJSONArray("requestedApis").toString());
  assertThrows(Exception.class,()->store.setApis("A",new JSONArray("[0,35]")));
  assertThrows(Exception.class,()->store.setApis("A",new JSONArray("[\"35\"]")));
  assertEquals("[29,35]",store.read("A").getJSONArray("requestedApis").toString());
 }
 @Test public void corruptHistoryIsReportedWithoutInventingCoverage() throws Exception {
  Path dir=Files.createTempDirectory("validation-corrupt");AndroidValidationStore one=new AndroidValidationStore(dir);one.setApis("A",new JSONArray("[29]"));
  try(var files=Files.list(dir)){for(Path f:files.toList())write(f,"invalid");}
  JSONObject value=new AndroidValidationStore(dir).read("A");assertTrue(value.getBoolean("storageWarning"));assertEquals(0,value.getJSONArray("records").length());
 }
 @Test public void oversizedRecordAndSymlinkedStorageAreRejected() throws Exception {
  Path dir=Files.createTempDirectory("validation-bound");AndroidValidationStore store=new AndroidValidationStore(dir);
  assertThrows(Exception.class,()->store.add("A",new JSONObject().put("output","x".repeat(100000))));
  Path link=dir.resolve("link");Files.createSymbolicLink(link,Files.createTempDirectory("validation-out"));
  assertThrows(Exception.class,()->new AndroidValidationStore(link).setApis("A",new JSONArray("[29]")));
 }
}
