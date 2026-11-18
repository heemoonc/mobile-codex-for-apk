package dev.mobilecodex.app.core;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/** One Activity's native-only APK save request. Never owns the project's source APK. */
public final class ApkExport {
    public record Pending(String requestId,String scope,Path file) {}
    private String requestId,scope;
    private Path file;
    private boolean closed;
    public synchronized void reserve(String id,String key)throws IOException {
        if(closed||requestId!=null)throw new IOException("APK 저장이 이미 진행 중이거나 화면이 종료되었습니다.");
        if(id==null||key==null)throw new IOException("APK 저장 요청이 올바르지 않습니다.");
        requestId=id;scope=key;
    }
    public synchronized boolean prepared(String id,Path copy)throws IOException {
        if(closed||!id.equals(requestId)){Files.deleteIfExists(copy);return false;}
        if(file!=null)throw new IOException("APK 저장 사본이 이미 준비되었습니다.");
        file=copy;return true;
    }
    /** Transfers cache ownership to the caller, which must remove it in finally. */
    public synchronized Pending take(String currentScope)throws IOException {
        if(requestId==null)return null;
        Pending value=new Pending(requestId,scope,file);requestId=null;scope=null;file=null;
        if(value.file()==null)throw new IOException("APK 저장 사본이 준비되지 않았습니다.");
        if(!value.scope().equals(currentScope)){Files.deleteIfExists(value.file());throw new IOException("프로젝트가 바뀌었습니다. APK 저장을 다시 시작하십시오.");}
        return value;
    }
    public synchronized String requestId(){return requestId;}
    public synchronized void cancel()throws IOException {Path old=file;file=null;requestId=null;scope=null;if(old!=null)Files.deleteIfExists(old);}
    public synchronized void close()throws IOException {closed=true;cancel();}
    /** The caller closes its document output before reporting success. */
    public static long write(Path source,OutputStream output,long limit)throws IOException {
        if(output==null)throw new IOException("저장 위치를 열지 못했습니다.");
        BasicFileAttributes before=Files.readAttributes(source,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isRegularFile()||before.size()<1||before.size()>limit)throw new IOException("APK 크기가 허용 범위를 벗어났습니다.");
        long total=0;byte[] buffer=new byte[65536];int count;
        try(InputStream input=Files.newInputStream(source,LinkOption.NOFOLLOW_LINKS)){
            while((count=input.read(buffer))!=-1){total+=count;if(total>limit||total>before.size())throw new IOException("APK 크기가 변경되었거나 상한을 초과했습니다.");output.write(buffer,0,count);}
        }
        BasicFileAttributes after=Files.readAttributes(source,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(total!=before.size()||!after.isRegularFile()||after.size()!=before.size()||!after.lastModifiedTime().equals(before.lastModifiedTime()))throw new IOException("저장 중 APK 사본이 변경되었습니다.");
        return total;
    }
}
