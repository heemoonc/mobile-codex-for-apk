package dev.mobilecodex.app;
import android.app.Application;import android.content.Context;import java.io.*;import java.nio.file.*;import org.json.*;import org.junit.*;import org.junit.runner.RunWith;import org.robolectric.*;import org.robolectric.annotation.Config;
import static org.junit.Assert.*;import static dev.mobilecodex.app.core.Json.*;
@org.robolectric.annotation.ConscryptMode(org.robolectric.annotation.ConscryptMode.Mode.OFF)
@RunWith(RobolectricTestRunner.class) @Config(sdk=29,application=Application.class)
public class DocumentStoreInternalProjectTest {
 Context context;String folder="internal-test-"+java.util.UUID.randomUUID();
 @Before public void setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("projects",0).edit().clear().commit();context.getSharedPreferences("workspace",0).edit().clear().commit();}
 @After public void cleanup()throws Exception{Path p=new File(context.getFilesDir(),"android-projects/"+folder).toPath();if(Files.exists(p))try(var paths=Files.walk(p)){for(Path f:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(f);}}
 @Test public void internalProjectSharesFileAndCwdRootAndRestoresAfterReopen()throws Exception{
  DocumentStore store=new DocumentStore(context);var created=store.createAndroidProject("한글 앱","com.example.named",folder,name->context.getAssets().open("android-sample/"+name));String key=created.getString("key");
  File root=new File(context.getFilesDir(),"android-projects/"+folder);assertEquals(root.getCanonicalFile(),store.directDirectory());assertTrue(store.workspace().getBoolean("available"));
  String path="app/src/main/res/values/strings.xml";var read=store.read(path);assertTrue(read.getString("content").contains("한글 앱"));store.commit(store.prepare("mobile_write",obj("path",path,"content","changed","expectedSha256",read.getString("sha256"))));assertEquals("changed",store.read(path).getString("content"));assertTrue(store.search("MainActivity").getJSONArray("entries").length()>0);
  store.selectProject("");assertEquals(new File(context.getFilesDir(),"workspace").getCanonicalFile(),store.directDirectory());store.selectProject(key);assertEquals(root.getCanonicalFile(),store.directDirectory());assertEquals("changed",new DocumentStore(context).read(path).getString("content"));
  assertThrows(Exception.class,()->store.read("../escape"));assertThrows(Exception.class,()->store.createAndroidProject("다른 앱","com.example.other",folder,name->context.getAssets().open("android-sample/"+name)));assertEquals(key,store.key());
 }
 @Test public void sourceRestorePreservesSourceAndUsesNewInternalFolder()throws Exception{
  DocumentStore store=new DocumentStore(context);Path source=Files.createTempDirectory("restore-source"),zip=source.resolveSibling(source.getFileName()+".zip");
  try{Files.write(source.resolve("hello.txt"),"source".getBytes(java.nio.charset.StandardCharsets.UTF_8));dev.mobilecodex.app.core.SourceBackup.write(source,"원본",zip,dev.mobilecodex.app.core.SourceBackup.DEFAULT);
   store.restoreAndroidProject("복원 앱",folder,zip);assertEquals("source",store.read("hello.txt").getString("content"));assertEquals(new File(context.getFilesDir(),"android-projects/"+folder).getCanonicalFile(),store.directDirectory());String key=store.key();assertThrows(IOException.class,()->store.restoreAndroidProject("다시",folder,zip));assertEquals(key,store.key());assertTrue(Files.exists(source.resolve("hello.txt")));assertEquals("source",new DocumentStore(context).read("hello.txt").getString("content"));
  }finally{Files.deleteIfExists(zip);Files.deleteIfExists(source.resolve("hello.txt"));Files.deleteIfExists(source);}
 }
 @Test public void corruptLocalMapCannotBeOverwrittenByCreation()throws Exception{
  DocumentStore store=new DocumentStore(context);context.getSharedPreferences("projects",0).edit().putString("internal-folders-v1","broken").commit();assertThrows(IOException.class,()->store.createAndroidProject("앱","com.example.named",folder,name->context.getAssets().open("android-sample/"+name)));assertFalse(new File(context.getFilesDir(),"android-projects/"+folder).exists());assertEquals("broken",context.getSharedPreferences("projects",0).getString("internal-folders-v1",""));
 }
 @Test public void realPortableImportDoesNotCarryLocalFolderMapping()throws Exception{
  DocumentStore original=new DocumentStore(context);String key=original.createAndroidProject("앱","com.example.named",folder,name->context.getAssets().open("android-sample/"+name)).getString("key");String bundle=original.exportProject(key).toString();assertFalse(bundle.contains("mobilecodex-internal:"));
  context.getSharedPreferences("projects",0).edit().clear().commit();DocumentStore imported=new DocumentStore(context);JSONObject preview=imported.previewProjects(bundle);imported.importProjects(bundle,preview.getString("token"));String importedKey=imported.projects().getJSONObject(0).getString("key");imported.selectProject(importedKey);assertFalse(imported.workspace().getBoolean("available"));assertNull(imported.directDirectory());assertTrue(new File(context.getFilesDir(),"android-projects/"+folder+"/build.gradle").isFile());
  var forged=dev.mobilecodex.app.core.ProjectRegistry.fromJson(context.getSharedPreferences("projects",0).getString("registry",""));forged.put("mobilecodex-internal:"+java.util.UUID.randomUUID(),"forged",null);context.getSharedPreferences("projects",0).edit().putString("registry",forged.toJson()).commit();DocumentStore unknown=new DocumentStore(context);assertFalse(unknown.workspace().getBoolean("available"));assertThrows(Exception.class,()->unknown.list(""));
 }
 @Test public void failedRegistryCommitPreservesSelectionAndReportsOwnedSourceFolder()throws Exception{
  String previous=new DocumentStore(context).createProject("기존 프로젝트").getString("key");android.content.SharedPreferences real=context.getSharedPreferences("projects",0);String before=real.getString("registry","");
  var failing=(android.content.SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{android.content.SharedPreferences.class},(proxy,method,args)->{
   if(!method.getName().equals("edit"))return method.invoke(real,args);var editor=real.edit();return java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{android.content.SharedPreferences.Editor.class},(p,m,a)->{if(m.getName().equals("commit"))return false;if(m.getReturnType()==android.content.SharedPreferences.Editor.class)return p;return m.invoke(editor,a);});
  });
  Context wrapper=new android.content.ContextWrapper(context){@Override public android.content.SharedPreferences getSharedPreferences(String name,int mode){return name.equals("projects")?failing:super.getSharedPreferences(name,mode);}};
  DocumentStore store=new DocumentStore(wrapper);IOException error=assertThrows(IOException.class,()->store.createAndroidProject("앱","com.example.named",folder,name->context.getAssets().open("android-sample/"+name)));assertTrue(error.getMessage().contains(folder));assertEquals(previous,store.key());assertEquals(before,real.getString("registry",""));assertTrue(new File(context.getFilesDir(),"android-projects/"+folder+"/build.gradle").isFile());
 }
 @Test public void unknownImportedInternalIdCannotBindAndMissingFolderDoesNotFallback()throws Exception{
  DocumentStore store=new DocumentStore(context);var created=store.createAndroidProject("앱","com.example.named",folder,name->context.getAssets().open("android-sample/"+name));Path root=store.directDirectory().toPath();Path moved=root.resolveSibling(folder+"-moved");Files.move(root,moved);
  try{assertFalse(store.workspace().getBoolean("available"));assertNull(store.directDirectory());assertThrows(Exception.class,()->store.read("build.gradle"));}finally{Files.move(moved,root);}
  context.getSharedPreferences("projects",0).edit().remove("internal-folders-v1").commit();DocumentStore withoutMap=new DocumentStore(context);assertFalse(withoutMap.workspace().getBoolean("available"));assertNull(withoutMap.directDirectory());assertThrows(Exception.class,()->withoutMap.list(""));assertTrue(Files.exists(root.resolve("build.gradle")));
 }
 @Test public void cloneUsesIndependentNewFolderAndExcludesBuildCache()throws Exception {
  DocumentStore store=new DocumentStore(context);store.createAndroidProject("원본","com.example.clone",folder,name->context.getAssets().open("android-sample/"+name));String oldKey=store.key();Path original=store.directDirectory().toPath();String copyFolder=folder+"-copy";
  Files.createDirectories(original.resolve("app/build"));Files.write(original.resolve("app/build/ignored.apk"),new byte[]{1});
  try {
   store.cloneAndroidProject("복제",copyFolder);assertNotEquals(oldKey,store.key());Path copied=store.directDirectory().toPath();assertNotEquals(original,copied);assertFalse(Files.exists(copied.resolve("app/build/ignored.apk")));assertTrue(store.read("app/build.gradle").getString("content").contains("com.example.clone"));
   Files.write(copied.resolve("app/src/main/res/values/strings.xml"),"changed".getBytes(java.nio.charset.StandardCharsets.UTF_8));assertFalse(new String(Files.readAllBytes(original.resolve("app/src/main/res/values/strings.xml")),java.nio.charset.StandardCharsets.UTF_8).equals("changed"));
   String current=store.key();assertThrows(IOException.class,()->store.cloneAndroidProject("충돌",copyFolder));assertEquals(current,store.key());assertEquals("changed",store.read("app/src/main/res/values/strings.xml").getString("content"));
  }finally{Path copy=new File(context.getFilesDir(),"android-projects/"+copyFolder).toPath();if(Files.exists(copy))try(var paths=Files.walk(copy)){for(Path f:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(f);}}
 }
 @Test public void kotlinProfileCreatesAndReopensWithoutJavaSource()throws Exception{profileCreates("kotlin");}
 @Test public void composeProfileCreatesAndReopensWithoutJavaSource()throws Exception{profileCreates("compose");}
 private void profileCreates(String template)throws Exception {
  DocumentStore store=new DocumentStore(context);store.createAndroidProject("새 앱","com.example.profile",folder,template,name->context.getAssets().open("android-sample/"+name),name->context.getAssets().open("android-templates/"+template+"/"+name));
  assertTrue(store.read("app/src/main/java/com/example/profile/MainActivity.kt").getString("content").contains("package com.example.profile"));assertFalse(new File(store.directDirectory(),"app/src/main/java/com/example/profile/MainActivity.java").exists());assertEquals(store.key(),new DocumentStore(context).key());
 }
 @Test public void githubCloneRegistersOnlyCompleteInternalProject()throws Exception {
  DocumentStore store=new DocumentStore(context);String key=store.cloneGitProject("Imported",folder,target->{Files.createDirectories(target.resolve(".git"));write(target.resolve("README.md"),"source");}).getString("key");
  assertEquals(key,store.key());assertEquals("source",store.read("README.md").getString("content"));assertEquals(key,new DocumentStore(context).key());
  assertThrows(IOException.class,()->store.cloneGitProject("Existing",folder,target->{fail("Existing target must not be overwritten");}));
 }
 @Test public void githubCloneFailureCleansStagingWithoutChangingSelection()throws Exception {
  DocumentStore store=new DocumentStore(context);String key=store.createProject("Existing").getString("key");
  assertThrows(IOException.class,()->store.cloneGitProject("Failed",folder,target->{Files.createDirectories(target);write(target.resolve("partial"),"partial");throw new IOException("network failed");}));
  assertEquals(key,store.key());Path parent=new File(context.getFilesDir(),"android-projects").toPath();assertFalse(Files.exists(parent.resolve(folder)));try(var paths=Files.list(parent)){assertFalse(paths.anyMatch(p->p.getFileName().toString().startsWith(".cloning-")));}
 }
 @Test public void githubCloneRejectsLinksWithoutRegisteringProject()throws Exception {
  DocumentStore store=new DocumentStore(context);String key=store.createProject("Existing").getString("key");
  assertThrows(IOException.class,()->store.cloneGitProject("Links",folder,target->{Files.createDirectories(target);Files.createSymbolicLink(target.resolve("link"),Paths.get("/etc/passwd"));}));assertEquals(key,store.key());
 }
 private static void write(Path path,String text)throws IOException {Files.write(path,text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
