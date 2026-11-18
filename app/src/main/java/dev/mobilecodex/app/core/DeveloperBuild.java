package dev.mobilecodex.app.core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/** Trusted-project developer helpers; this is not an arbitrary-code sandbox. */
public final class DeveloperBuild {
    public static final long MAX_APK_BYTES=512L*1024*1024;
    public record Config(String javaHome,String sdkRoot,String aapt2,String task) {}
    public record Apk(String path,long size,long modified) {}
    public record Listing(List<Apk> entries,boolean truncated) {}
    private DeveloperBuild() {}
    public static String quote(String value) {return "'"+value.replace("'","'\"'\"'")+"'";}
    private static void path(String value) {
        if(value==null||!value.startsWith("/")||value.length()>4096||value.chars().anyMatch(c->c<32||c==127))
            throw new IllegalArgumentException("Linux 도구 경로는 줄바꿈 없는 절대 경로로 입력해 주세요.");
    }
    public static String command(Config config,boolean build) {
        path(config.javaHome());path(config.sdkRoot());path(config.aapt2());
        if(config.task()==null||!config.task().matches(":?[A-Za-z_][A-Za-z0-9_.-]*(?::[A-Za-z_][A-Za-z0-9_.-]*)*")||config.task().length()>200)
            throw new IllegalArgumentException("Gradle 작업 이름 하나를 입력해 주세요. 예: :app:assembleDebug");
        String check="set -eu\nexport JAVA_HOME="+quote(config.javaHome())+"\nexport ANDROID_HOME="+quote(config.sdkRoot())
            +"\nexport ANDROID_SDK_ROOT=\"$ANDROID_HOME\"\nexport PATH=\"$JAVA_HOME/bin:$PATH\"\nAAPT2="+quote(config.aapt2())
            +"\n[ -x \"$JAVA_HOME/bin/java\" ] && [ -x \"$JAVA_HOME/bin/javac\" ] || { echo 'JDK 경로에 java/javac가 없습니다.'; exit 2; }"
            +"\n[ -x \"$AAPT2\" ] || { echo 'ARM64 aapt2 실행 파일이 없습니다.'; exit 2; }"
            +"\nplatform_found=0\nfor jar in \"$ANDROID_HOME\"/platforms/android-*/android.jar; do [ ! -f \"$jar\" ] || platform_found=1; done"
            +"\n[ \"$platform_found\" = 1 ] || { echo 'SDK Platform android.jar가 없습니다.'; exit 2; }"
            +"\n\"$JAVA_HOME/bin/java\" -version\n\"$JAVA_HOME/bin/javac\" -version\n\"$AAPT2\" version\n";
        if(!build)return check+"echo 'Android 빌드 도구 기본 검사를 마쳤습니다. 프로젝트 호환성은 실제 빌드로 확인합니다.'\n";
        return check+"[ -f ./gradlew ] || { echo '현재 프로젝트 루트에 gradlew가 없습니다.'; exit 2; }\n"
            +"exec /bin/sh ./gradlew "+quote(config.task())+" --no-daemon --max-workers=1 --console=plain "
            +quote("-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8")+" \"-Pandroid.aapt2FromMavenOverride=$AAPT2\"\n";
    }
    /** Caches and Gradle intermediate products are not installable build outputs. */
    private static boolean excluded(Path path) {
        String name=path.getFileName().toString();
        if(Set.of(".git",".gradle",".kotlin","node_modules").contains(name))return true;
        return path.getParent()!=null && path.getParent().getFileName()!=null
            && path.getParent().getFileName().toString().equals("build")
            && Set.of("intermediates","tmp","generated").contains(name);
    }
    private static int priority(Path path) {
        return Set.of("build","outputs","apk").contains(path.getFileName().toString())?0:1;
    }
    private static final class Scan {
        final Path root;final List<Apk> entries=new ArrayList<>();int visited;boolean truncated;
        Scan(Path root){this.root=root;}
        void walk(Path directory)throws IOException {
            if(visited>=5000 || entries.size()>=100){truncated=true;return;}
            List<Path> children=new ArrayList<>();
            try(DirectoryStream<Path> paths=Files.newDirectoryStream(directory)) {
                for(Path path:paths) {
                    if(excluded(path))continue;
                    if(++visited>5000){truncated=true;break;}
                    children.add(path);
                }
            }
            children.sort(Comparator.comparingInt(DeveloperBuild::priority).thenComparing(Path::toString));
            for(Path path:children) {
                BasicFileAttributes attr=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(attr.isSymbolicLink())continue;
                if(attr.isDirectory()) {
                    if(root.relativize(path).getNameCount()<8)walk(path);else truncated=true;
                } else if(attr.isRegularFile()&&path.getFileName().toString().endsWith(".apk")) {
                    if(entries.size()>=100){truncated=true;return;}
                    entries.add(new Apk(root.relativize(path).toString(),attr.size(),attr.lastModifiedTime().toMillis()));
                }
            }
        }
    }
    public static Listing list(Path directory)throws IOException {
        Scan scan=new Scan(directory.toRealPath());scan.walk(scan.root);
        scan.entries.sort(Comparator.comparingLong(Apk::modified).reversed());
        return new Listing(Collections.unmodifiableList(scan.entries),scan.truncated);
    }
    private static Path source(Path root,String relative)throws IOException {
        if(relative==null||relative.isEmpty()||relative.indexOf('\\')>=0||relative.indexOf('\0')>=0)
            throw new IOException("APK 상대 경로가 올바르지 않습니다.");
        Path rel;
        try{rel=Paths.get(relative);}catch(InvalidPathException error){throw new IOException("APK 경로가 올바르지 않습니다.",error);}
        if(rel.isAbsolute()||!relative.endsWith(".apk"))throw new IOException("프로젝트 내부 APK를 선택해 주세요.");
        Path selected=root;
        for(Path segment:rel) {
            String name=segment.toString();
            if(name.equals("..")||name.equals(".")||name.isEmpty())throw new IOException("상위 경로를 사용할 수 없습니다.");
            selected=selected.resolve(segment);
            if(Files.isSymbolicLink(selected))throw new IOException("심볼릭 링크 APK는 설치할 수 없습니다.");
        }
        if(!selected.toRealPath().startsWith(root)||!Files.isRegularFile(selected,LinkOption.NOFOLLOW_LINKS))throw new IOException("프로젝트 내부의 일반 APK 파일을 선택해 주세요.");
        return selected;
    }
    public static Apk describe(Path directory,String relative)throws IOException {
        Path selected=source(directory.toRealPath(),relative);
        BasicFileAttributes attrs=Files.readAttributes(selected,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!attrs.isRegularFile()||attrs.size()<1||attrs.size()>MAX_APK_BYTES)throw new IOException("APK 크기가 허용 범위를 벗어났습니다.");
        return new Apk(relative,attrs.size(),attrs.lastModifiedTime().toMillis());
    }
    public static Path copyApk(Path directory,String relative,Path cache,long maximumBytes)throws IOException {
        if(maximumBytes<1||maximumBytes>MAX_APK_BYTES)throw new IllegalArgumentException("Invalid APK size limit");
        Path selected=source(directory.toRealPath(),relative);
        if(Files.size(selected)<1||Files.size(selected)>maximumBytes)throw new IOException("APK 크기가 허용 범위를 벗어났습니다.");
        Files.createDirectories(cache);Path copy=Files.createTempFile(cache,"build-",".apk");boolean complete=false;
        try(InputStream input=Files.newInputStream(selected,LinkOption.NOFOLLOW_LINKS);OutputStream output=Files.newOutputStream(copy)) {
            byte[] buffer=new byte[65536];long total=0;int count;
            while((count=input.read(buffer))!=-1){total+=count;if(total>maximumBytes)throw new IOException("APK 크기 상한을 초과했습니다.");output.write(buffer,0,count);}
            if(total<1)throw new IOException("APK가 비어 있습니다.");complete=true;
        }finally{if(!complete)Files.deleteIfExists(copy);}
        return copy;
    }
}
