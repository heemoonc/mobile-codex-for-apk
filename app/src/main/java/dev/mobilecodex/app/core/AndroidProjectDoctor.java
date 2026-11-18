package dev.mobilecodex.app.core;
import org.json.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;
/** Bounded, conservative literal Gradle diagnostics. Never executes project build scripts. */
public final class AndroidProjectDoctor {
    private AndroidProjectDoctor() {
    }
    private static final int MAX=512*1024;
    private record Library(String id,String name,String category,String coordinate,int min,int compile,String... plugins){
    }
    private static final List<Library> LIBRARIES=List.of(
    new Library("okhttp","OkHttp","network","com.squareup.okhttp3:okhttp:4.12.0",21,35),
    new Library("retrofit","Retrofit","network","com.squareup.retrofit2:retrofit:2.11.0",21,35),
    new Library("coroutines","Coroutines","async","org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2",21,35,"org.jetbrains.kotlin.android"),
    new Library("datastore","DataStore Preferences","storage","androidx.datastore:datastore-preferences:1.1.7",21,35,"org.jetbrains.kotlin.android"),
    new Library("lifecycle","Lifecycle ViewModel","lifecycle","androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1",23,35,"org.jetbrains.kotlin.android"),
    new Library("room","Room","storage","androidx.room:room-runtime:2.7.2",23,35,"org.jetbrains.kotlin.android","com.google.devtools.ksp"),
    new Library("workmanager","WorkManager","background","androidx.work:work-runtime-ktx:2.10.2",23,35,"org.jetbrains.kotlin.android"),
    new Library("navigation","Navigation Compose","ui","androidx.navigation:navigation-compose:2.9.1",23,35,"org.jetbrains.kotlin.android","org.jetbrains.kotlin.plugin.compose"),
    new Library("coil","Coil Compose","images","io.coil-kt.coil3:coil-compose:3.2.0",21,35,"org.jetbrains.kotlin.android","org.jetbrains.kotlin.plugin.compose")
    );
    private static Path safe(Path root,String rel)throws IOException {
        Path p=root.resolve(rel).normalize();
        if(!p.startsWith(root))throw new IOException("프로젝트 외부 경로입니다.");
        for(Path q=p;q!=null&&q.startsWith(root);q=q.getParent())if(Files.isSymbolicLink(q))throw new IOException("링크 파일은 지원하지 않습니다.");
        return p;
    }
    private static Path root(Path input)throws IOException {
        Path p=input.toAbsolutePath().normalize();
        for(Path q=p;q!=null;q=q.getParent())if(Files.isSymbolicLink(q))throw new IOException("링크 프로젝트는 지원하지 않습니다.");
        if(!Files.isDirectory(p))throw new IOException("프로젝트 폴더가 없습니다.");
        return p.toRealPath();
    }
    private static String read(Path root,String rel)throws IOException {
        Path p=safe(root,rel);
        if(!Files.exists(p,LinkOption.NOFOLLOW_LINKS))return "";
        if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||Files.size(p)>MAX)throw new IOException("파일 형식 또는 크기가 지원 범위를 벗어났습니다: "+rel);
        byte[] bytes=Files.readAllBytes(p);
        if(bytes.length>MAX)throw new IOException("파일 크기 상한 초과");
        return new String(bytes,StandardCharsets.UTF_8);
    }
    /** Preserve string literals and offsets, blank comments. */
    private static String clean(String s){
        StringBuilder b=new StringBuilder(s);
        char quote=0;
        boolean line=false,block=false,escape=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i),n=i+1<s.length()?s.charAt(i+1):0;
            if(line){
                if(c=='\n')line=false;
                else b.setCharAt(i,' ');
                continue;
            }
            if(block){
                if(c=='*'&&n=='/'){
                    b.setCharAt(i,' ');
                    b.setCharAt(++i,' ');
                    block=false;
                }
                else if(c!='\n')b.setCharAt(i,' ');
                continue;
            }
            if(quote!=0){
                if(escape){
                    escape=false;
                    continue;
                }
                if(c=='\\'){
                    escape=true;
                    continue;
                }
                if(c==quote)quote=0;
                continue;
            }
            if(c=='\''||c=='"'){
                quote=c;
                continue;
            }
            if(c=='/'&&n=='/'){
                b.setCharAt(i,' ');
                b.setCharAt(++i,' ');
                line=true;
            }
            else if(c=='/'&&n=='*'){
                b.setCharAt(i,' ');
                b.setCharAt(++i,' ');
                block=true;
            }
        }
        return b.toString();
    }
    private static String code(String s){
        StringBuilder b=new StringBuilder(s);
        char q=0;
        boolean escape=false;
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(q!=0){
                if(c!='\n')b.setCharAt(i,' ');
                if(escape)escape=false;
                else if(c=='\\')escape=true;
                else if(c==q)q=0;
            }
            else if(c=='\''||c=='"'){
                q=c;
                b.setCharAt(i,' ');
            }
        }
        return b.toString();
    }
    private static boolean lexicallyValid(String text) {
        char quote = 0;
        boolean escaped = false, line = false, block = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i), next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (line) {
                if (c == '\n') line = false;
                continue;
            }
            if (block) {
                if (c == '*' && next == '/') {
                    block = false;
                    i++;
                }
                continue;
            }
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"') quote = c;
            else if (c == '/' && next == '/') {
                line = true;
                i++;
            }
            else if (c == '/' && next == '*') {
                block = true;
                i++;
            }
        }
        return quote == 0 && !block;
    }
    /** Small structural scanner: only unqualified top-level Android/defaultConfig literals qualify. */
    private static final class Structure {
        final String masked;
        final List<String> scopes = new ArrayList<>();
        final List<Integer> opens = new ArrayList<>();
        boolean valid = true;
        Structure(String text) {
            masked = code(clean(text));
            Deque<String> stack = new ArrayDeque<>();
            Deque<Character> delimiters = new ArrayDeque<>();
            for (int i = 0; i < masked.length(); i++) {
                char c = masked.charAt(i);
                if (c == '(' || c == '[' || c == '{') delimiters.addLast(c);
                else if (c == ')' || c == ']' || c == '}') {
                    char expected = c == ')' ? '(' : c == ']' ? '[' : '{';
                    if (delimiters.isEmpty() || delimiters.removeLast() != expected) valid = false;
                }
                if (c == '{') {
                    int end = i;
                    while (end > 0 && Character.isWhitespace(masked.charAt(end - 1))) end--;
                    int begin = end;
                    while (begin > 0 && Character.isJavaIdentifierPart(masked.charAt(begin - 1))) begin--;
                    String name = masked.substring(begin, end);
                    stack.addLast(name);
                    opens.add(i);
                }
                else if (c == '}') {
                    if (stack.isEmpty()) valid = false;
                    else stack.removeLast();
                }
                scopes.add(String.join("/", stack));
            }
            if (!stack.isEmpty() || !delimiters.isEmpty()) valid = false;
            if (!lexicallyValid(text)) valid = false;
        }
        boolean inScope(int offset, String scope) {
            return valid && offset >= 0 && offset < scopes.size() && scopes.get(offset).equals(scope);
        }
    }
    private static Matcher sdkMatch(String text, String key) {
        return Pattern.compile("(?m)(?<![\\w.])" + key
        + "(?:Version)?[ \t]*(?:=[ \t]*|\\([ \t]*)?([0-9]+)(?=[ \t]*(?:[;})]|$))")
        .matcher(code(clean(text)));
    }
    private static Object sdk(String text, String key) {
        Structure structure = new Structure(text);
        Matcher matcher = sdkMatch(text, key);
        Object result = JSONObject.NULL;
        int count = 0;
        while (matcher.find()) {
            count++;
            String scope = key.equals("compileSdk") ? "android" : "android/defaultConfig";
            if (!structure.inScope(matcher.start(), scope)) return JSONObject.NULL;
            try {
                int value = Integer.parseInt(matcher.group(1));
                if (value < 1 || value > 100) return JSONObject.NULL;
                result = value;
            }
            catch (NumberFormatException invalid) {
                return JSONObject.NULL;
            }
        }
        Matcher declarations = Pattern.compile("(?<![\\w.])" + key + "(?:Version)?\\b").matcher(structure.masked);
        int total = 0;
        while (declarations.find()) total++;
        return count == 1 && total == 1 ? result : JSONObject.NULL;
    }
    private static String module(Path r)throws IOException {
        List<String> found=new ArrayList<>();
        for(String p:List.of("app/build.gradle","app/build.gradle.kts"))if(Files.exists(safe(r,p),LinkOption.NOFOLLOW_LINKS))found.add(p);
        if(found.size()==1)return found.get(0);
        if(found.size()>1)throw new IOException("app 빌드 파일이 중복됩니다.");
        for(String p:List.of("build.gradle","build.gradle.kts")){
            String s=clean(read(r,p));
            if(s.contains("com.android.application"))found.add(p);
        }
        if(found.size()!=1)throw new IOException("app 또는 단일 Android application 모듈만 지원합니다.");
        return found.get(0);
    }
    private static List<String> inputs(String module){
        return new ArrayList<>(new LinkedHashSet<>(List.of(module,"build.gradle","build.gradle.kts","settings.gradle","settings.gradle.kts","gradle.properties","gradle/libs.versions.toml","gradle/wrapper/gradle-wrapper.properties")));
    }
    private static String fingerprint(Path r,String module)throws IOException {
        try{
            MessageDigest d=MessageDigest.getInstance("SHA-256");
            for(String p:inputs(module)){
                d.update(p.getBytes(StandardCharsets.UTF_8));
                d.update((byte)0);
                d.update(read(r,p).getBytes(StandardCharsets.UTF_8));
                d.update((byte)0);
            }
            StringBuilder hex=new StringBuilder();
            for(byte value:d.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            return hex.toString();
        }
        catch(java.security.NoSuchAlgorithmException e){
            throw new IOException(e);
        }
    }
    private static void issue(JSONArray issues,String severity,String code,String message)throws JSONException {
        issues.put(new JSONObject().put("severity",severity).put("code",code).put("message",message));
    }
    private static String version(String text,String plugin){
        Matcher m=Pattern.compile("[\"']"+Pattern.quote(plugin)+"[\"']\\s*\\)?\\s*version\\s*(?:\\(\\s*)?[\"']([^\"']+)[\"']").matcher(text);
        return m.find()?m.group(1):"unknown";
    }
    private static boolean plugin(String s,String id){
        return Pattern.compile("\\bid\\s*(?:\\(\\s*)?[\"']"+Pattern.quote(id)+"[\"']").matcher(s).find()||Pattern.compile("apply\\s+plugin\\s*:\\s*[\"']"+Pattern.quote(id)+"[\"']").matcher(s).find();
    }
    private static void checkDependencySdk(JSONObject sdk, JSONArray dependencies, JSONArray issues) throws JSONException {
        for (int i=0; i<dependencies.length(); i++) {
            JSONObject dependency=dependencies.getJSONObject(i);
            String coordinate=dependency.getString("coordinate");
            for (Library library:LIBRARIES) {
                if (!coordinate.equals(library.coordinate)) continue;
                if (!sdk.isNull("min") && sdk.getInt("min")<library.min)
                    issue(issues,"error","dependency-min-sdk",library.name+" 고정 버전의 minSdk 기준은 "+library.min+"입니다.");
                if (!sdk.isNull("compile") && sdk.getInt("compile")<library.compile)
                    issue(issues,"error","dependency-compile-profile",library.name+"의 보수적 검증 프로필은 compileSdk "+library.compile+" 이상입니다. 실제 라이브러리 최소 요구값을 뜻하지 않습니다.");
            }
        }
    }
    private static void checkKotlinProfile(String version, Library library, JSONArray issues) throws JSONException {
        if (!Arrays.asList(library.plugins).contains("org.jetbrains.kotlin.android")) return;
        // A bounded automation policy, not a promise about every compiler/library combination.
        if (!version.equals("2.2.0"))
            issue(issues,"error","kotlin-profile",library.name+" 자동 추가는 검증 프로필의 명시된 Kotlin 2.2.0만 허용합니다. 현재 "+version+"은 호환성을 수동 확인하십시오. 도구 버전은 자동 변경하지 않습니다.");
    }
    public static JSONObject inspect(Path input)throws IOException,JSONException {
        Path r=root(input);
        String mod=module(r),s=clean(read(r,mod)),all=s+"\n"+clean(read(r,"build.gradle"))+"\n"+clean(read(r,"build.gradle.kts"));
        JSONArray issues=new JSONArray(),deps=new JSONArray(),catalog=new JSONArray();
        JSONObject sdk=new JSONObject().put("min",sdk(read(r,mod),"minSdk")).put("target",sdk(read(r,mod),"targetSdk")).put("compile",sdk(read(r,mod),"compileSdk"));
        for(String key:List.of("min","target","compile"))if(sdk.isNull(key))issue(issues,"warning","sdk-unknown","SDK "+key+" 값을 리터럴로 확인할 수 없습니다.");
        if(!sdk.isNull("min")&&!sdk.isNull("target")&&!sdk.isNull("compile")&&(sdk.getInt("min")>sdk.getInt("target")||sdk.getInt("target")>sdk.getInt("compile")))issue(issues,"error","sdk-order","minSdk ≤ targetSdk ≤ compileSdk 조건을 확인하십시오.");
        Matcher dep=Pattern.compile("\\b(implementation|api|ksp|kapt|testImplementation|androidTestImplementation)\\s*(?:\\(\\s*)?[\"']([a-zA-Z0-9_.-]+:[a-zA-Z0-9_.-]+:[^\"']+)[\"']").matcher(s);
        while(dep.find())deps.put(new JSONObject().put("configuration",dep.group(1)).put("coordinate",dep.group(2)).put("dynamic",dep.group(2).matches(".*[+$\\[\\](].*")||dep.group(2).contains("latest.")));
        checkDependencySdk(sdk,deps,issues);
        for (int i=0; i<deps.length(); i++) {
            for (Library library:LIBRARIES) {
                if (!deps.getJSONObject(i).getString("coordinate").equals(library.coordinate)) continue;
                JSONArray kotlinIssues=new JSONArray();
                checkKotlinProfile(version(all,"org.jetbrains.kotlin.android"),library,kotlinIssues);
                for (int j=0; j<kotlinIssues.length(); j++)
                    issues.put(kotlinIssues.getJSONObject(j).put("severity","warning"));
            }
        }
        if(s.contains("libs.")||s.contains("alias("))issue(issues,"warning","catalog-unknown","버전 카탈로그·별칭은 자동 해석하지 않습니다.");
        for(Library l:LIBRARIES)catalog.put(new JSONObject().put("id",l.id).put("name",l.name).put("category",l.category).put("version",l.coordinate.substring(l.coordinate.lastIndexOf(':')+1)).put("coordinates",new JSONArray().put(l.coordinate)).put("minSdk",l.min).put("minCompileSdk",l.compile).put("compileSdkMeaning","conservative-verification-profile").put("kotlinPolicy","explicit-2.2.0-verification-profile-for-kotlin-libraries").put("requires",new JSONArray(Arrays.asList(l.plugins))));
        String wrapper=read(r,"gradle/wrapper/gradle-wrapper.properties");
        Matcher gm=Pattern.compile("gradle-([0-9.]+)-(?:bin|all)\\.zip").matcher(wrapper);
        return new JSONObject().put("modulePath",mod).put("sdk",sdk).put("tools",new JSONObject().put("agp",version(all,"com.android.application")).put("kotlin",version(all,"org.jetbrains.kotlin.android")).put("gradle",gm.find()?gm.group(1):"unknown")).put("dependencies",deps).put("issues",issues).put("limitations",new JSONArray().put("정적 리터럴 검사입니다. 실제 의존성 해석·빌드·OS 실행 검증이 아닙니다.").put("카탈로그·변수·조건부 DSL·다중 모듈은 수동 확인이 필요합니다.").put("카탈로그 버전은 기존 probe 기준 고정값이며 최신 또는 모든 조합 지원을 뜻하지 않습니다.")).put("catalog",catalog).put("fingerprint",fingerprint(r,mod));
    }
    public static JSONObject plan(Path input,JSONObject request)throws IOException,JSONException {
        Path r=root(input);
        JSONObject info=inspect(r);
        String mod=info.getString("modulePath"),before=read(r,mod),s=clean(before),after=before;
        JSONArray issues=new JSONArray(),changes=new JSONArray();
        JSONObject sdk=info.getJSONObject("sdk");
        if (!new Structure(before).valid) issue(issues,"error","dsl-malformed","닫히지 않은 DSL·주석·문자열을 수정하십시오.");
        if (!plugin(s,"com.android.application")) issue(issues,"error","module-unknown","명시적인 application 모듈 플러그인이 필요합니다.");
        String settings = clean(read(r,"settings.gradle")) + "\n" + clean(read(r,"settings.gradle.kts"));
        if (settings.contains("includeBuild") || settings.contains("versionCatalogs") || settings.contains("projectDir")) {
            issue(issues,"error","settings-unknown","사용자 정의 설정·외부 빌드는 수동 확인이 필요합니다.");
        }
        Matcher includes = Pattern.compile("(?m)^\\s*include\\b([^\\n]*)").matcher(settings);
        while (includes.find()) {
            String declaration = includes.group(1);
            Matcher names = Pattern.compile("[\"']([^\"']+)[\"']").matcher(declaration);
            int modules = 0;
            boolean onlyApp = true;
            while (names.find()) {
                modules++;
                if (!names.group(1).equals(":app")) onlyApp = false;
            }
            if (modules != 1 || !onlyApp)
                issue(issues,"error","modules-unknown","app 외 모듈 또는 동적 include 선언은 수동 확인이 필요합니다.");
        }
        if(request==null||request.length()!=1||(!request.has("libraryId")&&!request.has("sdk")))issue(issues,"error","request","라이브러리 또는 SDK 변경 하나를 선택하십시오.");
        else if(request.has("libraryId")){
            Library l=LIBRARIES.stream().filter(x->x.id.equals(request.optString("libraryId"))).findFirst().orElse(null);
            if(l==null)issue(issues,"error","library-unknown","등록되지 않은 라이브러리입니다.");
            else {
                if(sdk.isNull("min")||sdk.isNull("compile"))issue(issues,"error","sdk-unknown","SDK 값을 확인한 뒤 추가하십시오.");
                else if(sdk.getInt("min")<l.min||sdk.getInt("compile")<l.compile)issue(issues,"error","sdk-incompatible","이 고정 버전의 검증 기준은 minSdk "+l.min+", compileSdk "+l.compile+" 이상입니다. SDK를 자동 변경하지 않습니다.");
                String allPlugins=s+"\n"+clean(read(r,"build.gradle"))+"\n"+clean(read(r,"build.gradle.kts"));
                checkKotlinProfile(version(allPlugins,"org.jetbrains.kotlin.android"),l,issues);
                if (l.id.equals("room") && Pattern.compile("androidx\\.room:room-(?:runtime|compiler|ktx):").matcher(s).find())
                    issue(issues,"error","room-family-declared","Room runtime·compiler·ktx 선언이 이미 있습니다. 구성 요소와 버전을 함께 수동 검토하십시오.");
                for(String p:l.plugins)if(!plugin(s,p))issue(issues,"error","plugin-required","필수 모듈 플러그인: "+p);
                if(s.contains("libs.")||s.contains("alias("))issue(issues,"error","catalog-unknown","카탈로그 기반 프로젝트는 수동 의존성 확인이 필요합니다.");
                String artifact=l.coordinate.substring(0,l.coordinate.lastIndexOf(':'))+":";
                if(s.contains(artifact))issue(issues,"error","already-declared","같은 라이브러리가 선언되어 있습니다. 버전 변경은 수동 검토하십시오.");
                Matcher block=Pattern.compile("(?m)^\\s*dependencies\\s*\\{").matcher(code(s));
                if(!block.find())issue(issues,"error","dependencies-unknown","독립 dependencies 블록을 찾을 수 없습니다.");
                else {
                    int at=block.end();
                    if (!new Structure(before).inScope(at-1,"dependencies")) issue(issues,"error","dependencies-scope","최상위 dependencies 블록만 지원합니다.");
                    if(block.find())issue(issues,"error","dependencies-ambiguous","여러 dependencies 블록은 자동 수정하지 않습니다.");
                    else {
                        String line=mod.endsWith(".kts")?"\n    implementation(\""+l.coordinate+"\")":"\n    implementation '"+l.coordinate+"'";
                        if(l.id.equals("room"))line+=mod.endsWith(".kts")?"\n    ksp(\"androidx.room:room-compiler:2.7.2\")":"\n    ksp 'androidx.room:room-compiler:2.7.2'";
                        after=before.substring(0,at)+line+before.substring(at);
                    }
                }
            }
        }
        else {
            JSONObject wanted=request.optJSONObject("sdk");
            if(wanted==null||wanted.length()==0)issue(issues,"error","sdk-request","SDK 변경 값이 없습니다.");
            else {
                JSONObject merged=new JSONObject(sdk.toString());
                for(java.util.Iterator<String> keys=wanted.keys();keys.hasNext();){
                    String key=keys.next();
                    Object value=wanted.get(key);
                    if(!List.of("min","target","compile").contains(key)||!(value instanceof Integer)||((Integer)value)<21||((Integer)value)>100){
                        issue(issues,"error","sdk-value","SDK는 지원 키와 21~100 정수만 허용합니다.");
                        continue;
                    }
                    if (sdk.isNull(key)) {
                        issue(issues,"error","sdk-unknown","SDK 선언 범위를 확인할 수 없습니다.");
                        continue;
                    }
                    merged.put(key,value);
                    Matcher m=sdkMatch(clean(after),key+"Sdk");
                    if(!m.find()){
                        issue(issues,"error","sdk-unknown","리터럴 SDK 선언만 수정할 수 있습니다.");
                        continue;
                    }
                    int start=m.start(1),end=m.end(1);
                    if(m.find()){
                        issue(issues,"error","sdk-ambiguous","SDK 선언이 중복됩니다.");
                        continue;
                    }
                    after=after.substring(0,start)+value+after.substring(end);
                }
                checkDependencySdk(merged,info.getJSONArray("dependencies"),issues);
                if(merged.isNull("min")||merged.isNull("target")||merged.isNull("compile")||merged.optInt("min")>merged.optInt("target")||merged.optInt("target")>merged.optInt("compile"))issue(issues,"error","sdk-order","알려진 minSdk ≤ targetSdk ≤ compileSdk 값이 필요합니다.");
            }
        }
        boolean blocked=issues.length()>0;
        if(!blocked&&!after.equals(before))changes.put(new JSONObject().put("path",mod).put("before",before).put("after",after));
        return new JSONObject().put("fingerprint",info.getString("fingerprint")).put("changes",changes).put("issues",issues).put("canApply",!blocked&&changes.length()>0).put("summary",blocked?"호환 조건을 먼저 해결하십시오.":"빌드 파일 변경 미리보기입니다. 실제 빌드 검증은 별도입니다.");
    }
    public static synchronized JSONObject apply(Path input,JSONObject request,String expectedFingerprint,Path backupRoot)throws IOException,JSONException {
        Path r=root(input);
        JSONObject plan=plan(r,request);
        if(!plan.getString("fingerprint").equals(expectedFingerprint))throw new IOException("미리보기 이후 프로젝트가 변경되었습니다. 다시 진단하십시오.");
        if(!plan.getBoolean("canApply"))throw new IOException("적용할 수 없는 변경입니다.");
        Path b=backupRoot.toAbsolutePath().normalize();
        if(b.startsWith(r))throw new IOException("백업은 프로젝트 외부에 저장해야 합니다.");
        for(Path p=b;p!=null;p=p.getParent())if(Files.isSymbolicLink(p))throw new IOException("백업 경로가 링크입니다.");
        Files.createDirectories(b);
        b=b.toRealPath();
        if(b.startsWith(r))throw new IOException("백업 경로가 프로젝트 내부입니다.");
        JSONObject change=plan.getJSONArray("changes").getJSONObject(0);
        Path target=safe(r,change.getString("path"));
        String before=change.getString("before"),after=change.getString("after");
        Path backup=Files.createTempDirectory(b,"android-edit-");
        Files.write(backup.resolve("build.before"),before.getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE_NEW);
        if(!fingerprint(r,change.getString("path")).equals(expectedFingerprint)||!read(r,change.getString("path")).equals(before))throw new IOException("적용 직전 파일이 변경되었습니다.");
        // Lock cooperating writers; retain original in backup. Recheck immediately before replacement.
        Path temp=Files.createTempFile(target.getParent(),".android-edit-",".tmp");
        try{
            Files.write(temp,after.getBytes(StandardCharsets.UTF_8));
            if(!read(r,change.getString("path")).equals(before))throw new IOException("동시 편집을 감지했습니다.");
            safe(r,change.getString("path"));
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }
        finally{
            Files.deleteIfExists(temp);
        }
        return inspect(r).put("applied",true).put("backupId",backup.getFileName().toString());
    }
}
