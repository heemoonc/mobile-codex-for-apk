package dev.mobilecodex.app;
import dev.mobilecodex.app.core.LinuxGitSetup;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class LinuxGitSetupTest {
 private static void write(Path path,String text) throws java.io.IOException {Files.write(path,text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 private void run(boolean working,int packageExit) throws Exception {
  Path dir=Files.createTempDirectory("git-setup"),bin=Files.createDirectory(dir.resolve("bin")),ready=dir.resolve("ready"),log=dir.resolve("packages");
  write(bin.resolve("git"),"#!/bin/sh\n[ -f \"$MC_TEST_READY\" ] || exit 1\necho 'git version fixture'\n");bin.resolve("git").toFile().setExecutable(true);
  write(bin.resolve("pacman"),"#!/bin/sh\nprintf '%s\\n' \"$*\" > \"$MC_TEST_PACKAGES\"\n"+(packageExit==0?"touch \"$MC_TEST_READY\"\n":"exit "+packageExit+"\n"));bin.resolve("pacman").toFile().setExecutable(true);
  write(bin.resolve("pacman-key"),"#!/bin/sh\necho keyring \"$*\"\n");bin.resolve("pacman-key").toFile().setExecutable(true);
  if(working)Files.createFile(ready);
  ProcessBuilder b=new ProcessBuilder("/bin/sh","-c",LinuxGitSetup.command()).redirectErrorStream(true);b.environment().put("PATH",bin+":/usr/bin:/bin");b.environment().put("MC_TEST_READY",ready.toString());b.environment().put("MC_TEST_PACKAGES",log.toString());Process p=b.start();String output=new String(p.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);int exit=p.waitFor();
  if(working){assertEquals(0,exit);assertFalse(Files.exists(log));assertTrue(LinuxGitSetup.passed(exit,output));}
  else if(packageExit==0){assertEquals(0,exit);assertEquals("-Syyu --noconfirm --needed git ca-certificates\n",new String(Files.readAllBytes(log),java.nio.charset.StandardCharsets.UTF_8));assertTrue(LinuxGitSetup.passed(exit,output));}
  else {assertNotEquals(0,exit);assertFalse(LinuxGitSetup.passed(exit,output));assertFalse(output.contains("MC_LINUX_GIT=passed"));}
 }
 @Test public void workingGitSkipsPackages() throws Exception {run(true,0);}
 @Test public void missingGitRefreshesPackagesAndVerifiesExecution() throws Exception {run(false,0);}
 @Test public void failedPackageInstallNeverClaimsSuccess() throws Exception {run(false,42);}
 @Test public void evidenceMustHaveOneExactMarker(){assertFalse(LinuxGitSetup.passed(0,"MC_LINUX_GIT=passed\nMC_LINUX_GIT=passed\n"));assertFalse(LinuxGitSetup.passed(1,"MC_LINUX_GIT=passed\n"));}
}
