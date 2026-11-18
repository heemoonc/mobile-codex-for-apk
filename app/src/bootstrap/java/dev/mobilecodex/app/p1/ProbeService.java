package dev.mobilecodex.app.p1;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.*;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** No engine, account, network request, or generic command RPC is available here. */
public final class ProbeService extends Service {
    private final Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(), message->{
        if(message.what!=1||message.replyTo==null)return false;
        Bundle input=message.getData();
        Messenger reply=message.replyTo;
        new Thread(()->{
            JSONObject result=new JSONObject();
            try {
                String marker=input.getString("marker","");
                result.put("isolatedUid",android.os.Process.myUid());
                result.put("probeSchemaVersion",3);
                JSONObject reads=new JSONObject();
                for(String[] item:new String[][]{{"directRead","direct"},{"symlinkRead","link"},{"privateToolRead","toolFile"}}) {
                    JSONObject read=AndroidFileProbe.inspect(input.getString(item[1]),marker);
                    reads.put(item[0],read);result.put(item[0],read.getString("outcome"));
                    result.put("nio_"+item[0],ProbeChecks.readOutcome(java.nio.file.Paths.get(input.getString(item[1])),marker));
                }
                result.put("fileReadDetails",reads);
                result.put("internetPermissionGranted",checkSelfPermission(Manifest.permission.INTERNET)==PackageManager.PERMISSION_GRANTED);
                result.put("systemShell",probe("/system/bin/sh","-c","id"));
                result.put("bundledPython",probe(input.getString("nativeDir")+"/libpython3.so","--version"));
                result.put("bundledProot",probe(input.getString("nativeDir")+"/libproot.so","--version"));
                result.put("projectBroker",BrokerProbe.run(input.getBinder("fixtureBroker"),marker,input.getString("sourceMarker","")));
                result.put("workspaceCreation",AndroidFileProbe.createWorkspace(input.getString("scratch")));
                java.util.Map<String,String> prootEnv=new java.util.HashMap<>();
                prootEnv.put("PROOT_LOADER",input.getString("nativeDir")+"/libproot_loader.so");
                prootEnv.put("PROOT_TMP_DIR",input.getString("scratch"));
                prootEnv.put("PROOT_DONT_POLLUTE_ROOTFS","1");
                result.put("prootChildExecution",probe(prootEnv,input.getString("nativeDir")+"/libproot.so","-r","/","-w","/","/system/bin/sh","-c","id"));
                result.put("credentialIsolationGate","pending");
                result.put("jdkGradleExecution","not_tested");
                result.put("allAiAndTerminalRoutes","not_integrated");
            } catch(Exception error) {
                try{result.put("probeError",error.getClass().getSimpleName());}catch(Exception ignored){}
            }
            Message response=Message.obtain(null,2);
            Bundle data=new Bundle();data.putString("result",result.toString());response.setData(data);
            try{reply.send(response);}catch(RemoteException ignored){}
        },"p1-isolation-probe").start();
        return true;
    }));
    private JSONObject probe(String... command) throws Exception {return probe(java.util.Collections.emptyMap(),command);}
    private JSONObject probe(java.util.Map<String,String> extra,String... command) throws Exception {
        JSONObject out=new JSONObject();java.lang.Process child=null;
        try {
            ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true).directory(new java.io.File("/"));
            builder.environment().clear();
            builder.environment().put("PATH","/system/bin");
            builder.environment().put("LD_LIBRARY_PATH",getApplicationInfo().nativeLibraryDir);
            builder.environment().putAll(extra);
            child=builder.start();
            if(!child.waitFor(2,TimeUnit.SECONDS)) {
                out.put("status","timeout");child.destroyForcibly();
            } else {
                out.put("status","exited");out.put("exitCode",child.exitValue());
                try(InputStream stream=child.getInputStream()) {
                    String text=new String(ProbeChecks.readPrefix(stream,2048),StandardCharsets.UTF_8);
                    // Keep only fixed-probe output; redact package installation paths.
                    out.put("output",text.replace(getApplicationInfo().nativeLibraryDir,"<nativeDir>").replace(getApplicationInfo().dataDir,"<appData>"));
                }
            }
        } catch(Exception error){out.put("status","error");out.put("errorClass",error.getClass().getSimpleName());}
        finally {if(child!=null&&child.isAlive())child.destroyForcibly();}
        return out;
    }
    @Override public IBinder onBind(Intent intent){return receiver.getBinder();}
}
