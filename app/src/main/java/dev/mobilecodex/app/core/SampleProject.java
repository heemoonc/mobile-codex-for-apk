package dev.mobilecodex.app.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Fixed, small template only. Existing project files are never replaced or deleted. */
public final class SampleProject {
    public interface Assets {InputStream open(String name)throws IOException;}
    public static final List<String> FILES=List.of("settings.gradle","build.gradle","gradle.properties","app/build.gradle","app/src/main/AndroidManifest.xml","app/src/main/java/dev/mobilecodex/sample/MainActivity.java","app/src/main/res/values/strings.xml","gradlew","gradle/wrapper/gradle-wrapper.jar","gradle/wrapper/gradle-wrapper.properties","WRAPPER-LICENSE.txt");
    private SampleProject() {}
    public static boolean canCreate(Path root)throws IOException {
        if(!Files.isDirectory(root,LinkOption.NOFOLLOW_LINKS))return false;
        try(DirectoryStream<Path> stream=Files.newDirectoryStream(root)){return !stream.iterator().hasNext();}
    }
    private static void requireEmpty(Path root)throws IOException {
        if(!canCreate(root))throw new IOException("샘플은 비어 있는 일반 폴더에만 만들 수 있습니다. 기존 파일은 변경하지 않았습니다.");
    }
    public static int create(Path root,Assets source)throws IOException {
        requireEmpty(root);Map<String,byte[]> data=new LinkedHashMap<>();long total=0;
        // Validate the entire packaged template before creating the first file.
        for(String name:FILES) {
            try(InputStream in=source.open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                if(in==null)throw new IOException("샘플 템플릿 파일이 없습니다.");
                byte[] b=new byte[8192];int n;
                while((n=in.read(b))!=-1){total+=n;if(out.size()+n>1024*1024||total>2L*1024*1024)throw new IOException("샘플 템플릿 크기 상한을 초과했습니다.");out.write(b,0,n);}
                if(out.size()==0)throw new IOException("샘플 템플릿이 비어 있습니다.");
                data.put(name,out.toByteArray());
            }
        }
        requireEmpty(root);
        try {
            for(Map.Entry<String,byte[]> item:data.entrySet()) {
                Path relative=Paths.get(item.getKey()),parent=root;
                for(int i=0;i<relative.getNameCount()-1;i++) {
                    parent=parent.resolve(relative.getName(i));
                    try{Files.createDirectory(parent);}catch(FileAlreadyExistsException exists){if(!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))throw new IOException("샘플 경로에 파일 또는 링크가 있습니다.",exists);}
                }
                try(OutputStream out=Files.newOutputStream(root.resolve(relative),StandardOpenOption.CREATE_NEW,LinkOption.NOFOLLOW_LINKS)){out.write(item.getValue());}
            }
        }catch(IOException error){throw new IOException("샘플 생성 중 오류가 발생했습니다. 일부 새 파일이 남아 있을 수 있습니다. 기존 파일은 덮어쓰거나 삭제하지 않았습니다.",error);}
        return data.size();
    }
}
