package dev.mobilecodex.app.core;
import java.nio.file.*;import java.io.*;import java.util.*;
public class AndroidTemplatesHarness {
 static int n;static void check(boolean b){n++;if(!b)throw new AssertionError("assertion "+n);}
 interface Op{void run()throws Exception;}static void rejects(Op op)throws Exception{n++;try{op.run();throw new AssertionError("accepted");}catch(IOException|IllegalArgumentException expected){}}
 public static void main(String[] args)throws Exception {
  Path base=Path.of(args[0]),profiles=Path.of(args[1]);SampleProject.Assets source=name->Files.newInputStream(base.resolve(name));
  Path parent=args.length==3?Path.of(args[2]):Files.createTempDirectory("android-templates-");Files.createDirectories(parent);
  try {
   for(String template:List.of("kotlin","compose")) {
    var c=new AndroidProject.Config("앱 & <친구>","com.example."+template,template);
    SampleProject.Assets assets=name->Files.newInputStream(profiles.resolve(template).resolve(name));
    Path root=AndroidProject.create(parent,c,source,template,assets);
    check(Files.exists(root.resolve("app/src/main/java/com/example/"+template+"/MainActivity.kt")));check(!Files.exists(root.resolve("app/src/main/java/com/example/"+template+"/MainActivity.java")));
    check(Files.readString(root.resolve("app/src/main/java/com/example/"+template+"/MainActivity.kt")).contains("package com.example."+template));
    check(Files.readString(root.resolve("app/build.gradle")).contains("applicationId 'com.example."+template+"'"));check(Files.readString(root.resolve("app/build.gradle")).contains("versionName '1.0'"));
    check(Files.readString(root.resolve("build.gradle")).contains("2.2.0"));check(Files.readString(root.resolve("gradle.properties")).contains("kotlin.compiler.execution.strategy=in-process"));
    check(Files.readString(root.resolve("app/src/main/res/values/strings.xml")).contains("&amp;"));
    check(Files.readString(root.resolve("app/build.gradle")).contains("compose true")==template.equals("compose"));
    check(Arrays.equals(Files.readAllBytes(base.resolve("gradle/wrapper/gradle-wrapper.jar")),Files.readAllBytes(root.resolve("gradle/wrapper/gradle-wrapper.jar"))));
    rejects(()->AndroidProject.create(parent,c,source,template,assets));
   }
   rejects(()->AndroidProject.create(parent,new AndroidProject.Config("앱","com.example.valid","invalid"),source,"../bad",name->{throw new AssertionError("untrusted asset path");}));
   rejects(()->AndroidProject.create(parent,new AndroidProject.Config("앱","com.when.app","keyword"),source,"kotlin",name->{throw new AssertionError("invalid package");}));
   rejects(()->AndroidProject.create(parent,new AndroidProject.Config("앱","com.example.valid","missing"),source,"kotlin",name->{throw new IOException("missing");}));check(!Files.exists(parent.resolve("missing")));
   rejects(()->AndroidProject.create(parent,new AndroidProject.Config("앱","com.example.valid","large"),source,"kotlin",name->new ByteArrayInputStream(new byte[65537])));check(!Files.exists(parent.resolve("large")));
   try(var entries=Files.list(parent)){check(entries.noneMatch(p->p.getFileName().toString().startsWith(".creating-")));}
   System.out.println("AndroidTemplates: "+n+" assertions passed");
  }finally{if(args.length!=3)try(var paths=Files.walk(parent)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
 }
}
