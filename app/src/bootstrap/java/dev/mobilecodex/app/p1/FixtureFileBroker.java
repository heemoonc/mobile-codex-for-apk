package dev.mobilecodex.app.p1;

import android.os.*;
import java.io.Closeable;
import java.io.IOException;

/** Preopened read-only fixture only. No request can cause a host pathname to be opened. */
final class FixtureFileBroker extends Binder implements Closeable {
    static final String INTERFACE="dev.mobilecodex.apk.probe.FIXTURE_V1";
    static final int REQUEST=IBinder.FIRST_CALL_TRANSACTION;
    private final FixtureGrantPolicy policy;
    private ParcelFileDescriptor source;
    private int requests;
    FixtureFileBroker(ParcelFileDescriptor source,String token) {
        this.source=source;policy=new FixtureGrantPolicy(token,16);attachInterface(null,INTERFACE);
    }
    @Override protected synchronized boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
        if(code!=REQUEST)return super.onTransact(code,data,reply,flags);
        boolean grant=false;
        if(++requests<=16&&source!=null&&data.dataSize()<=2048) {
            data.enforceInterface(INTERFACE);
            String token=data.readString(),project=data.readString(),entry=data.readString();
            if(data.dataAvail()==0)grant=policy.acquire(token,project,entry);
        }
        reply.writeNoException();
        if(!grant){reply.writeInt(0);return true;}
        try {
            ParcelFileDescriptor duplicate=ParcelFileDescriptor.dup(source.getFileDescriptor());
            reply.writeInt(1);duplicate.writeToParcel(reply,Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
        }catch(IOException error){throw new RemoteException("fixture_descriptor_unavailable");}
        return true;
    }
    @Override public synchronized void close(){
        policy.close();
        if(source!=null){try{source.close();}catch(IOException ignored){}source=null;}
    }
}
