package dev.mobilecodex.app.core;

import static dev.mobilecodex.app.core.Texts.t;
import org.json.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.regex.*;
import static dev.mobilecodex.app.core.Json.*;

/** Public GitHub release metadata. Downloaded APK identity is independently checked before installation. */
public final class UpdateCatalog {
    private static final Pattern VERSION = Pattern.compile("^(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})(?:-(alpha|beta|rc)(?:[.-]?(\\d{1,9}))?)?(?:\\+[A-Za-z0-9.-]+)?$");
    private static final Pattern APK = Pattern.compile("^mobile-codex-(.+)-arm64\\.apk$");
    private static final Pattern FORK_VERSION = Pattern.compile("^(\\d{1,9})\\.(\\d{1,9})\\.(\\d{1,9})-apk-dev(\\d{1,9})$");
    private static final Pattern FORK_APK = Pattern.compile("^Mobile-Codex-for-APK-(.+)-arm64\\.apk$");
    public static String repository(String value) throws IOException {
        String repo = value.trim();
        if (!repo.matches("[A-Za-z0-9][A-Za-z0-9-]{0,38}/[A-Za-z0-9_.-]{1,100}") || repo.endsWith("/.") || repo.endsWith("/..")) throw new IOException(t("GitHub 저장소를 소유자/저장소 형식으로 입력해 주세요."));
        return repo;
    }
    private static long[] version(String value) throws IOException {
        Matcher fork = FORK_VERSION.matcher(value);
        if (fork.matches()) return new long[]{Long.parseLong(fork.group(1)), Long.parseLong(fork.group(2)), Long.parseLong(fork.group(3)), -1, Long.parseLong(fork.group(4))};
        Matcher m = VERSION.matcher(value);
        if (!m.matches()) throw new IOException(t("지원하지 않는 버전 형식입니다: ") + value);
        int rank = m.group(4) == null ? 3 : List.of("alpha", "beta", "rc").indexOf(m.group(4));
        return new long[]{Long.parseLong(m.group(1)), Long.parseLong(m.group(2)), Long.parseLong(m.group(3)), rank, m.group(5) == null ? 0 : Long.parseLong(m.group(5))};
    }
    public static int compare(String left, String right) throws IOException {
        long[] a = version(left), b = version(right);
        for (int i=0;i<a.length;i++) { int result=Long.compare(a[i],b[i]); if(result!=0)return result; }
        return 0;
    }
    public static boolean sameDistribution(String left, String right) throws IOException {
        version(left); version(right);
        return FORK_VERSION.matcher(left).matches() == FORK_VERSION.matcher(right).matches();
    }
    public static JSONObject latest(JSONArray releases, String repo, boolean prereleases) throws Exception {
        return latest(releases,repo,prereleases,null);
    }
    public static JSONObject latest(JSONArray releases, String repo, boolean prereleases, String installedVersion) throws Exception {
        repository(repo); if(installedVersion!=null)version(installedVersion); JSONObject best = null;
        for (int i=0;i<releases.length();i++) {
            JSONObject release=releases.getJSONObject(i);
            if(release.optBoolean("draft") || (!prereleases && release.optBoolean("prerelease")))continue;
            JSONArray assets=release.optJSONArray("assets"); if(assets==null)continue;
            for(int j=0;j<assets.length();j++) {
                JSONObject asset=assets.getJSONObject(j);String name=asset.optString("name");Matcher m=APK.matcher(name);boolean forkAsset=false;
                if(!m.matches()){m=FORK_APK.matcher(name);if(!m.matches())continue;forkAsset=true;}
                String v=m.group(1);try{version(v);}catch(IOException unsupported){continue;}
                if(forkAsset!=FORK_VERSION.matcher(v).matches() || (installedVersion!=null && !sameDistribution(installedVersion,v)))continue;
                if(!prereleases && version(v)[3]<3)continue;
                String digest=asset.optString("digest");long size=asset.optLong("size");
                if(!digest.matches("sha256:[a-fA-F0-9]{64}") || size<=0 || size>2147483647L || !asset.optString("state").equals("uploaded"))continue;
                String url=asset.getString("browser_download_url");URI uri=new URI(url);
                String prefix="/"+repo+"/releases/download/";
                if(!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || uri.getPort()!=-1 || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getQuery()!=null || !uri.getPath().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT)) || !uri.getPath().endsWith("/"+name) || Arrays.asList(uri.getPath().split("/")).contains(".."))continue;
                if(best==null || compare(v,best.getString("versionName"))>0) {
                    String notes=release.optString("body");if(notes.length()>12000)notes=notes.substring(0,12000)+"\n…";
                    best=obj("versionName",v,"name",name,"url",url,"size",size,"sha256",digest.substring(7).toLowerCase(Locale.ROOT),"tag",release.optString("tag_name"),"notes",notes,"repository",repo);
                }
            }
        }
        return best;
    }
    public static boolean compatibleSigners(Set<String> installed, Set<String> candidate, Set<String> candidateHistory) {
        if(installed.isEmpty() || candidate.isEmpty())return false;
        if(installed.equals(candidate))return true;
        return installed.size()==1 && candidate.size()==1 && candidateHistory.containsAll(installed);
    }
    public static void identity(String expectedPackage, long installedVersion, String actualPackage, long actualVersion, String expectedName, String actualName, int minSdk, int deviceSdk, boolean signersMatch) throws IOException {
        if(!expectedPackage.equals(actualPackage))throw new IOException(t("다른 앱의 APK입니다. 기존 앱은 변경하지 않았습니다."));
        if(actualVersion<=installedVersion)throw new IOException(t("현재 앱보다 새 버전의 APK가 아닙니다."));
        if(!expectedName.equals(actualName))throw new IOException(t("릴리스에 표시된 버전과 APK의 버전이 다릅니다."));
        if(minSdk>deviceSdk)throw new IOException(t("이 업데이트는 Android API ")+minSdk+t(" 이상이 필요합니다."));
        if(!signersMatch)throw new IOException(t("설치된 앱과 서명키가 다릅니다. 데이터를 유지하려면 기존 서명키로 빌드한 APK가 필요합니다. 기존 앱을 삭제하지 마세요."));
    }
}
