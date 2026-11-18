package dev.mobilecodex.app.core;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
public final class ToolchainBundleHarness {
 static int assertions;
 interface Action {void run()throws Exception;}
 static void check(boolean x){assertions++;if(!x)throw new AssertionError("assertion "+assertions);}
 static void reject(Action x)throws Exception{assertions++;try{x.run();throw new AssertionError("unexpected acceptance");}catch(java.io.IOException|IllegalArgumentException expected){}}
 static String hash(Path p)throws Exception{var md=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}return HexFormat.of().formatHex(md.digest());}
 static Map<String,String> valid(){var map=new LinkedHashMap<String,String>();for(String p:List.of("profile.json","jdk/bin/java","jdk/bin/javac","sdk/platforms/android-35/android.jar","sdk/build-tools/35.0.0/lib/d8.jar","bin/aapt2"))map.put(p,"test");return map;}
 static Path zip(Path base,String name,Map<String,String> entries)throws Exception{Path p=base.resolve(name);try(var z=new ZipOutputStream(Files.newOutputStream(p))){for(var e:entries.entrySet()){z.putNextEntry(new ZipEntry(e.getKey()));z.write(e.getValue().getBytes(StandardCharsets.UTF_8));z.closeEntry();}}return p;}
 static ToolchainBundle.Limits limits(Path p)throws Exception{return new ToolchainBundle.Limits(Files.size(p),100000,100);}
 static void clean(Path root)throws Exception{try(var s=Files.walk(root)){for(Path p:s.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 public static void main(String[] args)throws Exception{
  if(args.length==4&&args[0].equals("--install-real")){Path zip=Path.of(args[1]),root=Path.of(args[2]);Files.createDirectories(root);System.out.println(ToolchainBundle.install(zip,root,args[3],new ToolchainBundle.Limits(512L*1024*1024,1024L*1024*1024,20000),(b,n)->{}));return;}
  Path base=Files.createTempDirectory("toolchain-bundle-test-");
  try{
   Path archive=zip(base,"good.zip",valid()),root=Files.createDirectory(base.resolve("root"));
   reject(()->ToolchainBundle.install(archive,root,"0".repeat(64),limits(archive),(b,n)->{}));check(!Files.exists(root.resolve("opt/android")));
   Path installed=ToolchainBundle.install(archive,root,hash(archive),limits(archive),(b,n)->{});check(Files.readString(installed.resolve("jdk/bin/java")).equals("test"));check(Files.isExecutable(installed.resolve("jdk/bin/java")));
   reject(()->ToolchainBundle.install(archive,root,hash(archive),limits(archive),(b,n)->{}));check(Files.readString(installed.resolve("jdk/bin/java")).equals("test"));
   int seq=0;
   for(String bad:List.of("../escape","/absolute","jdk/../../escape","jdk\\escape","jdk//escape")){
    var entries=valid();entries.put(bad,"bad");Path z=zip(base,"bad"+(seq++)+".zip",entries),r=Files.createDirectory(base.resolve("r"+seq));
    reject(()->ToolchainBundle.install(z,r,hash(z),limits(z),(b,n)->{}));check(!Files.exists(r.resolve("opt/android")));
    if(Files.exists(r.resolve("opt")))try(var s=Files.list(r.resolve("opt"))){check(s.findAny().isEmpty());}
   }
   Path missing=zip(base,"missing.zip",Map.of("profile.json","test")),r=Files.createDirectory(base.resolve("missing-root"));reject(()->ToolchainBundle.install(missing,r,hash(missing),limits(missing),(b,n)->{}));check(!Files.exists(r.resolve("opt/android")));
   Path overflow=Files.createDirectory(base.resolve("overflow"));reject(()->ToolchainBundle.install(archive,overflow,hash(archive),new ToolchainBundle.Limits(Files.size(archive),1,100),(b,n)->{}));check(!Files.exists(overflow.resolve("opt/android")));
   Path count=Files.createDirectory(base.resolve("count"));reject(()->ToolchainBundle.install(archive,count,hash(archive),new ToolchainBundle.Limits(Files.size(archive),100000,1),(b,n)->{}));
   Path parent=Files.createDirectory(base.resolve("parent")),outside=Files.createDirectory(base.resolve("outside"));Files.createSymbolicLink(parent.resolve("opt"),outside);reject(()->ToolchainBundle.install(archive,parent,hash(archive),limits(archive),(b,n)->{}));check(!Files.exists(outside.resolve("android")));
   Path dup=zip(base,"dup.zip",Map.of("jdk/same1","x","jdk/same2","y"));byte[] raw=Files.readAllBytes(dup),needle="same2".getBytes(StandardCharsets.US_ASCII);for(int i=0;i<=raw.length-needle.length;i++){boolean match=true;for(int j=0;j<needle.length;j++)match&=raw[i+j]==needle[j];if(match)raw[i+4]='1';}Files.write(dup,raw);Path dr=Files.createDirectory(base.resolve("duplicate"));reject(()->ToolchainBundle.install(dup,dr,hash(dup),limits(dup),(b,n)->{}));
   System.out.println("ToolchainBundle: "+assertions+" assertions passed");
  }finally{clean(base);}
 }
}
