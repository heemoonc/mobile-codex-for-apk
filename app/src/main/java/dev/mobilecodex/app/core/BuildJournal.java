package dev.mobilecodex.app.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Latest button-started build only. Historical records never control a process. */
public final class BuildJournal implements AutoCloseable {
    public static final int MAX_LOG_CHARS=32768, MAX_FILE_BYTES=256*1024;
    private static final int MAGIC=0x4d434252, VERSION=1;
    private static final Set<String> STATES=Set.of("preparing","running","stopping","succeeded","failed","cancelled","interrupted");
    public record Entry(String jobId,String projectKey,String task,String status,long startedAt,long updatedAt,Integer exitCode,String output,boolean truncated) {}
    public record View(Entry record,boolean storageWarning) {}
    private final Path directory,file;
    private final ScheduledExecutorService writer=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"mobile-build-journal");t.setDaemon(true);return t;});
    private ScheduledFuture<?> pending;
    private Entry latest;
    private String activeTerminalId;
    private boolean awaitingStart, storageWarning, closed;

    public BuildJournal(Path directory) {
        this.directory=directory;file=directory.resolve("recent.bin");
        try {
            if(Files.exists(directory,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid journal directory");
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS))latest=read();
            if(latest!=null&&Set.of("preparing","running","stopping").contains(latest.status())) {
                latest=entry("interrupted",null,latest.output(),latest.truncated());requestSave(0);
            }
        }catch(IOException|RuntimeException error){latest=null;storageWarning=true;}
        writer.execute(this::cleanAbandonedFiles);
    }
    private void cleanAbandonedFiles() {
        try {
            if(!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))return;
            try(DirectoryStream<Path> entries=Files.newDirectoryStream(directory,".build-record-*.tmp")) {
                for(Path path:entries)if(path.getFileName().toString().matches("\\.build-record-[0-9]+\\.tmp")&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))Files.deleteIfExists(path);
            }
        }catch(IOException|RuntimeException error){synchronized(this){storageWarning=true;}}
    }
    public synchronized View view(String key){return new View(latest!=null&&latest.projectKey().equals(key)?latest:null,storageWarning);}
    public synchronized void begin(String key,String task) {
        if(closed)throw new IllegalStateException("Journal closed");
        if(key==null||!key.matches("[0-9a-f]{64}")||task==null||task.isBlank()||task.length()>200||task.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Invalid build record");
        long now=System.currentTimeMillis();latest=new Entry(UUID.randomUUID().toString(),key,task,"preparing",now,now,null,"",false);
        activeTerminalId=null;awaitingStart=true;requestSave(0);
    }
    public synchronized void state(String id,boolean running,boolean stopping,String output,Integer exitCode) {
        if(closed||latest==null||id==null||id.isBlank())return;
        if(awaitingStart&&running){activeTerminalId=id;awaitingStart=false;}
        if(!id.equals(activeTerminalId))return;
        String status=running?(stopping?"stopping":"running"):(latest.status().equals("stopping")?"cancelled":Integer.valueOf(0).equals(exitCode)?"succeeded":"failed");
        String text=output==null?"":output;
        latest=entry(status,running?null:exitCode,tail(text),latest.truncated()||text.length()>MAX_LOG_CHARS);
        if(!running)activeTerminalId=null;
        requestSave(0);
    }
    public synchronized void output(String id,String text) {
        if(closed||latest==null||activeTerminalId==null||!activeTerminalId.equals(id)||text==null)return;
        String combined=latest.output()+text;
        latest=entry(latest.status(),null,tail(combined),latest.truncated()||combined.length()>MAX_LOG_CHARS);requestSave(1000);
    }
    public synchronized void launchFailed() {
        if(closed||latest==null||!awaitingStart)return;
        awaitingStart=false;activeTerminalId=null;
        latest=entry("failed",null,"빌드를 시작하지 못했습니다.",false);requestSave(0);
    }
    private Entry entry(String status,Integer exit,String output,boolean truncated) {
        return new Entry(latest.jobId(),latest.projectKey(),latest.task(),status,latest.startedAt(),Math.max(latest.startedAt(),System.currentTimeMillis()),exit,output,truncated);
    }
    private static String tail(String text) {
        int start=Math.max(0,text.length()-MAX_LOG_CHARS);
        if(start<text.length()&&Character.isLowSurrogate(text.charAt(start)))start++;
        return text.substring(start);
    }
    private synchronized void requestSave(long delay) {
        if(closed)return;
        if(pending!=null&&!pending.isDone()) {
            if(delay!=0)return;
            pending.cancel(false);
        }
        pending=writer.schedule(this::checkpoint,delay,TimeUnit.MILLISECONDS);
    }
    private void checkpoint() {
        Entry snapshot;
        synchronized(this){pending=null;snapshot=latest;}
        if(snapshot==null)return;
        boolean warning=false;
        try{write(snapshot);}catch(IOException|RuntimeException error){warning=true;}
        synchronized(this){storageWarning=warning;}
    }
    private void write(Entry e)throws IOException {
        if(Files.exists(directory,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid journal directory");
        Files.createDirectories(directory);
        if(Files.isSymbolicLink(file))throw new IOException("Journal link rejected");
        Path temporary=Files.createTempFile(directory,".build-record-",".tmp");
        try {
            try(FileOutputStream raw=new FileOutputStream(temporary.toFile());DataOutputStream out=new DataOutputStream(raw)) {
                out.writeInt(MAGIC);out.writeInt(VERSION);out.writeUTF(e.jobId());out.writeUTF(e.projectKey());out.writeUTF(e.task());out.writeUTF(e.status());
                out.writeLong(e.startedAt());out.writeLong(e.updatedAt());out.writeBoolean(e.exitCode()!=null);if(e.exitCode()!=null)out.writeInt(e.exitCode());
                out.writeBoolean(e.truncated());byte[] text=e.output().getBytes(StandardCharsets.UTF_8);out.writeInt(text.length);out.write(text);out.flush();raw.getFD().sync();
            }
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(temporary);}
    }
    private Entry read()throws IOException {
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)>MAX_FILE_BYTES)throw new IOException("Invalid journal file");
        try(DataInputStream in=new DataInputStream(Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS))) {
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Invalid journal version");
            String job=in.readUTF(),key=in.readUTF(),task=in.readUTF(),status=in.readUTF();long started=in.readLong(),updated=in.readLong();Integer exit=in.readBoolean()?in.readInt():null;boolean truncated=in.readBoolean();int length=in.readInt();
            if(!job.matches("[a-f0-9-]{36}")||!key.matches("[a-f0-9]{64}")||task.isBlank()||task.length()>200||task.chars().anyMatch(c->c<32)||!STATES.contains(status)||started<0||updated<started||length<0||length>MAX_LOG_CHARS*4)throw new IOException("Invalid journal data");
            byte[] bytes=new byte[length];in.readFully(bytes);String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if(text.length()>MAX_LOG_CHARS||in.read()!=-1||status.equals("succeeded")&&!Integer.valueOf(0).equals(exit))throw new IOException("Invalid journal data");
            return new Entry(job,key,task,status,started,updated,exit,text,truncated);
        }
    }
    @Override public void close()throws IOException {
        synchronized(this){
            if(closed)return;
            if(pending!=null)pending.cancel(false);
            writer.execute(this::checkpoint);closed=true;writer.shutdown();
        }
        try{if(!writer.awaitTermination(5,TimeUnit.SECONDS)){writer.shutdownNow();throw new IOException("Journal save timeout");}}
        catch(InterruptedException error){Thread.currentThread().interrupt();throw new IOException("Journal save interrupted",error);}
    }
}
