package dev.mobilecodex.app.core;
import java.io.*;import java.nio.file.*;import java.util.*;
public class ApkExportHarness {
 static int n;interface Op{void run()throws Exception;}static void check(boolean b){n++;if(!b)throw new AssertionError("assert "+n);}static void reject(Op op)throws Exception{n++;try{op.run();throw new AssertionError("accepted");}catch(IOException expected){}}
 public static void main(String[] args)throws Exception{
  if(args.length==2){try(var output=Files.newOutputStream(Path.of(args[1]),StandardOpenOption.CREATE_NEW)){System.out.println("APK bytes written: "+ApkExport.write(Path.of(args[0]),output,1024L*1024*1024));}return;}
  Path base=Files.createTempDirectory("apk-export-");try{
   Path source=base.resolve("source.apk");byte[] bytes={1,2,3,4};Files.write(source,bytes);ByteArrayOutputStream out=new ByteArrayOutputStream();check(ApkExport.write(source,out,10)==4);check(Arrays.equals(bytes,out.toByteArray()));check(Files.exists(source));reject(()->ApkExport.write(source,new ByteArrayOutputStream(),3));
   reject(()->ApkExport.write(source,new OutputStream(){public void write(int b)throws IOException{throw new IOException("provider failed");}},10));check(Arrays.equals(bytes,Files.readAllBytes(source)));
   ApkExport transfer=new ApkExport();transfer.reserve("request","A");reject(()->transfer.reserve("again","A"));Path copy=base.resolve("copy.apk");Files.copy(source,copy);check(transfer.prepared("request",copy));var pending=transfer.take("A");check(pending.requestId().equals("request"));check(pending.file().equals(copy));check(transfer.take("A")==null);Files.delete(copy);
   transfer.reserve("cancel","A");Files.copy(source,copy);check(transfer.prepared("cancel",copy));transfer.cancel();check(!Files.exists(copy));check(transfer.take("A")==null);
   transfer.reserve("stale","A");Files.copy(source,copy);transfer.prepared("stale",copy);reject(()->transfer.take("B"));check(!Files.exists(copy));check(transfer.take("A")==null);
   transfer.reserve("destroy","A");transfer.close();Files.copy(source,copy);check(!transfer.prepared("destroy",copy));check(!Files.exists(copy));reject(()->transfer.reserve("closed","A"));check(Files.exists(source));
   System.out.println("ApkExport: "+n+" assertions passed");
  }finally{try(var paths=Files.walk(base)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
