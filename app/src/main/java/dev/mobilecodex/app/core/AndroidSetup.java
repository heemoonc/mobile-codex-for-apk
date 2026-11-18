package dev.mobilecodex.app.core;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.json.*;
/** Device-wide setup; does not select, execute or edit a project. */
public final class AndroidSetup {
 private AndroidSetup(){}
 public static final Map<String,String> FILES=Map.of("java","opt/android/jdk/bin/java","javac","opt/android/jdk/bin/javac","platform","opt/android/sdk/platforms/android-35/android.jar","aapt2","opt/android/bin/aapt2","buildTools","opt/android/sdk/build-tools/35.0.0/lib/d8.jar");
 public static JSONObject inspect(Path root)throws JSONException {JSONObject tools=new JSONObject();boolean ready=true;for(var e:FILES.entrySet()){boolean found=Files.isRegularFile(root.resolve(e.getValue()));tools.put(e.getKey(),found);ready&=found;}return new JSONObject().put("tools",tools).put("filesReady",ready).put("executionVerified",false);}
 public static String command(){return DeveloperBuild.command(new DeveloperBuild.Config("/opt/android/jdk","/opt/android/sdk","/opt/android/bin/aapt2","assembleDebug"),false)
  +"[ -f /opt/android/sdk/platforms/android-35/android.jar ] || { echo 'SDK Platform 35 is missing'; exit 2; }\n"
  +"[ -f /opt/android/sdk/build-tools/35.0.0/lib/d8.jar ] || { echo 'Build Tools 35 is missing'; exit 2; }\n"
  +"\"$JAVA_HOME/bin/java\" -cp /opt/android/sdk/build-tools/35.0.0/lib/d8.jar com.android.tools.r8.D8 --version\n"
  +"echo MC_ANDROID_SETUP=passed\n";}
 public static boolean downloadPassed(int exit,String output){return markerPassed(exit,output,"MC_ANDROID_TOOLS=installed");}
 public static boolean passed(int exit,String output){return markerPassed(exit,output,"MC_ANDROID_SETUP=passed");}
 private static boolean markerPassed(int exit,String output,String marker){if(exit!=0)return false;var m=Pattern.compile("(?m)^"+Pattern.quote(marker)+"\\r?$").matcher(output==null?"":output);int n=0;while(m.find())n++;return n==1;}
}
