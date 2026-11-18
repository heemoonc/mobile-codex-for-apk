package dev.mobilecodex.app;
import dev.mobilecodex.app.core.AndroidSetup;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=32,application=android.app.Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AndroidSetupTest {
 @Test public void missingFilesNeverClaimReadiness() throws Exception {Path root=Files.createTempDirectory("setup");assertFalse(AndroidSetup.inspect(root).getBoolean("filesReady"));}
 @Test public void allFilesStillNeedExecutionCheck() throws Exception {Path root=Files.createTempDirectory("setup");for(String file:AndroidSetup.FILES.values()){Path p=root.resolve(file);Files.createDirectories(p.getParent());Files.write(p,"fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8));}assertTrue(AndroidSetup.inspect(root).getBoolean("filesReady"));assertFalse(AndroidSetup.inspect(root).getBoolean("executionVerified"));}
 @Test public void checkUsesFixedPathsAndNeverRunsProjectScripts(){String s=AndroidSetup.command();assertTrue(s.contains("/opt/android/jdk"));assertTrue(s.contains("platforms/android-35/android.jar"));assertTrue(s.contains("com.android.tools.r8.D8"));assertTrue(s.contains("MC_ANDROID_SETUP=passed"));assertFalse(s.contains("gradlew"));assertFalse(s.contains("curl"));}
 @Test public void completionRequiresZeroExitAndOneExactMarker(){assertTrue(AndroidSetup.passed(0,"MC_ANDROID_SETUP=passed\n"));assertFalse(AndroidSetup.passed(1,"MC_ANDROID_SETUP=passed\n"));assertFalse(AndroidSetup.passed(0,"xMC_ANDROID_SETUP=passed\n"));assertFalse(AndroidSetup.passed(0,"MC_ANDROID_SETUP=passed\nMC_ANDROID_SETUP=passed\n"));}
}
