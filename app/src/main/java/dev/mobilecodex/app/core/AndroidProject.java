package dev.mobilecodex.app.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Named fixed template. Only this invocation's staging files may be removed. */
public final class AndroidProject {
    public record Config(String name,String packageName,String folder) {}
    private static final Set<String> KEYWORDS=Set.of(("abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null _").split(" "));
    private AndroidProject() {}
    public static String folder(String value) {
        if(value==null||value.chars().anyMatch(c->c<32||c==127))throw new IllegalArgumentException("폴더 이름이 올바르지 않습니다.");
        String name=value.trim();
        if(name.isEmpty()||name.length()>64||!name.matches("[\\p{L}\\p{N} _-]+"))throw new IllegalArgumentException("폴더 이름은 64자 이내의 문자·숫자·공백·밑줄·하이픈만 사용할 수 있습니다.");
        return name;
    }
    public static Config validate(Config config) {
        if(config==null||config.name()==null||config.name().chars().anyMatch(c->c<32||c==127))throw new IllegalArgumentException("앱 이름이 올바르지 않습니다.");
        String name=config.name().trim(),pkg=config.packageName()==null?"":config.packageName().trim();
        if(name.isEmpty()||name.length()>80)throw new IllegalArgumentException("앱 이름은 1~80자로 입력하십시오.");
        if(pkg.length()>150||!pkg.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+"))throw new IllegalArgumentException("패키지명은 com.example.myapp 형식의 소문자로 입력하십시오.");
        for(String part:pkg.split("\\."))if(KEYWORDS.contains(part))throw new IllegalArgumentException("패키지명에는 Java 예약어를 사용할 수 없습니다.");
        if(Set.of("dev.mobilecodex.app","dev.mobilecodex.apk","dev.mobilecodex.sample").contains(pkg))throw new IllegalArgumentException("기존 Codex·샘플 앱과 다른 패키지명을 입력하십시오.");
        return new Config(name,pkg,folder(config.folder()));
    }
    private static String read(Path path)throws IOException{return new String(Files.readAllBytes(path),StandardCharsets.UTF_8);}
    private static void write(Path path,String value,OpenOption... options)throws IOException{Files.write(path,value.getBytes(StandardCharsets.UTF_8),options);}
    private static String resource(String value) {
        String escaped=value.replace("\\","\\\\").replace("\"","\\\"").replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("'","&apos;");
        return "\""+escaped+"\"";
    }
    private static String profileText(SampleProject.Assets assets,String name)throws IOException {
        if(assets==null)throw new IOException("앱 템플릿 파일이 없습니다.");
        try(InputStream in=assets.open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(in==null)throw new IOException("앱 템플릿 파일이 없습니다.");
            byte[] bytes=new byte[4096];int count;
            while((count=in.read(bytes))!=-1){if(out.size()+count>65536)throw new IOException("앱 템플릿 크기 상한을 초과했습니다.");out.write(bytes,0,count);}
            if(out.size()==0)throw new IOException("앱 템플릿이 비어 있습니다.");
            return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    public static Path create(Path parent,Config input,SampleProject.Assets assets)throws IOException {
        return create(parent,input,assets,"java",null);
    }
    public static Path create(Path parent,Config input,SampleProject.Assets assets,String template,SampleProject.Assets profileAssets)throws IOException {
        Config c=validate(input);
        if(!Set.of("java","kotlin","compose").contains(template))throw new IllegalArgumentException("지원하지 않는 앱 템플릿입니다.");
        if(!template.equals("java"))for(String part:c.packageName().split("\\."))if(Set.of("as","fun","in","is","object","typealias","typeof","val","var","when").contains(part))throw new IllegalArgumentException("Kotlin 예약어는 패키지명에 사용할 수 없습니다.");
        if(Files.isSymbolicLink(parent))throw new IOException("프로젝트 저장 위치가 링크입니다.");
        Files.createDirectories(parent);Path root=parent.toRealPath(),target=root.resolve(c.folder());
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("같은 이름의 폴더가 이미 있습니다. 다른 이름을 입력하십시오.");
        Path stage=Files.createTempDirectory(root,".creating-");
        try {
            SampleProject.create(stage,assets);
            Path gradle=stage.resolve("app/build.gradle");
            String build=read(gradle).replace("dev.mobilecodex.sample",c.packageName()).replace("0.1-sample1","1.0");write(gradle,build);
            Path old=stage.resolve("app/src/main/java/dev/mobilecodex/sample/MainActivity.java"),source=stage.resolve("app/src/main/java/"+c.packageName().replace('.','/')+"/MainActivity.java");
            String java=read(old).replace("package dev.mobilecodex.sample;","package "+c.packageName()+";");Files.createDirectories(source.getParent());write(source,java,StandardOpenOption.CREATE_NEW);Files.delete(old);
            Path manifest=stage.resolve("app/src/main/AndroidManifest.xml");write(manifest,read(manifest).replace("android:label=\"Mobile Codex Sample\"","android:label=\"@string/app_name\""));
            String text="<resources>\n    <string name=\"app_name\">"+resource(c.name())+"</string>\n    <string name=\"hello\">"+resource(c.name())+"</string>\n</resources>\n";
            write(stage.resolve("app/src/main/res/values/strings.xml"),text);
            if(!template.equals("java")) {
                String rootBuild=profileText(profileAssets,"build.gradle"),appBuild=profileText(profileAssets,"app-build.gradle"),activity=profileText(profileAssets,"MainActivity.kt");
                write(stage.resolve("build.gradle"),rootBuild);
                write(gradle,appBuild.replace("dev.mobilecodex.sample",c.packageName()).replace("0.1-sample1","1.0"));
                write(source.resolveSibling("MainActivity.kt"),activity.replace("dev.mobilecodex.sample",c.packageName()),StandardOpenOption.CREATE_NEW);
                Files.delete(source);
                Path properties=stage.resolve("gradle.properties");write(properties,read(properties)+"\nkotlin.compiler.execution.strategy=in-process\nkotlin.incremental=false\n");
            }
            // No REPLACE_EXISTING: an existing destination is never merged or replaced.
            Files.move(stage,target);return target;
        }catch(IOException|RuntimeException error){
            try(var paths=Files.walk(stage)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
            catch(IOException cleanup){error.addSuppressed(cleanup);throw new IOException("프로젝트 생성에 실패했습니다. 임시 폴더가 남았습니다: "+stage,error);}
            throw error;
        }
    }
}
