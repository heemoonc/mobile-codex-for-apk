package dev.mobilecodex.app.core;
import org.junit.Test;
import java.io.IOException;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;
public class GitProjectsTest {
 @Test public void githubUrlsExcludeCredentialsAndOtherHosts() throws Exception {
  assertEquals("https://github.com/owner/repo.git",GitProjects.github("https://github.com/owner/repo"));
  for(String s:List.of("https://user:secret@github.com/a/b","https://github.com/a/b?token=x","http://github.com/a/b","https://evil.test/a/b","https://github.com/a/b/tree/main","https://github.com/a/../b","git@github.com:a/b.git"))try{GitProjects.github(s);fail(s);}catch(java.io.IOException expected){}
 }
 @Test public void cloneUsesBoundedNonRecursiveArgumentArray() throws Exception {
  var args=GitProjects.cloneArguments("https://github.com/a/b",Paths.get("/tmp/clone target"));
  assertTrue(args.contains("--no-recurse-submodules"));assertTrue(args.contains("--depth=1"));assertEquals("https://github.com/a/b.git",args.get(args.size()-2));assertEquals("/tmp/clone target",args.get(args.size()-1));
 }
 @Test public void selectedCommitDoesNotIncludeOtherStagedFiles() throws Exception {
  Path p=Files.createTempDirectory("git-projects");
  GitProjects.Runner runner=(dir,args,seconds)->{var c=new ArrayList<String>(List.of("git","-c","user.name=Test","-c","user.email=test@example.invalid"));c.addAll(args);var proc=new ProcessBuilder(c).directory(dir.toFile()).redirectErrorStream(true).start();String out=new String(proc.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);if(proc.waitFor()!=0)throw new java.io.IOException(out);return out;};
  runner.run(p,List.of("init","-b","main"),15);write(p.resolve("first.txt"),"initial");runner.run(p,List.of("add","first.txt"),15);runner.run(p,List.of("commit","-m","initial"),15);
  write(p.resolve("first.txt"),"changed");write(p.resolve("other.txt"),"keep staged");runner.run(p,List.of("add","other.txt"),15);
  var git=new GitProjects(p,runner);var status=git.status();git.commit(List.of("first.txt"),"selected",status.getString("head"),"refs/heads/main");
  assertEquals("first.txt",runner.run(p,List.of("show","--pretty=format:","--name-only","HEAD"),15).trim());assertTrue(runner.run(p,List.of("diff","--cached","--name-only"),15).contains("other.txt"));
  try{git.commit(List.of("other.txt"),"stale",status.getString("head"),"refs/heads/main");fail();}catch(java.io.IOException expected){}
  for(String name:List.of("../secret","/absolute",".git/config","bad\nfile"))try{git.commit(List.of(name),"bad",git.status().getString("head"),"refs/heads/main");fail(name);}catch(java.io.IOException expected){}
 }
 @Test public void pushRequiresUnchangedHeadAndExplicitGithubRemote() throws Exception {
  Path p=Files.createTempDirectory("git-push");Files.createDirectory(p.resolve(".git"));var calls=new ArrayList<List<String>>();
  var g=new GitProjects(p,(dir,args,seconds)->{calls.add(args);return switch(args.get(0)){case "rev-parse"->args.contains("--show-toplevel")?p.toString():"a".repeat(40);case "config"->"https://github.com/a/b.git";case "symbolic-ref"->"refs/heads/main";default->"";};});
  try{g.push("b".repeat(40),"https://github.com/a/b.git","refs/heads/main");fail();}catch(java.io.IOException expected){}
  try{g.push("a".repeat(40),"https://github.com/a/other.git","refs/heads/main");fail();}catch(java.io.IOException expected){}
  assertFalse(calls.stream().anyMatch(x->x.get(0).equals("push")));g.push("a".repeat(40),"https://github.com/a/b.git","refs/heads/main");assertEquals(List.of("push","--porcelain","--","https://github.com/a/b.git","HEAD:refs/heads/main"),calls.get(calls.size()-1));
 }
 @Test public void networkRewriteConfigurationIsRejected()throws Exception {
  GitProjects.networkConfig("user.name\nTest\u0000");
  for(String config:List.of("url.https://evil.test/.insteadof\nhttps://github.com/\u0000","url.ssh://evil/.pushinsteadof\nhttps://github.com/\u0000","remote.https://github.com/a/b.git.url\nhttps://evil.test/other\u0000","remote.https://github.com/a/b.git.pushurl\nhttps://evil.test/other\u0000"))try{GitProjects.networkConfig(config);fail();}catch(java.io.IOException expected){}
 }
 @Test public void externalGitMetadataIsRejectedBeforeAnyCommand()throws Exception {
  Path p=Files.createTempDirectory("git-meta");Path meta=Files.createDirectory(p.resolve(".git"));write(meta.resolve("commondir"),"../outside");
  var g=new GitProjects(p,(dir,args,seconds)->{fail("No command should run");return "";});try{g.status();fail();}catch(java.io.IOException expected){}
 }
 private static void write(Path path,String text)throws IOException {Files.write(path,text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
}
