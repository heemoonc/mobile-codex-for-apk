package dev.mobilecodex.app;
import dev.mobilecodex.app.core.GitHubAccount;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
public class GitHubAccountTest {
 @Test public void resultNeedsZeroExitAndOneExactAccount(){assertEquals("sample-user",GitHubAccount.account(0,"MC_GITHUB_ACCOUNT=sample-user\n"));assertEquals("",GitHubAccount.account(1,"MC_GITHUB_ACCOUNT=sample-user\n"));assertEquals("",GitHubAccount.account(0,"MC_GITHUB_ACCOUNT=a\nMC_GITHUB_ACCOUNT=b\n"));assertEquals("",GitHubAccount.account(0,"MC_GITHUB_ACCOUNT=bad user\n"));}
 @Test public void deviceCodeRequiresCliPrompt(){assertEquals("ABCD-EFGH",GitHubAccount.code("! First copy your one-time code: ABCD-EFGH\n"));assertEquals("",GitHubAccount.code("unrelated ABCD-EFGH\n"));}
 @Test public void loginIsExplicitAndCheckNeverInstalls(){String login=GitHubAccount.command(true),check=GitHubAccount.command(false);assertTrue(login.contains("pacman -Syyu"));assertTrue(login.contains("gh auth login --hostname github.com --git-protocol https --web"));assertTrue(login.contains("gh auth setup-git --hostname github.com"));assertFalse(check.contains("pacman"));assertFalse(check.contains("gh auth login"));assertFalse(login.contains("auth token"));assertFalse(login.contains("--insecure-storage"));}
 @Test public void helperQuotesEveryArgumentAndPassesOnlyOperation()throws Exception{String helper=GitHubAccount.helper(List.of("/bin/sh","-lc","printf '%s' \"$0\""));Process p=new ProcessBuilder("/bin/sh","-c",helper.substring(1)+" get").start();assertEquals("get",new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));assertEquals(0,p.waitFor());}
 @Test public void fixedLoginCommandChecksActualCliResultAndConfiguresGit()throws Exception {
  for(boolean fail:new boolean[]{false,true}){
   var dir=java.nio.file.Files.createTempDirectory("github-cli-fixture");var bin=java.nio.file.Files.createDirectory(dir.resolve("bin"));
   String script="#!/bin/sh\ncase \"$1 $2\" in\n 'auth status') [ -f \"$MC_GH_READY\" ];;\n 'auth login') "+(fail?"exit 42":"echo '! First copy your one-time code: ABCD-EFGH'; touch \"$MC_GH_READY\"")+";;\n 'auth setup-git') [ -f \"$MC_GH_READY\" ];;\n 'api --hostname') echo sample-user;;\n *) exit 19;;\nesac\n";
   var gh=bin.resolve("gh");java.nio.file.Files.write(gh,script.getBytes(java.nio.charset.StandardCharsets.UTF_8));gh.toFile().setExecutable(true);
   ProcessBuilder builder=new ProcessBuilder("/bin/sh","-c",GitHubAccount.command(true)).redirectErrorStream(true);builder.environment().put("PATH",bin+":/usr/bin:/bin");builder.environment().put("MC_GH_READY",dir.resolve("ready").toString());Process process=builder.start();String output=new String(process.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);int exit=process.waitFor();assertEquals(fail?"":"sample-user",GitHubAccount.account(exit,output));assertEquals(fail?42:0,exit);
  }
 }
}
