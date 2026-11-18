package dev.mobilecodex.app.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Bounded app-format source archives; never executes or restores links/permissions. */
public final class SourceBackup {
    public record Limits(long zipBytes,long totalBytes,long fileBytes,int entries,int manifestBytes,int depth) {}
    public static final Limits DEFAULT=new Limits(100L*1024*1024,200L*1024*1024,50L*1024*1024,10000,2*1024*1024,32);
    public record Preview(String name,int entries,long bytes,String sha256) {}
    private record Item(String path,boolean directory,long size,long modified,String sha256) {}
    private record Manifest(String name,LinkedHashMap<String,Item> items) {}
    private SourceBackup() {}
    private static void path(String path,Limits l)throws IOException {
        if(path==null||path.isEmpty()||path.startsWith("/")||path.indexOf('\\')>=0||path.chars().anyMatch(c->c<32||c==127))throw new IOException("백업 상대 경로가 올바르지 않습니다.");
        String[] parts=path.split("/",-1);if(parts.length>l.depth())throw new IOException("백업 경로 깊이 상한을 초과했습니다.");
        for(String part:parts)if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("백업 경로 이탈을 거부했습니다.");
    }
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static final OutputStream DISCARD=new OutputStream(){@Override public void write(int b){} @Override public void write(byte[] b,int off,int n){}};
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    public static String hash(Path path)throws IOException {try(InputStream in=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)){return copy(in,DISCARD,DEFAULT.zipBytes(),-1);}}
    private static boolean excluded(Path root,Path entry,boolean directory) {
        String name=entry.getFileName().toString();
        if(Set.of(".git",".gradle",".kotlin","node_modules").contains(name))return true;
        if(entry.getParent().equals(root)&&name.equals("local.properties"))return true;
        return directory&&name.equals("build")&&(entry.getParent().equals(root)||Files.isRegularFile(entry.getParent().resolve("build.gradle"),LinkOption.NOFOLLOW_LINKS)||Files.isRegularFile(entry.getParent().resolve("build.gradle.kts"),LinkOption.NOFOLLOW_LINKS));
    }
    private static LinkedHashMap<String,Item> inventory(Path root,Limits l,boolean hashes)throws IOException {
        var items=new LinkedHashMap<String,Item>();long[] total={0};
        Files.walkFileTree(root,new SimpleFileVisitor<Path>(){
            private void add(Path file,BasicFileAttributes a,boolean dir)throws IOException {
                String name=root.relativize(file).toString().replace(File.separatorChar,'/');path(name,l);
                if(items.size()>=l.entries())throw new IOException("백업 항목 수 상한을 초과했습니다.");
                if(!dir){if(!a.isRegularFile()||a.size()>l.fileBytes())throw new IOException("백업에 링크·특수 파일 또는 큰 파일이 있습니다.");total[0]+=a.size();if(total[0]>l.totalBytes())throw new IOException("백업 총 용량 상한을 초과했습니다.");}
                String sha="";if(!dir&&hashes)try(InputStream input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){sha=copy(input,DISCARD,l.fileBytes(),a.size());}BasicFileAttributes after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                if(!dir&&(a.size()!=after.size()||!a.lastModifiedTime().equals(after.lastModifiedTime())||!after.isRegularFile()))throw new IOException("백업 중 소스가 변경되었습니다.");
                items.put(name,new Item(name,dir,dir?0:a.size(),dir?0:a.lastModifiedTime().toMillis(),sha));
            }
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes a)throws IOException{if(dir.equals(root))return FileVisitResult.CONTINUE;if(excluded(root,dir,true))return FileVisitResult.SKIP_SUBTREE;add(dir,a,true);return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes a)throws IOException{if(!excluded(root,file,false))add(file,a,false);return FileVisitResult.CONTINUE;}
        });return items;
    }
    private static String title(String name)throws IOException {if(name==null||name.isBlank()||name.length()>200||name.chars().anyMatch(c->c<32||c==127))throw new IOException("백업 프로젝트 이름이 올바르지 않습니다.");return name;}
    public static Preview write(Path source,String name,Path archive,Limits limits)throws IOException {
        title(name);if(Files.isSymbolicLink(source)||!Files.isDirectory(source,LinkOption.NOFOLLOW_LINKS))throw new IOException("일반 프로젝트 폴더가 필요합니다.");Path root=source.toRealPath();
        if(archive.toAbsolutePath().normalize().startsWith(root))throw new IOException("백업 캐시는 프로젝트 밖에 있어야 합니다.");
        var items=inventory(root,limits,true);JSONArray entries=new JSONArray();for(Item i:items.values())entries.put(Json.obj("path",i.path(),"directory",i.directory(),"size",i.size(),"sha256",i.sha256()));
        byte[] manifest=Json.obj("format","mobile-codex-source","version",1,"name",name,"createdAt",System.currentTimeMillis(),"entries",entries).toString().getBytes(StandardCharsets.UTF_8);
        if(manifest.length>limits.manifestBytes())throw new IOException("백업 목록 크기 상한을 초과했습니다.");
        OutputStream raw=Files.newOutputStream(archive,StandardOpenOption.CREATE_NEW);boolean complete=false;
        try {
            try(ZipOutputStream zip=new ZipOutputStream(new LimitedOutput(raw,limits.zipBytes()))) {
                zip.putNextEntry(new ZipEntry("backup.json"));zip.write(manifest);zip.closeEntry();
                for(Item i:items.values()) {
                    zip.putNextEntry(new ZipEntry("project/"+i.path()+(i.directory()?"/":"")));
                    if(!i.directory())try(InputStream in=Files.newInputStream(root.resolve(i.path()),LinkOption.NOFOLLOW_LINKS)){if(!copy(in,zip,limits.fileBytes(),i.size()).equals(i.sha256()))throw new IOException("백업 중 파일 내용이 변경되었습니다.");}
                    zip.closeEntry();
                }
            }
            var after=inventory(root,limits,true);if(after.size()!=items.size())throw new IOException("백업 중 프로젝트 목록이 변경되었습니다.");
            for(Item i:items.values()){Item a=after.get(i.path());if(a==null||a.directory()!=i.directory()||a.size()!=i.size()||a.modified()!=i.modified()||!a.sha256().equals(i.sha256()))throw new IOException("백업 중 소스가 변경되었습니다.");}
            Preview result=inspect(archive,limits);complete=true;return result;
        }finally{if(!complete)Files.deleteIfExists(archive);}
    }
    private static void jsonDepth(String text)throws IOException {
        int depth=0;boolean quoted=false,escaped=false;char previous=0;
        for(int n=0;n<text.length();n++){
            char c=text.charAt(n);
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"'){quoted=false;previous='"';}continue;}
            if(Character.isWhitespace(c))continue;
            // Android JSONObject accepts comments/single quotes and bare tokens. Reject
            // alternative quoting so the preflight and parser see identical nesting.
            if(c=='\''||c=='/')throw new IOException("표준 JSON 백업 목록이 필요합니다.");
            if(c=='"'){if(previous!='{'&&previous!='['&&previous!=':'&&previous!=',')throw new IOException("백업 목록 문자열 위치가 올바르지 않습니다.");quoted=true;}
            else if(c=='{'||c=='['){if(++depth>64)throw new IOException("백업 목록의 JSON 중첩 상한을 초과했습니다.");}
            else if(c=='}'||c==']'){if(--depth<0)throw new IOException("백업 목록 구조가 올바르지 않습니다.");}
            previous=c;
        }
        if(depth!=0||quoted)throw new IOException("백업 목록 구조가 올바르지 않습니다.");
    }
    private static Manifest manifest(byte[] bytes,Limits l)throws IOException {
        try {
            String json=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();jsonDepth(json);JSONObject data=new JSONObject(json);
            if(!"mobile-codex-source".equals(data.getString("format"))||data.getInt("version")!=1)throw new IOException("지원하는 소스 백업 ZIP이 아닙니다.");
            String name=title(data.getString("name"));JSONArray entries=data.getJSONArray("entries");if(entries.length()>l.entries())throw new IOException("백업 항목 수 상한을 초과했습니다.");
            var items=new LinkedHashMap<String,Item>();long total=0;
            for(int n=0;n<entries.length();n++){
                JSONObject e=entries.getJSONObject(n);String p=e.getString("path");path(p,l);boolean directory=e.getBoolean("directory");long size=e.getLong("size");String sha=e.getString("sha256");
                if(size<0||size>l.fileBytes()||directory&&(size!=0||!sha.isEmpty())||!directory&&!sha.matches("[a-f0-9]{64}")||items.containsKey(p))throw new IOException("백업 목록에 중복 또는 잘못된 항목이 있습니다.");
                total+=size;if(total>l.totalBytes())throw new IOException("복원 총 용량 상한을 초과했습니다.");items.put(p,new Item(p,directory,size,0,sha));
            }
            for(Item i:items.values()){int slash=i.path().lastIndexOf('/');while(slash>=0){String parent=i.path().substring(0,slash);Item p=items.get(parent);if(p==null||!p.directory())throw new IOException("백업 파일·폴더 경로가 충돌합니다.");slash=parent.lastIndexOf('/');}}
            return new Manifest(name,items);
        }catch(JSONException|java.nio.charset.CharacterCodingException e){throw new IOException("백업 목록을 읽지 못했습니다.",e);}
    }
    public static Preview inspect(Path archive,Limits limits)throws IOException{return read(archive,null,limits);}
    public static Preview extract(Path archive,Path target,Limits limits)throws IOException {
        if(Files.isSymbolicLink(target)||!Files.isDirectory(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("복원 대상은 비어 있는 일반 폴더여야 합니다.");
        try(var entries=Files.newDirectoryStream(target)){if(entries.iterator().hasNext())throw new IOException("기존 폴더는 덮어쓰지 않습니다.");}
        // Validate the entire archive before writing the first restored source file.
        inspect(archive,limits);return read(archive,target.toRealPath(),limits);
    }
    private static Preview read(Path archive,Path target,Limits l)throws IOException {
        if(!Files.isRegularFile(archive,LinkOption.NOFOLLOW_LINKS)||Files.size(archive)>l.zipBytes())throw new IOException("백업 ZIP 크기가 허용 범위를 벗어났습니다.");
        Set<String> centralNames=central(archive,l);
        Manifest data;Set<String> seen=new HashSet<>();long total=0;
        try(ZipInputStream zip=new ZipInputStream(new LimitedInput(Files.newInputStream(archive,LinkOption.NOFOLLOW_LINKS),l.zipBytes()))) {
            ZipEntry first=zip.getNextEntry();if(first==null||!first.getName().equals("backup.json")||first.isDirectory())throw new IOException("이 앱에서 만든 소스 백업 ZIP을 선택하십시오.");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();copy(zip,buffer,l.manifestBytes(),-1);data=manifest(buffer.toByteArray(),l);zip.closeEntry();ZipEntry e;
            while((e=zip.getNextEntry())!=null) {
                String name=e.getName();boolean directory=e.isDirectory();if(!name.startsWith("project/"))throw new IOException("백업에 알 수 없는 항목이 있습니다.");String p=name.substring(8);if(directory)p=p.substring(0,p.length()-1);path(p,l);
                Item i=data.items().get(p);if(i==null||i.directory()!=directory||!seen.add(p)||seen.size()>l.entries())throw new IOException("백업 항목이 목록과 일치하지 않습니다.");
                Path out=target==null?null:target.resolve(p);
                if(out!=null){Path parent=out.getParent();Files.createDirectories(parent);if(directory)Files.createDirectories(out);}
                if(directory){if(zip.read()!=-1)throw new IOException("백업 폴더에 파일 데이터가 있습니다.");}
                else {
                    OutputStream sink=out==null?DISCARD:Files.newOutputStream(out,StandardOpenOption.CREATE_NEW,LinkOption.NOFOLLOW_LINKS);
                    try(sink){if(!copy(zip,sink,l.fileBytes(),i.size()).equals(i.sha256()))throw new IOException("백업 파일 해시가 일치하지 않습니다.");}total+=i.size();if(total>l.totalBytes())throw new IOException("복원 용량 상한을 초과했습니다.");
                }
                zip.closeEntry();
            }
            if(seen.size()!=data.items().size())throw new IOException("백업 파일이 누락되었습니다.");
        }
        Set<String> expectedNames=new HashSet<>();expectedNames.add("backup.json");for(Item item:data.items().values())expectedNames.add("project/"+item.path()+(item.directory()?"/":""));if(!centralNames.equals(expectedNames))throw new IOException("ZIP 중앙 목록과 소스 목록이 일치하지 않습니다.");
        if(Files.size(archive)>l.zipBytes())throw new IOException("백업 ZIP 크기 상한을 초과했습니다.");
        return new Preview(data.name(),seen.size(),total,hash(archive));
    }
    // Bound central-directory parsing before streaming payloads; reject truncated and ZIP64 archives.
    private static long le(byte[] b,int off,int count){long v=0;for(int i=0;i<count;i++)v|=(long)(b[off+i]&255)<<(8*i);return v;}
    private static Set<String> central(Path archive,Limits l)throws IOException {
        try(RandomAccessFile file=new RandomAccessFile(archive.toFile(),"r")){
            long length=file.length();int tailSize=(int)Math.min(length,65557);byte[] tail=new byte[tailSize];file.seek(length-tailSize);file.readFully(tail);int end=-1;
            for(int i=tail.length-22;i>=0;i--)if(le(tail,i,4)==0x06054b50L&&i+22+le(tail,i+20,2)==tail.length){end=i;break;}
            if(end<0)throw new IOException("ZIP 끝 기록이 없거나 손상되었습니다.");
            long count=le(tail,end+10,2),size=le(tail,end+12,4),offset=le(tail,end+16,4),absolute=length-tailSize+end;
            if(le(tail,end+4,2)!=0||le(tail,end+6,2)!=0||le(tail,end+8,2)!=count||count>l.entries()+1L||count==65535||offset+size!=absolute)throw new IOException("ZIP 중앙 목록이 올바르지 않습니다.");
            file.seek(offset);Set<String> names=new HashSet<>();
            for(long i=0;i<count;i++){
                if(file.getFilePointer()+46>absolute)throw new IOException("ZIP 중앙 목록이 잘렸습니다.");byte[] header=new byte[46];file.readFully(header);
                if(le(header,0,4)!=0x02014b50L||le(header,34,2)!=0||le(header,42,4)>=offset)throw new IOException("ZIP 중앙 항목이 올바르지 않습니다.");
                int n=(int)le(header,28,2);long extra=le(header,30,2)+le(header,32,2);if(file.getFilePointer()+n+extra>absolute)throw new IOException("ZIP 중앙 경로가 잘렸습니다.");
                byte[] bytes=new byte[n];file.readFully(bytes);String name;
                try{name=StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();}catch(java.nio.charset.CharacterCodingException e){throw new IOException("ZIP 경로 인코딩이 올바르지 않습니다.",e);}
                if(!names.add(name))throw new IOException("ZIP 중앙 경로가 중복됩니다.");file.seek(file.getFilePointer()+extra);
            }
            if(file.getFilePointer()!=absolute)throw new IOException("ZIP 중앙 목록 크기가 일치하지 않습니다.");return names;
        }
    }
    private static String copy(InputStream in,OutputStream out,long limit,long expected)throws IOException {MessageDigest d=digest();byte[] b=new byte[65536];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>limit||expected>=0&&total>expected)throw new IOException("파일 크기 상한을 초과했습니다.");d.update(b,0,n);out.write(b,0,n);}if(expected>=0&&total!=expected)throw new IOException("백업 파일 크기가 일치하지 않습니다.");return hex(d.digest());}
    private static final class LimitedOutput extends FilterOutputStream {long count,limit;LimitedOutput(OutputStream out,long limit){super(out);this.limit=limit;}@Override public void write(int b)throws IOException{if(++count>limit)throw new IOException("ZIP 크기 상한을 초과했습니다.");out.write(b);}@Override public void write(byte[] b,int off,int n)throws IOException{count+=n;if(count>limit)throw new IOException("ZIP 크기 상한을 초과했습니다.");out.write(b,off,n);}}
    private static final class LimitedInput extends FilterInputStream {long count,limit;LimitedInput(InputStream in,long limit){super(in);this.limit=limit;}@Override public int read()throws IOException{int b=in.read();if(b>=0&&++count>limit)throw new IOException("ZIP 크기 상한을 초과했습니다.");return b;}@Override public int read(byte[] b,int off,int n)throws IOException{int r=in.read(b,off,n);if(r>0&&(count+=r)>limit)throw new IOException("ZIP 크기 상한을 초과했습니다.");return r;}}
}
