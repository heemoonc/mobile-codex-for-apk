package dev.mobilecodex.app;
import dev.mobilecodex.app.core.ApkExport;
import org.junit.Test;
import static org.junit.Assert.*;
public class ApkFileNameTest {
 @Test public void usesProjectAndActualBuildNumber(){assertEquals("메모테스트-build12.apk",ApkExport.fileName("메모테스트",12));}
 @Test public void removesUnsafePathCharacters(){String name=ApkExport.fileName("../앱:테스트/이름",7);assertFalse(name.contains("/"));assertFalse(name.contains(":"));assertFalse(name.startsWith("."));assertTrue(name.endsWith("-build7.apk"));}
 @Test public void emptyNameHasStableFallback(){assertEquals("Android-app-build1.apk",ApkExport.fileName(" ",1));}
}
