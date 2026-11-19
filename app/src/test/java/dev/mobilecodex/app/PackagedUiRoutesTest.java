package dev.mobilecodex.app;
import android.app.Application;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk=29,application=Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class PackagedUiRoutesTest {
 @Test public void everyScriptAndStyleReferencedByIndexIsActuallyServed() throws Exception {
  var assets=RuntimeEnvironment.getApplication().getAssets();
  String html=new String(assets.open("web/index.html").readAllBytes(),StandardCharsets.UTF_8);
  var matcher=Pattern.compile("(?:src|href)=\"([^\"#]+\\.(?:js|css))\"").matcher(html);int count=0;
  while(matcher.find()){String path=matcher.group(1);WebResourceResponse response=MainActivity.packagedUiResponse(assets,Uri.parse("https://appassets.androidplatform.net/"+path));assertEquals(path,200,response.getStatusCode());assertTrue(path,response.getData().readAllBytes().length>0);count++;}
  assertTrue(count>10);
 }
 @Test public void unlistedScriptIsStillDenied(){var response=MainActivity.packagedUiResponse(RuntimeEnvironment.getApplication().getAssets(),Uri.parse("https://appassets.androidplatform.net/not-allowed.js"));assertNotEquals(200,response.getStatusCode());}
}
