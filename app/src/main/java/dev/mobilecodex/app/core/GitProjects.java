package dev.mobilecodex.app.core;

import java.io.IOException;
import static dev.mobilecodex.app.core.Texts.t;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Explicit project-local Git operations. Credentials are never accepted in URLs. */
public final class GitProjects {
 public interface Runner { String run(Path directory,List<String> arguments,int seconds) throws Exception; }
 private final Path root; private final Runner runner;
 public GitProjects(Path root,Runner runner){this.root=root;this.runner=runner;}
 public static String github(String value)throws IOException {
  var match=Pattern.compile("^https://github\\.com/([A-Za-z0-9][A-Za-z0-9-]{0,38})/([A-Za-z0-9_.-]{1,100})/?$").matcher(value==null?"":value.trim());
  if(!match.matches())throw new IOException(t("GitHub HTTPS 저장소 주소를 입력하십시오. 인증정보가 들어간 주소는 사용할 수 없습니다."));
  String repo=match.group(2);if(repo.endsWith(".git"))repo=repo.substring(0,repo.length()-4);
  if(repo.isEmpty()||repo.equals(".")||repo.equals(".."))throw new IOException(t("GitHub 저장소 이름이 올바르지 않습니다."));
  return "https://github.com/"+match.group(1)+"/"+repo+".git";
 }
 public static void networkConfig(String config)throws IOException {
  for(String item:config.split("\u0000",-1)){String key=item.split("\n",2)[0].toLowerCase(Locale.ROOT);if((key.startsWith("url.")&&(key.endsWith(".insteadof")||key.endsWith(".pushinsteadof")))||(key.startsWith("remote.https://")&&(key.endsWith(".url")||key.endsWith(".pushurl"))))throw new IOException(t("Git 주소 재작성 설정이 있습니다. 확인한 GitHub 주소를 사용하려면 터미널에서 해당 설정을 확인하십시오."));}
 }
 public static List<String> cloneArguments(String url,Path target)throws IOException {
  return List.of("-c","http.followRedirects=false","clone","--depth=1","--no-recurse-submodules","--",github(url),target.toString());
 }
 private String run(List<String> arguments)throws Exception{return runner.run(root,arguments,30);}
 private void repository()throws Exception {
  if(!Files.isDirectory(root.resolve(".git"),LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(root.resolve(".git")))throw new IOException(t("현재 프로젝트는 Git 저장소가 아닙니다. GitHub에서 가져오거나 터미널에서 Git을 초기화하십시오."));
  if(Files.exists(root.resolve(".git/commondir"),LinkOption.NOFOLLOW_LINKS)||Files.exists(root.resolve(".git/objects/info/alternates"),LinkOption.NOFOLLOW_LINKS))throw new IOException(t("외부 Git 메타데이터를 사용하는 저장소는 지원하지 않습니다."));
  try(var paths=Files.walk(root.resolve(".git"))){if(paths.anyMatch(Files::isSymbolicLink))throw new IOException(t("외부 Git 메타데이터를 사용하는 저장소는 지원하지 않습니다."));}
  if(!Paths.get(run(List.of("rev-parse","--show-toplevel")).trim()).toRealPath().equals(root.toRealPath()))throw new IOException(t("Git 저장소와 현재 프로젝트의 경계가 다릅니다."));
 }
 private String head()throws Exception {try{return run(List.of("rev-parse","--verify","HEAD")).trim();}catch(Exception e){return "";}}
 private String origin()throws Exception {try{return github(run(List.of("config","--get","remote.origin.url")).trim());}catch(Exception e){return "";}}
 public JSONObject status()throws Exception {
  repository();String output=run(List.of("status","--porcelain=v1","-z","--untracked-files=all"));JSONArray files=new JSONArray();String[] parts=output.split("\u0000",-1);
  for(int i=0;i<parts.length;i++){String entry=parts[i];if(entry.length()<4)continue;String code=entry.substring(0,2),path=entry.substring(3);if(code.indexOf('R')>=0||code.indexOf('C')>=0){if(i+1<parts.length)i++;}
   files.put(Json.obj("path",path,"code",code,"selectable",safePath(path)&&code.indexOf('R')<0&&code.indexOf('C')<0&&code.indexOf('U')<0&&!code.equals("AA")&&!code.equals("DD")));
  }
  String branch="";try{branch=run(List.of("symbolic-ref","--quiet","HEAD")).trim();}catch(Exception ignored){}
  return Json.obj("head",head(),"origin",origin(),"branch",branch,"files",files);
 }
 private static boolean safePath(String p){return p!=null&&!p.isBlank()&&!p.startsWith("/")&&p.indexOf('\\')<0&&p.chars().noneMatch(c->c<32||c==127)&&Arrays.stream(p.split("/",-1)).noneMatch(s->s.isEmpty()||s.equals("..")||s.equals(".")||s.equals(".git"));}
 private void matchingHead(String expected)throws Exception {if(expected==null||!head().equals(expected))throw new IOException(t("Git 이력이 바뀌었습니다. 변경 목록을 새로고침하십시오."));}
 private void matchingBranch(String expected)throws Exception {String current="";try{current=run(List.of("symbolic-ref","--quiet","HEAD")).trim();}catch(Exception ignored){}if(expected==null||!current.equals(expected))throw new IOException(t("Git 브랜치가 바뀌었습니다. 변경 목록을 새로고침하십시오."));}
 public JSONObject commit(List<String> selected,String message,String expectedHead,String expectedBranch)throws Exception {
  repository();matchingHead(expectedHead);matchingBranch(expectedBranch);String text=message==null?"":message.trim();if(text.isEmpty()||text.length()>500||text.indexOf('\u0000')>=0)throw new IOException(t("커밋 메시지를 1~500자로 입력하십시오."));
  if(selected==null||selected.isEmpty()||selected.size()>500||new HashSet<>(selected).size()!=selected.size())throw new IOException(t("저장할 파일을 선택하십시오."));
  JSONArray current=status().getJSONArray("files");Set<String> allowed=new HashSet<>();for(int i=0;i<current.length();i++)if(current.getJSONObject(i).getBoolean("selectable"))allowed.add(current.getJSONObject(i).getString("path"));
  for(String path:selected)if(!safePath(path)||!allowed.contains(path))throw new IOException(t("선택할 수 없는 파일입니다. 변경 목록을 새로고침하십시오."));
  var add=new ArrayList<String>(List.of("add","--"));add.addAll(selected);run(add);
  var commit=new ArrayList<String>(List.of("commit","--only","-m",text,"--"));commit.addAll(selected);run(commit);return status();
 }
 public void push(String expectedHead,String expectedOrigin,String expectedBranch)throws Exception {
  repository();matchingHead(expectedHead);matchingBranch(expectedBranch);if(expectedHead==null||expectedHead.isBlank())throw new IOException(t("업로드할 커밋이 없습니다."));String remote=origin();if(remote.isEmpty()||!remote.equals(github(expectedOrigin)))throw new IOException(t("GitHub 원격 주소가 바뀌었습니다. 새로고침하십시오."));
  String branch=run(List.of("symbolic-ref","--quiet","HEAD")).trim();if(!branch.startsWith("refs/heads/")||!branch.matches("refs/heads/[A-Za-z0-9._/-]+")||branch.contains(".."))throw new IOException(t("업로드할 브랜치를 확인하지 못했습니다."));
  runner.run(root,List.of("push","--porcelain","--",remote,"HEAD:"+branch),180);
 }
}
