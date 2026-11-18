package dev.mobilecodex.app.p1;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.File;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Offline, synthetic-only bootstrap entry point. Original app components stay disabled. */
public final class ProbeActivity extends Activity {
    private TextView output;
    private Button run;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ServiceConnection connection;
    private boolean bound;
    private final AtomicBoolean completed=new AtomicBoolean(true);
    private String marker;
    private JSONObject fileControls;
    private FixtureFileBroker fixtureBroker;
    private Path sourceFile;
    private String sourceMarker;
    private long generation;
    private Path direct,link,toolFile;
    private final Runnable timeout=()->finishProbe(null,"unknown_timeout");
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(32,24,32,24);
        TextView title=new TextView(this);title.setText("Mobile Codex for APK — P1 진단");title.setTextSize(24);layout.addView(title);
        TextView info=new TextView(this);info.setText("포크 전체 소스의 태블릿 빌드와 실행 권한 분리를 검사합니다.\n로그인·AI·터미널은 시작하지 않습니다. 실제 계정 정보 대신 새 합성 파일만 사용합니다.\n이 시험만으로 전체 보안 격리가 통과되지는 않습니다.");layout.addView(info);
        run=new Button(this);run.setText("격리 진단 실행");run.setOnClickListener(view->startProbe());layout.addView(run);
        Button copy=new Button(this);copy.setText("결과 복사");copy.setOnClickListener(view->{
            ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("P1 diagnostic",output.getText()));
            Toast.makeText(this,"진단 결과를 복사했습니다.",Toast.LENGTH_SHORT).show();
        });layout.addView(copy);
        ScrollView scroll=new ScrollView(this);output=new TextView(this);output.setTextIsSelectable(true);output.setTextSize(16);output.setText("진단 대기 중입니다.");scroll.addView(output);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);
    }
    private void startProbe() {
        if(!completed.compareAndSet(true,false))return;
        final long currentGeneration=++generation;
        fileControls=null;sourceFile=null;sourceMarker=null;run.setEnabled(false);output.setText("합성 파일 준비 및 isolatedProcess 응답 대기 중입니다…");
        try {
            Path root=new File(getFilesDir(),"p1-probe").toPath();Files.createDirectories(root);
            direct=root.resolve("synthetic-auth.txt");link=root.resolve("synthetic-link");toolFile=root.resolve("synthetic-tool.txt");
            marker="p1-synthetic-"+System.nanoTime();Files.write(direct,marker.getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.write(toolFile,marker.getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.deleteIfExists(link);Files.createSymbolicLink(link,direct);
            if(!ProbeChecks.readOutcome(direct,marker).equals("matched")||!ProbeChecks.readOutcome(link,marker).equals("matched"))throw new IllegalStateException("control_failed");
            fileControls=new JSONObject();
            for(String[] item:new String[][]{{"directRead",direct.toString()},{"symlinkRead",link.toString()},{"privateToolRead",toolFile.toString()}}) {
                JSONObject control=AndroidFileProbe.inspect(item[1],marker);fileControls.put(item[0],control);
                if(!control.getString("outcome").equals("matched"))throw new IllegalStateException("syscall_control_failed");
            }
            Path project=root.resolve("project-a");Files.createDirectories(project);
            sourceFile=project.resolve("sample-source.txt");sourceMarker="p1-project-source-"+System.nanoTime();
            Files.write(sourceFile,sourceMarker.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Path escape=project.resolve("escape-link");Files.deleteIfExists(escape);Files.createSymbolicLink(escape,direct);
            Path other=root.resolve("project-b");Files.createDirectories(other);Files.write(other.resolve("sample-source.txt"),"different-project".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            final String scratch=root.resolve("worker-scratch-"+System.nanoTime()).toString();
            fixtureBroker=new FixtureFileBroker(ParcelFileDescriptor.open(sourceFile.toFile(),ParcelFileDescriptor.MODE_READ_ONLY),marker);
            final FixtureFileBroker runBroker=fixtureBroker;
            Messenger reply=new Messenger(new Handler(Looper.getMainLooper(),message->{
                if(message.what==2) {
                    if(currentGeneration!=generation||completed.get())return true;
                    try{finishProbe(new JSONObject(message.getData().getString("result")),"observed");}
                    catch(Exception error){finishProbe(null,"unknown_response");}
                    return true;
                }return false;
            }));
            connection=new ServiceConnection(){
                @Override public void onServiceConnected(ComponentName name,IBinder binder){
                    if(currentGeneration!=generation||completed.get())return;
                    try {
                        Message request=Message.obtain(null,1);request.replyTo=reply;Bundle data=new Bundle();
                        data.putString("marker",marker);data.putString("direct",direct.toString());data.putString("link",link.toString());data.putString("toolFile",toolFile.toString());data.putString("nativeDir",getApplicationInfo().nativeLibraryDir);data.putBinder("fixtureBroker",runBroker);data.putString("sourceMarker",sourceMarker);data.putString("scratch",scratch);request.setData(data);new Messenger(binder).send(request);
                    }catch(Exception error){finishProbe(null,"unknown_send_failed");}
                }
                @Override public void onServiceDisconnected(ComponentName name){if(currentGeneration==generation)finishProbe(null,"unknown_service_disconnected");}
                @Override public void onNullBinding(ComponentName name){if(currentGeneration==generation)finishProbe(null,"unknown_null_binding");}
                @Override public void onBindingDied(ComponentName name){if(currentGeneration==generation)finishProbe(null,"unknown_binding_died");}
            };
            bound=bindService(new Intent(this,ProbeService.class),connection,BIND_AUTO_CREATE);
            if(!bound)finishProbe(null,"unknown_bind_failed");
            else handler.postDelayed(timeout,15000);
        }catch(Exception error){finishProbe(null,"unknown_setup_"+error.getClass().getSimpleName());}
    }
    private void finishProbe(JSONObject result,String status) {
        if(!completed.compareAndSet(false,true))return;
        handler.removeCallbacks(timeout);
        if(bound&&connection!=null){unbindService(connection);bound=false;}
        try {
            JSONObject summary=new JSONObject();summary.put("status",status);summary.put("hostUid",android.os.Process.myUid());summary.put("probeSchemaVersion",3);
            if(sourceFile!=null&&sourceMarker!=null)summary.put("sourceAfterBroker",AndroidFileProbe.inspect(sourceFile.toString(),sourceMarker));
            if(fileControls!=null)summary.put("hostReadControls",fileControls);
            if(result!=null) {
                summary.put("isolatedResult",result);
                int uid=result.optInt("isolatedUid",-1);
                summary.put("uidDifferent",uid>=0&&uid!=android.os.Process.myUid());
                summary.put("syntheticReadsBlocked",result.optString("directRead").equals("denied")&&result.optString("symlinkRead").equals("denied")&&result.optString("privateToolRead").equals("denied"));
            }
            summary.put("credentialIsolationGate","pending");summary.put("note","파일 차단과 도구 실행 가능성은 별도 조건입니다. 모든 AI·터미널·Gradle 실행 경로는 아직 통합하지 않았습니다.");
            output.setText(summary.toString(2));
        }catch(Exception error){output.setText("진단 결과 처리 실패: unknown");}
        if(fixtureBroker!=null){fixtureBroker.close();fixtureBroker=null;}
        run.setEnabled(true);
    }
    @Override protected void onDestroy(){
        handler.removeCallbacks(timeout);completed.set(true);generation++;
        if(fixtureBroker!=null){fixtureBroker.close();fixtureBroker=null;}
        if(bound&&connection!=null){unbindService(connection);bound=false;}
        super.onDestroy();
    }
}
