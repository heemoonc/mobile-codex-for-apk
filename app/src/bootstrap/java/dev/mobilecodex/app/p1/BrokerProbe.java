package dev.mobilecodex.app.p1;

import android.os.*;
import android.system.*;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Fixed requests to the private fixture broker; never requests actual account data. */
final class BrokerProbe {
    private BrokerProbe() {}
    static JSONObject run(IBinder broker,String token,String marker) throws Exception {
        JSONObject result=new JSONObject();
        String[][] rejected={
            {"syntheticAuth","project-a","auth"},
            {"traversal","project-a","../synthetic-auth.txt"},
            {"absolutePath","project-a","/data/synthetic-auth.txt"},
            {"otherProject","project-b","sample-source"},
            {"symlink","project-a","escape-link"}
        };
        for(String[] item:rejected)result.put(item[0],request(broker,token,item[1],item[2],marker));
        result.put("staleRun",request(broker,"old-"+token,"project-a","sample-source",marker));
        result.put("allowedSource",request(broker,token,"project-a","sample-source",marker));
        result.put("repeatedGrant",request(broker,token,"project-a","sample-source",marker));
        return result;
    }
    private static JSONObject request(IBinder broker,String token,String project,String entry,String expected) throws Exception {
        JSONObject out=new JSONObject();Parcel input=Parcel.obtain(),reply=Parcel.obtain();
        ParcelFileDescriptor fd=null;
        try {
            if(broker==null){out.put("status","error");out.put("reason","missing_broker");return out;}
            input.writeInterfaceToken(FixtureFileBroker.INTERFACE);input.writeString(token);input.writeString(project);input.writeString(entry);
            if(!broker.transact(FixtureFileBroker.REQUEST,input,reply,0)){out.put("status","error");return out;}
            reply.readException();
            if(reply.readInt()!=1){out.put("status","denied");return out;}
            fd=ParcelFileDescriptor.CREATOR.createFromParcel(reply);out.put("status","granted");
            try {
                Os.write(fd.getFileDescriptor(),new byte[]{0},0,1);
                out.put("writeDenied",false);
            } catch(ErrnoException error) {
                out.put("writeErrno",error.errno);
                out.put("writeDenied",error.errno==OsConstants.EBADF||error.errno==OsConstants.EACCES||error.errno==OsConstants.EPERM);
            }
            try(ParcelFileDescriptor.AutoCloseInputStream stream=new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                fd=null;String text=new String(ProbeChecks.readPrefix(stream,128),StandardCharsets.UTF_8);
                out.put("readOutcome",text.equals(expected)?"matched":"mismatched");
            }
        } catch(Exception error) {out.put("status","error");out.put("errorClass",error.getClass().getSimpleName());}
        finally {if(fd!=null)try{fd.close();}catch(Exception ignored){}input.recycle();reply.recycle();}
        return out;
    }
}
