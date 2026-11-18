package dev.mobilecodex.app.core;
import java.nio.file.*;import java.util.*;import java.io.*;import javax.xml.parsers.*;
public class AndroidProjectHarness {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("assertion "+n);}
 interface Op{void run()throws Exception;}static void rejects(Op o)throws Exception{n++;try{o.run();throw new AssertionError("accepted");}catch(IOException|IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception{
  Path assets=Path.of(args[0]);SampleProject.Assets source=name->Files.newInputStream(assets.resolve(name));
  if(args.length==2){System.out.println(AndroidProject.create(Path.of(args[1]),new AndroidProject.Config("나의 앱 & \"친구\"", "com.example.tabletfixture", "한글 앱"),source));return;}
  Path parent=Files.createTempDirectory("android-projects-");
  try{
   var config=new AndroidProject.Config("나의 앱 & <친구> ' \"", "com.example.tabletfixture", "한글 앱");Path p=AndroidProject.create(parent,config,source);
   check(p.getFileName().toString().equals("한글 앱"));check(Files.isRegularFile(p.resolve("app/src/main/java/com/example/tabletfixture/MainActivity.java")));
   check(Files.readString(p.resolve("app/build.gradle")).contains("applicationId 'com.example.tabletfixture'"));check(Files.readString(p.resolve("app/build.gradle")).contains("versionName '1.0'"));
   check(!Files.exists(p.resolve("app/src/main/java/dev/mobilecodex/sample/MainActivity.java")));
   var xml=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(p.resolve("app/src/main/res/values/strings.xml").toFile());check(xml.getElementsByTagName("string").getLength()==2);
   check(Arrays.equals(Files.readAllBytes(p.resolve("gradle/wrapper/gradle-wrapper.jar")),Files.readAllBytes(assets.resolve("gradle/wrapper/gradle-wrapper.jar"))));
   byte[] before=Files.readAllBytes(p.resolve("app/build.gradle"));rejects(()->AndroidProject.create(parent,config,source));check(Arrays.equals(before,Files.readAllBytes(p.resolve("app/build.gradle"))));
   for(String pkg:List.of("one","com.class.app","com.true.app","com.Example.app","com.a-b","com..app","dev.mobilecodex.app","dev.mobilecodex.apk","dev.mobilecodex.sample","com.a;bad"))rejects(()->AndroidProject.create(parent,new AndroidProject.Config("앱",pkg,"unused"),source));
   for(String folder:List.of("../escape","a/b","a\\b",".","..",""," a.b ","a\n"))rejects(()->AndroidProject.create(parent,new AndroidProject.Config("앱","com.example.valid",folder),source));
   for(String name:List.of("","a\n","x".repeat(81)))rejects(()->AndroidProject.create(parent,new AndroidProject.Config(name,"com.example.valid","unused"),source));
   Path link=parent.resolve("linked");Files.createSymbolicLink(link,p);rejects(()->AndroidProject.create(parent,new AndroidProject.Config("앱","com.example.valid","linked"),source));
   rejects(()->AndroidProject.create(parent,new AndroidProject.Config("앱","com.example.valid","broken"),name->{if(name.equals("gradlew"))throw new IOException("missing");return source.open(name);}));check(!Files.exists(parent.resolve("broken")));
   try(var entries=Files.list(parent)){check(entries.noneMatch(x->x.getFileName().toString().startsWith(".creating-")));}
   System.out.println("AndroidProject: "+n+" assertions passed");
  }finally{try(var all=Files.walk(parent)){for(Path p:all.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
