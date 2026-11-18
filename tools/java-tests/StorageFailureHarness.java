package dev.mobilecodex.app.core;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Run only in a new fixture, under an OS file-size limit with SIGXFSZ ignored. */
public final class StorageFailureHarness {
    static int assertions;
    static void check(boolean ok){assertions++;if(!ok)throw new AssertionError("assertion "+assertions);}
    static String hash(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]),source=root.resolve("project/app-debug.apk"),cache=root.resolve("cache");
        String original=hash(source);Files.createDirectory(cache);
        try {DeveloperBuild.copyApk(source.getParent(),"app-debug.apk",cache,10000);throw new AssertionError("Expected real write failure");}
        catch(IOException error){check(String.valueOf(error.getMessage()).contains("File too large"));System.out.println("APK_COPY_IO_FAILURE: "+error.getClass().getSimpleName()+": "+error.getMessage());}
        check(hash(source).equals(original));try(var files=Files.list(cache)){check(files.findAny().isEmpty());}
        Path linux=Files.createDirectory(root.resolve("rootfs"));
        try {ToolchainBundle.install(root.resolve("bundle.zip"),linux,args[1],new ToolchainBundle.Limits(100000,100000,100),(n,c)->{});throw new AssertionError("Expected extraction write failure");}
        catch(IOException error){check(String.valueOf(error.getMessage()).contains("File too large"));System.out.println("TOOL_IMPORT_IO_FAILURE: "+error.getClass().getSimpleName()+": "+error.getMessage());}
        check(!Files.exists(linux.resolve("opt/android")));try(var files=Files.list(linux.resolve("opt"))){check(files.findAny().isEmpty());}
        Path sample=Files.createDirectory(root.resolve("sample"));
        try {SampleProject.create(sample,name->new ByteArrayInputStream(new byte[5000]));throw new AssertionError("Expected sample write failure");}
        catch(IOException error){check(error.getMessage().contains("일부 새 파일"));check(error.getCause()!=null);System.out.println("SAMPLE_PARTIAL_WRITE_REPORTED");}
        check(!SampleProject.canCreate(sample));Path partial=sample.resolve("settings.gradle");check(Files.size(partial)>0&&Files.size(partial)<5000);String partialHash=hash(partial);
        try {SampleProject.create(sample,name->new ByteArrayInputStream(new byte[5000]));throw new AssertionError("Partial folder must not be overwritten");}
        catch(IOException expected){}
        check(hash(partial).equals(partialHash));check(hash(source).equals(original));
        System.out.println("StorageFailure: "+assertions+" assertions passed");
    }
}
