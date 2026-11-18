package dev.mobilecodex.app.core;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class BuildJournalHarness {
 static int n;static final String A="a".repeat(64),B="b".repeat(64);
 static void check(boolean ok){n++;if(!ok)throw new AssertionError("assertion "+n);}
 public static void main(String[] args)throws Exception {
  if(args.length==2&&args[0].equals("--seed-success")){
   try(var j=new BuildJournal(Path.of(args[1]))){j.begin(A,"assembleDebug");j.state("seed",true,false,"old successful log",null);j.state("seed",false,false,"old successful log",0);}return;
  }
  if(args.length==2&&args[0].equals("--write-failure")){
   Path dir=Path.of(args[1]),file=dir.resolve("recent.bin");byte[] before=Files.readAllBytes(file);var j=new BuildJournal(dir);
   j.begin(A,"assembleDebug");j.state("new",true,false,"new log".repeat(6000),null);j.close();
   check(j.view(A).storageWarning());check(j.view(A).record().output().contains("new log"));check(Arrays.equals(before,Files.readAllBytes(file)));
   try(var files=Files.list(dir)){check(files.noneMatch(p->p.getFileName().toString().endsWith(".tmp")));}
   System.out.println("Write failure preserved previous bytes and memory record: 4 assertions passed");return;
  }
  if(args.length==2){
   try(var journal=new BuildJournal(Path.of(args[1]))){
    if(args[0].equals("--leave-running")){journal.begin(A,"assembleDebug");journal.state("old-session",true,false,"checkpoint before process exit",null);}
    else{var e=journal.view(A).record();check(e!=null&&e.status().equals("interrupted")&&e.exitCode()==null);check(e.output().contains("checkpoint before process exit"));System.out.println("Separate JVM recovered interrupted record");}
   }return;
  }
  Path root=Files.createTempDirectory("build-journal-");
  try {
   Path normal=root.resolve("normal");String first;
   try(var j=new BuildJournal(normal)){
    check(j.view(A).record()==null&&!j.view(A).storageWarning());j.begin(A,"assembleDebug");
    j.state("session-one",true,false,"header",null);j.output("other","ignored");j.output("session-one","body");
    j.state("session-one",false,false,"headerbody",0);var e=j.view(A).record();first=e.jobId();check(e.status().equals("succeeded"));check(e.exitCode()==0&&e.output().equals("headerbody"));check(j.view(B).record()==null);
   }
   try(var j=new BuildJournal(normal)){check(j.view(A).record().jobId().equals(first));check(j.view(A).record().status().equals("succeeded"));
    j.begin(A,":app:assembleDebug");j.state("s2",true,false,"running",null);j.state("s2",true,true,"stopping",null);j.state("s2",false,false,"stopped",143);check(j.view(A).record().status().equals("cancelled"));}
   try(var j=new BuildJournal(normal)){check(j.view(A).record().status().equals("cancelled"));j.begin(A,"assembleDebug");j.state("s3",true,false,"partial",null);}
   try(var j=new BuildJournal(normal)){check(j.view(A).record().status().equals("interrupted"));check(j.view(A).record().exitCode()==null);check(j.view(A).record().output().equals("partial"));}
   try(var j=new BuildJournal(normal)){check(j.view(A).record().status().equals("interrupted"));
    j.begin(A,"assembleDebug");j.state("s4",true,false,"",null);j.output("s4","문😀".repeat(40000));var e=j.view(A).record();check(e.output().length()<=BuildJournal.MAX_LOG_CHARS&&e.truncated());check(!Character.isLowSurrogate(e.output().charAt(0)));
    j.state("s4",false,false,"compiler error",1);check(j.view(A).record().status().equals("failed"));
    j.begin(B,"assembleDebug");j.launchFailed();check(j.view(B).record().status().equals("failed")&&j.view(B).record().exitCode()==null);check(j.view(A).record()==null);
   }
   try(var j=new BuildJournal(normal)){for(int i=0;i<30;i++){j.begin(A,"assembleDebug");j.state("session-"+i,true,false,"run"+i,null);j.state("session-"+i,false,false,"done"+i,0);}}
   try(var j=new BuildJournal(normal)){check(j.view(A).record().output().equals("done29"));check(j.view(A).record().status().equals("succeeded"));}
   check(Files.size(normal.resolve("recent.bin"))<=BuildJournal.MAX_FILE_BYTES);check(!new String(Files.readAllBytes(normal.resolve("recent.bin")),StandardCharsets.ISO_8859_1).contains("session-29"));
   Path blocked=root.resolve("blocked");Files.writeString(blocked,"sentinel");BuildJournal j=new BuildJournal(blocked);j.begin(A,"assembleDebug");j.close();check(j.view(A).record()!=null&&j.view(A).storageWarning());check(Files.readString(blocked).equals("sentinel"));
   for(boolean oversized:List.of(false,true)){Path dir=Files.createDirectory(root.resolve(oversized?"large":"broken"));byte[] bytes=oversized?new byte[BuildJournal.MAX_FILE_BYTES+1]:new byte[]{1,2,3};Files.write(dir.resolve("recent.bin"),bytes);try(var bad=new BuildJournal(dir)){check(bad.view(A).record()==null&&bad.view(A).storageWarning());}check(Arrays.equals(bytes,Files.readAllBytes(dir.resolve("recent.bin"))));}
   Path linked=Files.createDirectory(root.resolve("linked")),outside=root.resolve("outside");Files.writeString(outside,"untouched");Files.createSymbolicLink(linked.resolve("recent.bin"),outside);
   try(var bad=new BuildJournal(linked)){check(bad.view(A).storageWarning());bad.begin(A,"assembleDebug");}check(Files.readString(outside).equals("untouched"));check(Files.isSymbolicLink(linked.resolve("recent.bin")));
   Path malformed=Files.createDirectory(root.resolve("malformed"));byte[] invalid=Files.readAllBytes(normal.resolve("recent.bin"));invalid[invalid.length-1]=(byte)0xff;Files.write(malformed.resolve("recent.bin"),invalid);
   try(var bad=new BuildJournal(malformed)){check(bad.view(A).record()==null&&bad.view(A).storageWarning());}
   check(Arrays.equals(invalid,Files.readAllBytes(malformed.resolve("recent.bin"))));
   Path orphan=Files.createDirectory(root.resolve("orphan")),abandoned=orphan.resolve(".build-record-123.tmp"),keep=orphan.resolve("unrelated.tmp"),dir=orphan.resolve(".build-record-456.tmp");
   Files.writeString(abandoned,"partial");Files.writeString(keep,"keep");Files.createDirectory(dir);Files.writeString(dir.resolve("keep"),"keep");Files.createSymbolicLink(orphan.resolve(".build-record-789.tmp"),outside);
   try(var recovered=new BuildJournal(orphan)){}
   check(!Files.exists(abandoned));check(Files.readString(keep).equals("keep"));check(Files.readString(dir.resolve("keep")).equals("keep"));check(Files.isSymbolicLink(orphan.resolve(".build-record-789.tmp")));check(Files.readString(outside).equals("untouched"));
   System.out.println("BuildJournal: "+n+" assertions passed");
  }finally{try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
