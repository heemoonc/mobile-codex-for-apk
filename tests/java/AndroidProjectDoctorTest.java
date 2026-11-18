import dev.mobilecodex.app.core.AndroidProjectDoctor;
import java.nio.file.*;
import org.json.*;
public class AndroidProjectDoctorTest {
    static void check(boolean value){
        if(!value)throw new AssertionError();
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("doctor"), backup=Files.createTempDirectory("doctor-backup");
        Files.createDirectories(root.resolve("app"));
        Path build=root.resolve("app/build.gradle");
        String source="plugins { id 'com.android.application' }\nandroid { compileSdk 35\n defaultConfig { minSdk 29; targetSdk 35 } }\ndependencies {\n}\n// minSdk 1\n";
        Files.writeString(build,Files.readString(Path.of("app/src/main/assets/android-sample/app/build.gradle")));
        if(!AndroidProjectDoctor.plan(root,new JSONObject().put("libraryId","okhttp")).getBoolean("canApply"))
            throw new AssertionError("Fresh Java template must support library preview");
        Files.writeString(build,source);
        JSONObject inspected=AndroidProjectDoctor.inspect(root);
        check(inspected.getJSONObject("sdk").getInt("min")==29);
        JSONObject req=new JSONObject().put("libraryId","okhttp"), plan=AndroidProjectDoctor.plan(root,req);
        check(plan.getBoolean("canApply"));
        Files.writeString(build,source+"// concurrent\n");
        try{
            AndroidProjectDoctor.apply(root,req,plan.getString("fingerprint"),backup);
            throw new AssertionError();
        }
        catch(java.io.IOException expected){
        }
        plan=AndroidProjectDoctor.plan(root,req);
        JSONObject result=AndroidProjectDoctor.apply(root,req,plan.getString("fingerprint"),backup);
        check(result.getBoolean("applied"));
        check(Files.readString(build).contains("okhttp:4.12.0"));
        check(!AndroidProjectDoctor.plan(root,new JSONObject().put("libraryId","room")).getBoolean("canApply"));
        Files.writeString(build,source.replace("minSdk 29","minSdk versions.minimum"));
        check(!AndroidProjectDoctor.plan(root,req).getBoolean("canApply"));
        Files.writeString(build,source);
        JSONObject sdk=AndroidProjectDoctor.plan(root,new JSONObject().put("sdk",new JSONObject().put("min",36)));
        check(!sdk.getBoolean("canApply"));
        Files.writeString(build,source.replace("android {", "android { /* unclosed"));
        check(!AndroidProjectDoctor.plan(root,req).getBoolean("canApply"));
        Files.writeString(build,"plugins { id 'com.android.application' }\nsubprojects { android { compileSdk 35; defaultConfig { minSdk 29; targetSdk 35 } } }\ndependencies {}\n");
        check(AndroidProjectDoctor.inspect(root).getJSONObject("sdk").isNull("min"));
        Files.writeString(build,source.replace("minSdk 29", "minSdk 999999999999999999999"));
        check(AndroidProjectDoctor.inspect(root).getJSONObject("sdk").isNull("min"));
        Files.writeString(build,source.replace("minSdk 29", "minSdk 29; minSdk project.minimum"));
        check(AndroidProjectDoctor.inspect(root).getJSONObject("sdk").isNull("min"));
        Files.writeString(build,source);
        Files.createDirectories(root.resolve("gradle"));
        Files.writeString(root.resolve("gradle/libs.versions.toml"),"[versions]\nexample = '1'\n");
        plan=AndroidProjectDoctor.plan(root,req);
        Files.writeString(root.resolve("gradle/libs.versions.toml"),"[versions]\nexample = '2'\n");
        try {
            AndroidProjectDoctor.apply(root,req,plan.getString("fingerprint"),backup);
            throw new AssertionError();
        }
        catch(java.io.IOException expected) {
        }
        String roomSource=source.replace("minSdk 29","minSdk 23").replace("dependencies {", "dependencies { implementation 'androidx.room:room-runtime:2.7.2';");
        Files.writeString(build,roomSource);
        check(!AndroidProjectDoctor.plan(root,new JSONObject().put("sdk",new JSONObject().put("min",21))).getBoolean("canApply"));
        check(!AndroidProjectDoctor.plan(root,new JSONObject().put("sdk",new JSONObject().put("target",34).put("compile",34))).getBoolean("canApply"));
        Files.writeString(build,roomSource.replace("minSdk 23","minSdk 21"));
        check(AndroidProjectDoctor.inspect(root).getJSONArray("issues").toString().contains("dependency-min-sdk"));
        Files.writeString(build,source.replace("id 'com.android.application'", "id 'com.android.application'; id 'org.jetbrains.kotlin.android' version '1.9.24'"));
        check(!AndroidProjectDoctor.plan(root,new JSONObject().put("libraryId","coroutines")).getBoolean("canApply"));
        Files.writeString(build,source.replace("dependencies {", "dependencies { ksp 'androidx.room:room-compiler:2.8.0';"));
        check(!AndroidProjectDoctor.plan(root,new JSONObject().put("libraryId","room")).getBoolean("canApply"));
        Files.writeString(build,source.replace("id 'com.android.application'", "id 'com.android.application'; id 'org.jetbrains.kotlin.android' version '2.2.0'"));
        check(AndroidProjectDoctor.plan(root,new JSONObject().put("libraryId","coroutines")).getBoolean("canApply"));
        Files.writeString(build,source.replace("id 'com.android.application'", "id 'com.android.application'; id 'org.jetbrains.kotlin.android' version '2.3.0'"));
        check(!AndroidProjectDoctor.plan(root,new JSONObject().put("libraryId","coroutines")).getBoolean("canApply"));
        Files.delete(build);
        Files.createSymbolicLink(build,backup.resolve("outside"));
        try{
            AndroidProjectDoctor.inspect(root);
            throw new AssertionError();
        }
        catch(java.io.IOException expected){
        }
        System.out.println("PASS doctor safety and planning");
    }
}
