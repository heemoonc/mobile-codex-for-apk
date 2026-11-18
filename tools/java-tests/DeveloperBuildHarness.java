package dev.mobilecodex.app.core;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class DeveloperBuildHarness {
    static int assertions;
    static void check(boolean ok){assertions++;if(!ok)throw new AssertionError("assertion "+assertions);}
    interface Operation{void run()throws Exception;}
    static void rejects(Operation op)throws Exception{assertions++;try{op.run();throw new AssertionError("accepted invalid input");}catch(java.io.IOException|IllegalArgumentException expected){}}
    public static void main(String[] args)throws Exception{
        if(args.length==1&&(args[0].equals("--emit-check")||args[0].equals("--emit-build"))) {
            System.out.print(DeveloperBuild.command(new DeveloperBuild.Config("/workspace/tabletforge-p0/toolchains/jdk-17.0.21+9","/workspace/tabletforge-p0/toolchains/android-sdk","/workspace/tabletforge-p0/toolchains/bin/aapt2","assembleDebug"),args[0].equals("--emit-build")));return;
        }
        String tricky="/tmp/a b/'quoted'/$HOME/`id`";
        Process p=new ProcessBuilder("/bin/sh","-c","printf '%s' "+DeveloperBuild.quote(tricky)).start();
        check(new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8).equals(tricky));check(p.waitFor()==0);
        var config=new DeveloperBuild.Config(tricky,"/sdk","/aapt2",":app:assembleDebug");
        check(DeveloperBuild.command(config,true).contains("':app:assembleDebug'"));
        check(!DeveloperBuild.command(config,false).contains("./gradlew"));
        for(String task:List.of("--help","a b","a;id","$(id)","",":","a\nb"))rejects(()->DeveloperBuild.command(new DeveloperBuild.Config("/jdk","/sdk","/aapt2",task),true));
        rejects(()->DeveloperBuild.command(new DeveloperBuild.Config("jdk","/sdk","/aapt2","assembleDebug"),true));
        rejects(()->DeveloperBuild.command(new DeveloperBuild.Config("/jdk\n","/sdk","/aapt2","assembleDebug"),true));
        Path root=Files.createTempDirectory("developer-build-"),cache=Files.createTempDirectory("developer-cache-"),outside=Files.createTempFile("outside-",".apk");
        try{
            Path apk=root.resolve("app/build/outputs/apk/debug/test.apk");Files.createDirectories(apk.getParent());Files.write(apk,new byte[]{1,2,3});
            var detail=DeveloperBuild.describe(root,"app/build/outputs/apk/debug/test.apk");check(detail.size()==3);check(detail.modified()==Files.getLastModifiedTime(apk).toMillis());check(detail.path().equals("app/build/outputs/apk/debug/test.apk"));
            Files.createSymbolicLink(root.resolve("escape.apk"),outside);
            Files.createSymbolicLink(root.resolve("linked"),apk.getParent());
            var listing=DeveloperBuild.list(root);check(listing.entries().size()==1);check(!listing.truncated());check(listing.entries().get(0).path().equals("app/build/outputs/apk/debug/test.apk"));
            Path copy=DeveloperBuild.copyApk(root,"app/build/outputs/apk/debug/test.apk",cache,512);check(Arrays.equals(Files.readAllBytes(copy),new byte[]{1,2,3}));check(!copy.equals(apk));
            for(String path:List.of("../outside.apk",outside.toString(),"escape.apk","linked/test.apk","app/../app/build/outputs/apk/debug/test.apk","missing.apk","app"))rejects(()->DeveloperBuild.copyApk(root,path,cache,512));
            for(String path:List.of("../outside.apk",outside.toString(),"escape.apk","linked/test.apk","missing.apk"))rejects(()->DeveloperBuild.describe(root,path));
            rejects(()->DeveloperBuild.copyApk(root,"app/build/outputs/apk/debug/test.apk",cache,2));check(Files.readAllBytes(apk).length==3);
            // Typical Gradle caches/intermediates must not consume APK discovery limits.
            Path gradle=root.resolve(".gradle/cache"),intermediate=root.resolve("app/build/intermediates/deep/a/b/c/d/e/f/g");
            Files.createDirectories(gradle);Files.createDirectories(intermediate);
            for(int i=0;i<5500;i++)Files.write(gradle.resolve("cache-"+i),new byte[]{1});
            Files.write(intermediate.resolve("internal.apk"),new byte[]{9});
            for(String ignored:List.of(".git",".kotlin","node_modules","app/build/tmp","app/build/generated")) {
                Path directory=root.resolve(ignored);Files.createDirectories(directory);Files.write(directory.resolve("ignored.apk"),new byte[]{9});
            }
            var cached=DeveloperBuild.list(root);check(!cached.truncated());check(cached.entries().size()==1);check(cached.entries().get(0).path().equals("app/build/outputs/apk/debug/test.apk"));
            Path many=root.resolve("many");Files.createDirectory(many);for(int i=0;i<101;i++)Files.write(many.resolve(i+".apk"),new byte[]{1});
            var limited=DeveloperBuild.list(root);check(limited.truncated());check(limited.entries().size()<=100);
            check(limited.entries().stream().anyMatch(entry->entry.path().equals("app/build/outputs/apk/debug/test.apk")));
            Path budget=root.resolve("budget");Files.createDirectory(budget);
            for(int i=0;i<5100;i++)Files.write(budget.resolve("file-"+i),new byte[]{1});
            check(DeveloperBuild.list(budget).truncated());
            System.out.println("DeveloperBuild: "+assertions+" assertions passed");
        }finally{
            for(Path dir:List.of(root,cache)){try(var paths=Files.walk(dir)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}Files.deleteIfExists(outside);
        }
    }
}
