package dev.mobilecodex.app.core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Pinned private tool bundle installer, never a general archive importer. */
public final class ToolchainBundle {
    public record Limits(long archiveBytes,long expandedBytes,int entries) {
        public Limits {
            if(archiveBytes<1||archiveBytes>512L*1024*1024||expandedBytes<1||expandedBytes>1024L*1024*1024||entries<1||entries>20000)
                throw new IllegalArgumentException("도구 묶음 크기 상한이 올바르지 않습니다.");
        }
    }
    public interface Progress {void update(long expanded,int entries);}
    private ToolchainBundle() {}
    private static String digest(Path file)throws IOException {
        try {
            MessageDigest md=MessageDigest.getInstance("SHA-256");byte[] b=new byte[65536];
            try(InputStream in=Files.newInputStream(file)){int n;while((n=in.read(b))!=-1)md.update(b,0,n);}
            StringBuilder s=new StringBuilder();for(byte value:md.digest())s.append(String.format(Locale.ROOT,"%02x",value&255));return s.toString();
        }catch(java.security.NoSuchAlgorithmException impossible){throw new IOException(impossible);}
    }
    private static Path entry(Path stage,String name)throws IOException {
        if(name==null||name.length()>4096||name.startsWith("/")||name.indexOf('\\')>=0||name.chars().anyMatch(c->c<32||c==127))throw new IOException("허용되지 않은 ZIP 경로입니다.");
        String value=name.endsWith("/")?name.substring(0,name.length()-1):name;
        if(!(value.equals("profile.json")||value.equals("jdk")||value.equals("sdk")||value.equals("bin")||value.startsWith("jdk/")||value.startsWith("sdk/")||value.startsWith("bin/")))throw new IOException("허용되지 않은 도구 경로입니다.");
        for(String part:value.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("상위/빈 ZIP 경로는 사용할 수 없습니다.");
        Path path=stage.resolve(value).normalize();if(!path.startsWith(stage))throw new IOException("ZIP 경로가 루트를 벗어났습니다.");return path;
    }
    public static Path install(Path archive,Path rootfs,String expected,Limits limits,Progress progress)throws IOException {
        if(expected==null||!expected.matches("[0-9a-f]{64}")||Files.size(archive)>limits.archiveBytes()||!digest(archive).equals(expected))throw new IOException("검증된 도구 ZIP과 SHA-256이 일치하지 않습니다.");
        if(!Files.isDirectory(rootfs,LinkOption.NOFOLLOW_LINKS))throw new IOException("설치된 Linux 폴더가 필요합니다.");
        Path root=rootfs.toRealPath(),opt=root.resolve("opt");
        if(Files.exists(opt,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(opt,LinkOption.NOFOLLOW_LINKS))throw new IOException("Linux opt 경로가 일반 폴더가 아닙니다.");
        Files.createDirectories(opt);Path target=opt.resolve("android");
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("/opt/android가 이미 있습니다. 기존 설치는 변경하지 않았습니다.");
        Path stage=Files.createTempDirectory(opt,".android-import-");boolean moved=false;
        try {
            long expanded=0,lastNotice=0;int count=0;Set<Path> seen=new HashSet<>();byte[] b=new byte[65536];
            try(ZipInputStream zip=new ZipInputStream(Files.newInputStream(archive))) {
                ZipEntry e;
                while((e=zip.getNextEntry())!=null) {
                    if(++count>limits.entries())throw new IOException("ZIP 항목 수 상한을 초과했습니다.");
                    Path out=entry(stage,e.getName());if(!seen.add(out))throw new IOException("중복 ZIP 경로입니다.");
                    if(e.isDirectory())Files.createDirectories(out);
                    else {
                        Files.createDirectories(out.getParent());
                        try(OutputStream stream=Files.newOutputStream(out,StandardOpenOption.CREATE_NEW)) {
                            int n;while((n=zip.read(b))!=-1){expanded+=n;if(expanded>limits.expandedBytes())throw new IOException("ZIP 해제 크기 상한을 초과했습니다.");stream.write(b,0,n);}
                        }
                        if(!out.toFile().setExecutable(true,true))throw new IOException("도구 실행 비트를 설정하지 못했습니다.");
                    }
                    zip.closeEntry();
                    if(expanded-lastNotice>=16L*1024*1024){progress.update(expanded,count);lastNotice=expanded;}
                }
            }
            for(String required:List.of("profile.json","jdk/bin/java","jdk/bin/javac","sdk/platforms/android-35/android.jar","sdk/build-tools/35.0.0/lib/d8.jar","bin/aapt2"))
                if(!Files.isRegularFile(stage.resolve(required),LinkOption.NOFOLLOW_LINKS)||Files.size(stage.resolve(required))<1)throw new IOException("필수 도구 파일이 없습니다: "+required);
            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("설치 대상이 이미 생성됐습니다.");
            Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE);moved=true;return target;
        }finally{if(!moved)removeOwnStage(stage);}
    }
    private static void removeOwnStage(Path stage)throws IOException {
        Files.walkFileTree(stage,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs)throws IOException{Files.delete(file);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult postVisitDirectory(Path dir,IOException error)throws IOException{if(error!=null)throw error;Files.delete(dir);return FileVisitResult.CONTINUE;}
        });
    }
}
