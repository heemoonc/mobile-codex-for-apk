package dev.mobilecodex.app.p1;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import org.json.JSONObject;
import java.io.FileDescriptor;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Direct Android open/read diagnostics for synthetic markers, without paths or file contents in output. */
final class AndroidFileProbe {
    private AndroidFileProbe() {}
    static JSONObject createWorkspace(String path) throws Exception {
        JSONObject out=new JSONObject();
        try {Os.mkdir(path,0700);out.put("status","created");}
        catch(ErrnoException error){out.put("status","error");out.put("errno",error.errno);out.put("operation","mkdir");}
        catch(Exception error){out.put("status","error");out.put("errorClass",error.getClass().getSimpleName());}
        return out;
    }
    static JSONObject inspect(String path,String marker) throws Exception {
        JSONObject out=new JSONObject();FileDescriptor fd=null;String operation="open";
        try {
            byte[] expected=marker.getBytes(StandardCharsets.UTF_8);
            if(expected.length==0||expected.length>127)throw new IllegalArgumentException("invalid_marker");
            fd=Os.open(path,OsConstants.O_RDONLY|OsConstants.O_CLOEXEC,0);
            operation="read";
            byte[] bytes=new byte[128];int total=0;
            while(total<bytes.length) {
                int count=Os.read(fd,bytes,total,bytes.length-total);
                if(count==0)break;
                total+=count;
            }
            out.put("outcome",Arrays.equals(expected,Arrays.copyOf(bytes,total))?"matched":"mismatched");
        } catch(ErrnoException error) {
            out.put("outcome",ProbeChecks.errnoOutcome(error.errno));
            out.put("errno",error.errno);out.put("operation",operation);
            out.put("errorClass","ErrnoException");
        } catch(Exception error) {
            out.put("outcome","error");out.put("errorClass",error.getClass().getSimpleName());
        } finally {
            if(fd!=null)try{Os.close(fd);}catch(ErrnoException ignored){}
        }
        return out;
    }
}
