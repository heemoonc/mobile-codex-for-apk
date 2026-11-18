package dev.mobilecodex.app.core;
import java.nio.file.*;
import java.util.List;

/** Integration fixture: production generators and source archive, no user projects. */
public final class AndroidWorkflowHarness {
 public static void main(String[] args)throws Exception {
  Path repo=Path.of(args[0]),run=Path.of(args[1]);
  if(args[2].equals("create")) {
   for(String profile:List.of("java","kotlin","compose")) {
    AndroidProject.create(run,new AndroidProject.Config("Workflow "+profile+" 1","com.example.workflow."+profile+"fixture",profile),
     name->Files.newInputStream(repo.resolve("app/src/main/assets/android-sample").resolve(name)),profile,
     name->Files.newInputStream(repo.resolve("app/src/main/assets/android-templates").resolve(profile).resolve(name)));
   }
  } else if(args[2].equals("restore")) {
   for(String profile:List.of("java","kotlin","compose")) {
    Path zip=run.resolve(profile+".zip"),target=run.resolve(profile+"-restored");
    SourceBackup.write(run.resolve(profile),"Workflow "+profile,zip,SourceBackup.DEFAULT);
    Files.createDirectory(target);SourceBackup.extract(zip,target,SourceBackup.DEFAULT);
   }
  } else throw new IllegalArgumentException("Unknown fixture stage");
 }
}
