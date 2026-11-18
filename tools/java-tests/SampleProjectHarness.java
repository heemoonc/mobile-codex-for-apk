package dev.mobilecodex.app.core;
import java.nio.file.*;
import java.io.*;
import java.util.*;
public final class SampleProjectHarness {
 static int count;static void check(boolean x){count++;if(!x)throw new AssertionError("assertion "+count);}
 interface Action{void run()throws Exception;}
 static void reject(Action x)throws Exception{count++;try{x.run();throw new AssertionError("unexpected acceptance");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
  if(args.length==3&&args[0].equals("--create-real")){Path assets=Path.of(args[1]),root=Path.of(args[2]);Files.createDirectories(root);System.out.println("Created "+SampleProject.create(root,name->Files.newInputStream(assets.resolve(name)))+" files");return;}
  Path base=Files.createTempDirectory("sample-project-test-");
  try{
   Path root=Files.createDirectory(base.resolve("root"));check(SampleProject.canCreate(root));
   check(SampleProject.create(root,name->new ByteArrayInputStream(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)))==SampleProject.FILES.size());
   check(Files.isRegularFile(root.resolve("gradle/wrapper/gradle-wrapper.jar")));check(!SampleProject.canCreate(root));
   reject(()->SampleProject.create(root,name->new ByteArrayInputStream(new byte[]{1})));check(Files.readString(root.resolve("settings.gradle")).equals("settings.gradle"));
   Path hidden=Files.createDirectory(base.resolve("hidden"));Files.writeString(hidden.resolve(".keep"),"existing");check(!SampleProject.canCreate(hidden));reject(()->SampleProject.create(hidden,name->new ByteArrayInputStream(new byte[]{1})));check(Files.readString(hidden.resolve(".keep")).equals("existing"));
   Path link=base.resolve("link");Files.createSymbolicLink(link,hidden);check(!SampleProject.canCreate(link));reject(()->SampleProject.create(link,name->new ByteArrayInputStream(new byte[]{1})));
   Path absent=Files.createDirectory(base.resolve("absent"));reject(()->SampleProject.create(absent,name->{throw new IOException("missing asset");}));check(SampleProject.canCreate(absent));
   Path huge=Files.createDirectory(base.resolve("huge"));reject(()->SampleProject.create(huge,name->new ByteArrayInputStream(new byte[1024*1024+1])));check(SampleProject.canCreate(huge));
   Path total=Files.createDirectory(base.resolve("total"));reject(()->SampleProject.create(total,name->new ByteArrayInputStream(new byte[1024*1024])));check(SampleProject.canCreate(total));
   Path race=Files.createDirectory(base.resolve("race"));reject(()->SampleProject.create(race,name->{Files.writeString(race.resolve("settings.gradle"),"user-file");return new ByteArrayInputStream(new byte[]{1});}));check(Files.readString(race.resolve("settings.gradle")).equals("user-file"));
   System.out.println("SampleProject: "+count+" assertions passed");
  }finally{try(var paths=Files.walk(base)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
