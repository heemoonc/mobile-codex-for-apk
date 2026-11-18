package dev.mobilecodex.app.core;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;

/** Native-owned one-shot source previews. No browser-supplied filesystem paths. */
public final class SourceTransfers {
 public record Export(Path file,SourceBackup.Preview preview) {}
 public record Preview(String token,SourceBackup.Preview summary) {}
 private record Pending(String token,String scope,Path file,String hash) {}
 private final Path cache;private Pending pending;
 public SourceTransfers(Path cache)throws IOException{this.cache=cache;Files.createDirectories(cache);try(var files=Files.newDirectoryStream(cache)){for(Path file:files){String name=file.getFileName().toString();if(name.matches("(?:export|import)-[a-f0-9-]{36}\\.zip")&&!Files.isDirectory(file,LinkOption.NOFOLLOW_LINKS))Files.deleteIfExists(file);}}}
 public static void failure(String requestId,Exception error,java.util.function.Consumer<Exception> response,java.util.function.Consumer<String> notice){
  if(requestId==null)notice.accept("화면이 다시 열렸습니다. ZIP을 다시 선택하십시오.");else response.accept(error);
 }
 public synchronized Export export(Path root,String name)throws IOException{
  Path file=cache.resolve("export-"+UUID.randomUUID()+".zip");
  try{return new Export(file,SourceBackup.write(root,name,file,SourceBackup.DEFAULT));}catch(IOException e){Files.deleteIfExists(file);throw e;}
 }
 public synchronized Preview preview(InputStream in,String scope)throws IOException{
  cancel();Path file=cache.resolve("import-"+UUID.randomUUID()+".zip");boolean keep=false;
  try{
   try(OutputStream out=Files.newOutputStream(file,StandardOpenOption.CREATE_NEW)){byte[] b=new byte[65536];long count=0;int n;while((n=in.read(b))!=-1){count+=n;if(count>SourceBackup.DEFAULT.zipBytes())throw new IOException("ZIP 크기 상한을 초과했습니다.");out.write(b,0,n);}}
   var summary=SourceBackup.inspect(file,SourceBackup.DEFAULT);String token=UUID.randomUUID().toString();pending=new Pending(token,scope,file,summary.sha256());keep=true;return new Preview(token,summary);
  }finally{if(!keep)Files.deleteIfExists(file);}
 }
 @FunctionalInterface public interface Check {void run()throws Exception;}
 /** A rejected apply attempt must not leave its own reusable preview behind. */
 public synchronized Path checkedConsume(String token,String scope,Check check)throws Exception{
  try{check.run();return consume(token,scope);}
  catch(Exception error){try{cancel(token);}catch(IOException cleanup){error.addSuppressed(cleanup);}throw error;}
 }
 public synchronized Path consume(String token,String scope)throws IOException{
  Pending value=pending;
  if(value==null||!value.token().equals(token))throw new IOException("복원 미리보기가 만료되었습니다. ZIP을 다시 선택하십시오.");
  pending=null;
  try{if(!value.scope().equals(scope)||!SourceBackup.hash(value.file()).equals(value.hash()))throw new IOException("복원 미리보기가 변경되었습니다. ZIP을 다시 선택하십시오.");return value.file();}
  catch(IOException e){Files.deleteIfExists(value.file());throw e;}
 }
 /** Late failures may revoke their own preview, never a newer request. */
 public synchronized void cancel(String token)throws IOException{if(pending!=null&&pending.token().equals(token))cancel();}
 public synchronized void cancel()throws IOException{Pending old=pending;pending=null;if(old!=null)Files.deleteIfExists(old.file());}
}
