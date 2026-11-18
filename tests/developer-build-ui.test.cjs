const {test,afterEach}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {JSDOM}=require('jsdom');
const root='app/src/main/assets/web/';const opened=[];
afterEach(()=>{for(const dom of opened.splice(0))dom.window.close();});
const tick=()=>new Promise(r=>setTimeout(r,20));
function setup(overrides={},settings={}){
 const dom=new JSDOM(fs.readFileSync(root+'index.html','utf8'),{url:'https://appassets.androidplatform.net/index.html',runScripts:'outside-only'});opened.push(dom);
 const w=dom.window,calls=[];if(settings.timers){const original=w.setTimeout.bind(w);w.setTimeout=(fn,delay,...args)=>{const id=original(fn,delay,...args);settings.timers.push({fn,delay,id});return id;};}w.matchMedia=()=>({matches:false,addEventListener(){}});
 w.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};w.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');this.dispatchEvent(new w.Event('close'));};
 const state={ready:false,busy:false,models:[],messages:[],sessions:[],account:{},workspace:{key:'A',selected:true,name:'A',available:true},threadId:'A',cwd:'/A'};
 const inspected={workspaceKey:'A',project:'/A',linux:{enabled:true,installed:true},config:{javaHome:'/jdk',sdkRoot:'/sdk',aapt2:'/aapt2',task:'assembleDebug'},apks:[{path:'app/build/outputs/apk/debug/a.apk',size:123}],truncated:false};
 w.Native={locale:()=>JSON.stringify({choice:'ko',systemLanguage:'ko'}),textSize:()=>JSON.stringify({percent:100}),postMessage(raw){const m=JSON.parse(raw);calls.push(m);queueMicrotask(async()=>{try{const result=overrides[m.action]?await overrides[m.action](m):m.action==='state'?state:m.action==='build.inspect'||m.action==='build.save'?inspected:m.action==='build.check'||m.action==='build.run'?{revision:1,id:'build-1',running:true,output:'building'}:{};w.mobileCodexEvent('response',{id:m.id,result});}catch(e){w.mobileCodexEvent('response',{id:m.id,error:e.message});}});}};
 for(const f of ['translations.js','locale.js','ui-core.js','app.js'])w.eval(fs.readFileSync(root+f,'utf8'));
 return {w,d:w.document,calls,state,inspected};
}
async function open(ui){await tick();assert.ok(ui.d.getElementById('show-build'),'build menu entry exists');ui.d.getElementById('show-build').click();await tick();}
test('build panel inspects current project and saves scoped settings',async()=>{
 const ui=setup();await open(ui);assert.equal(ui.d.getElementById('build-dialog').open,true);assert.equal(ui.d.getElementById('build-java-home').value,'/jdk');
 assert.equal(ui.calls.find(m=>m.action==='build.inspect').args.workspaceKey,'A');
 ui.d.getElementById('build-task').value=':app:assembleDebug';ui.d.getElementById('build-save').click();await tick();
 assert.deepEqual(ui.calls.find(m=>m.action==='build.save').args,{workspaceKey:'A',javaHome:'/jdk',sdkRoot:'/sdk',aapt2:'/aapt2',task:':app:assembleDebug'});
});
for(const action of ['check','run'])test('build '+action+' opens existing in-app terminal',async()=>{
 const ui=setup();await open(ui);ui.d.getElementById('build-'+action).click();await tick();await tick();
 assert.equal(ui.calls.find(m=>m.action==='build.'+action).args.workspaceKey,'A');assert.equal(ui.d.getElementById('terminal-dialog').open,true);assert.match(ui.d.getElementById('terminal-output').textContent,/building/);
});
test('APK selection requests Android installation with scoped relative path',async()=>{
 const ui=setup({'build.install':()=>({installRequested:true})});await open(ui);ui.d.querySelector('#build-apks button').click();await tick();
 assert.deepEqual(ui.calls.find(m=>m.action==='build.install').args,{workspaceKey:'A',path:'app/build/outputs/apk/debug/a.apk'});
 assert.match(ui.d.getElementById('build-status').textContent,/요청/);assert.doesNotMatch(ui.d.getElementById('build-status').textContent,/완료/);
});
test('missing Linux keeps preparation guidance visible and disables execution',async()=>{
 const ui=setup({'build.inspect':()=>({workspaceKey:'A',project:'/A',linux:{enabled:false},config:{},apks:[]})});await open(ui);
 assert.equal(ui.d.getElementById('build-run').disabled,true);assert.match(ui.d.getElementById('build-status').textContent,/Linux/);assert.ok(ui.d.getElementById('build-linux-settings'));
});
test('late inspection for previous project is discarded',async()=>{
 let release;const ui=setup({'build.inspect':()=>new Promise(r=>release=r)});await open(ui);
 ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,name:'B',available:true},cwd:'/B'});release(ui.inspected);await tick();
 assert.equal(ui.d.getElementById('build-apks').textContent,'');assert.equal(ui.d.getElementById('build-run').disabled,true);assert.notEqual(ui.d.getElementById('build-project').textContent,'/A');
});
test('installation failure is shown without success claim',async()=>{
 const ui=setup({'build.install':()=>{throw Error('설치 허용이 필요합니다.');}});await open(ui);ui.d.querySelector('#build-apks button').click();await tick();
 assert.match(ui.d.getElementById('build-status').textContent,/설치 허용/);assert.match(ui.d.getElementById('toast').textContent,/설치 허용/);
});
test('build tool setup opens download setup without requesting a ZIP',async()=>{const ui=setup();let opened=0;ui.w.AndroidSetupUI={open:()=>opened++};await open(ui);ui.d.getElementById('build-tools-import').click();await tick();assert.equal(opened,1);assert.equal(ui.calls.some(m=>m.action==='build.tools.import'),false);assert.equal(ui.d.getElementById('build-java-home').value,'/jdk');});
test('empty current folder creates a scoped sample and then offers build',async()=>{
 const ui=setup({'build.inspect':()=>({...ui.inspected,apks:[],canCreateSample:true}),'build.sample.create':()=>({...ui.inspected,apks:[],canCreateSample:false,sampleCreated:true})});await open(ui);
 assert.ok(ui.d.getElementById('build-sample-create'),'sample button exists');assert.equal(ui.d.getElementById('build-sample-create').disabled,false);ui.d.getElementById('build-sample-create').click();await tick();
 assert.deepEqual(ui.calls.find(m=>m.action==='build.sample.create').args,{workspaceKey:'A'});assert.match(ui.d.getElementById('build-status').textContent,/APK 빌드/);assert.equal(ui.d.getElementById('build-sample-create').disabled,true);assert.equal(ui.d.getElementById('build-java-home').value,'/jdk');
});
test('sample creation stays disabled for a nonempty project',async()=>{
 const ui=setup({'build.inspect':()=>({...ui.inspected,canCreateSample:false})});await open(ui);assert.ok(ui.d.getElementById('build-sample-create'),'sample button exists');assert.equal(ui.d.getElementById('build-sample-create').disabled,true);
});
test('late sample result does not replace another project state',async()=>{
 let release;const ui=setup({'build.inspect':()=>({...ui.inspected,canCreateSample:true}),'build.sample.create':()=>new Promise(r=>release=r)});await open(ui);
 assert.ok(ui.d.getElementById('build-sample-create'),'sample button exists');ui.d.getElementById('build-sample-create').click();await tick();
 ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,name:'B',available:true},cwd:'/B'});release({...ui.inspected,canCreateSample:false,sampleCreated:true});await tick();
 assert.equal(ui.d.getElementById('build-run').disabled,true);assert.doesNotMatch(ui.d.getElementById('build-status').textContent,/샘플을 만들었습니다/);
});
test('refresh reports APK count and places results before preparation controls',async()=>{
 const ui=setup();await open(ui);ui.d.getElementById('build-refresh').click();await tick();
 assert.match(ui.d.getElementById('build-status').textContent,/APK 1개/);assert.match(ui.d.getElementById('toast').textContent,/APK 1개/);
 assert.ok(ui.d.getElementById('build-apks').compareDocumentPosition(ui.d.getElementById('build-tools-import')) & ui.w.Node.DOCUMENT_POSITION_FOLLOWING);
});
test('empty truncated discovery does not claim APK absence',async()=>{
 const ui=setup({'build.inspect':()=>({...ui.inspected,apks:[],truncated:true})});await open(ui);
 assert.match(ui.d.getElementById('build-status').textContent,/상한/);assert.doesNotMatch(ui.d.getElementById('build-apks').textContent,/APK가 없습니다/);
});
test('refresh errors remain visible through a toast',async()=>{
 let fail=false;const ui=setup({'build.inspect':()=>{if(fail)throw Error('탐색 실패');return ui.inspected;}});await open(ui);fail=true;ui.d.getElementById('build-refresh').click();await tick();
 assert.match(ui.d.getElementById('toast').textContent,/탐색 실패/);
});
test('late refresh from a previous workspace cannot announce an APK result',async()=>{
 let release,refresh=false;const ui=setup({'build.inspect':()=>refresh?new Promise(r=>release=r):ui.inspected});await open(ui);refresh=true;ui.d.getElementById('build-refresh').click();await tick();
 ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,available:true},cwd:'/B'});release(ui.inspected);await tick();
 assert.doesNotMatch(ui.d.getElementById('toast').textContent,/APK 1개/);assert.equal(ui.d.getElementById('build-apks').textContent,'');
});
test('install preparation blocks duplicates and reports beside the chosen APK',async()=>{
 let release;const ui=setup({'build.install':()=>new Promise(r=>release=r)});await open(ui);const button=ui.d.querySelector('#build-apks button');button.click();button.click();await tick();
 assert.equal(button.disabled,true);assert.match(ui.d.querySelector('#build-apks .build-apk-status').textContent,/준비/);assert.equal(ui.calls.filter(m=>m.action==='build.install').length,1);
 release({installRequested:true});await tick();assert.equal(button.disabled,false);assert.match(ui.d.querySelector('#build-apks .build-apk-status').textContent,/Android 설치/);assert.doesNotMatch(ui.d.querySelector('#build-apks .build-apk-status').textContent,/설치 완료/);
});
test('successful build session offers a direct scoped result view',async()=>{
 const ui=setup();await open(ui);ui.d.getElementById('build-run').click();await tick();await tick();const button=ui.d.getElementById('terminal-build-results');assert.ok(button);assert.equal(button.hidden,true);
 ui.w.mobileCodexEvent('terminal.state',{revision:2,id:'build-1',running:false,exitCode:0,output:'BUILD SUCCESSFUL'});assert.equal(button.hidden,false);button.click();await tick();
 assert.equal(ui.d.getElementById('build-dialog').open,true);assert.equal(ui.d.getElementById('terminal-dialog').open,false);assert.match(ui.d.getElementById('build-status').textContent,/APK 1개/);
});
for(const scenario of ['failed','other-session','other-workspace','check'])test('result shortcut excludes '+scenario,async()=>{
 const ui=setup();await open(ui);ui.d.getElementById('build-'+(scenario==='check'?'check':'run')).click();await tick();await tick();
 if(scenario==='other-workspace')ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,available:true},cwd:'/B'});
 ui.w.mobileCodexEvent('terminal.state',{revision:2,id:scenario==='other-session'?'unrelated':'build-1',running:false,exitCode:scenario==='failed'?1:0,output:'done'});
 assert.ok(ui.d.getElementById('terminal-build-results'));assert.equal(ui.d.getElementById('terminal-build-results').hidden,true);
});
test('terminal completion arriving before build RPC reply still offers results',async()=>{
 let release;const ui=setup({'build.run':()=>new Promise(r=>release=r)});await open(ui);ui.d.getElementById('build-run').click();await tick();await tick();
 ui.w.mobileCodexEvent('terminal.state',{revision:2,id:'fast-build',running:false,exitCode:0,output:'BUILD SUCCESSFUL'});
 release({revision:1,id:'fast-build',running:true,output:'starting'});await tick();
 assert.equal(ui.d.getElementById('terminal-build-results').hidden,false);assert.match(ui.d.getElementById('terminal-output').textContent,/BUILD SUCCESSFUL/);
});
test('closing and reopening a running build keeps its transcript without starting another build',async()=>{
 let current={revision:2,id:'build-1',running:true,output:'compile in progress'};
 const ui=setup({'terminal.state':()=>current});await open(ui);ui.d.getElementById('build-run').click();await tick();await tick();
 ui.d.querySelector('[data-close="terminal-dialog"]').click();ui.w.mobileCodexEvent('terminal.state',current);
 ui.d.getElementById('show-terminal').click();await tick();
 assert.equal(ui.d.getElementById('terminal-dialog').open,true);assert.match(ui.d.getElementById('terminal-output').textContent,/compile in progress/);assert.equal(ui.calls.filter(m=>m.action==='build.run').length,1);
});
test('failed build log and exit code remain available after the dialog reopens',async()=>{
 const failed={revision:2,id:'build-1',running:false,exitCode:1,output:'MainActivity.java: error: fixture compile failure'};
 const ui=setup({'terminal.state':()=>failed});await open(ui);ui.d.getElementById('build-run').click();await tick();await tick();
 ui.w.mobileCodexEvent('terminal.state',failed);ui.d.querySelector('[data-close="terminal-dialog"]').click();ui.d.getElementById('show-terminal').click();await tick();
 assert.match(ui.d.getElementById('terminal-output').textContent,/MainActivity.java: error/);assert.match(ui.d.getElementById('terminal-output').textContent,/종료 코드 1/);assert.equal(ui.d.getElementById('terminal-build-results').hidden,true);assert.equal(ui.calls.filter(m=>m.action==='build.run').length,1);
});
for(const [status,label] of [['succeeded','성공'],['failed','실패'],['cancelled','취소됨'],['interrupted','중단됨'],['running','실행 중']])test('recent build metadata displays '+status+' without controlling the live terminal',async()=>{
 const entry={jobId:'history-1',task:'assembleDebug',status,startedAt:1000,updatedAt:2000,exitCode:status==='succeeded'?0:null,truncated:false};
 const ui=setup({'build.inspect':()=>({...ui.inspected,history:{record:entry,storageWarning:false}}),'build.history':()=>({workspaceKey:'A',history:{record:{...entry,output:'saved build log'},storageWarning:false}})});await open(ui);
 assert.ok(ui.d.getElementById('build-history-open'));assert.match(ui.d.getElementById('build-history-summary').textContent,new RegExp(label));ui.d.getElementById('build-history-open').click();await tick();
 assert.equal(ui.d.getElementById('build-history-dialog').open,true);assert.match(ui.d.getElementById('build-history-output').textContent,/saved build log/);
 assert.equal(ui.d.querySelectorAll('#build-history-dialog input,#build-history-dialog textarea').length,0);assert.equal(ui.calls.filter(m=>m.action==='terminal.run'||m.action==='terminal.stop').length,0);assert.deepEqual(ui.calls.find(m=>m.action==='build.history').args,{workspaceKey:'A'});
 if(status==='interrupted')assert.match(ui.d.getElementById('build-history-note').textContent,/종료 여부/);
});
test('missing or failed journal does not pretend there is a saved build',async()=>{
 const ui=setup({'build.inspect':()=>({...ui.inspected,history:{record:null,storageWarning:true}})});await open(ui);
 assert.ok(ui.d.getElementById('build-history-open'));assert.equal(ui.d.getElementById('build-history-open').disabled,true);assert.match(ui.d.getElementById('build-history-warning').textContent,/저장|복구/);
});
test('late previous-project history cannot open or leak a saved log',async()=>{
 let release;const ui=setup({'build.inspect':()=>({...ui.inspected,history:{record:{status:'interrupted',task:'assembleDebug'},storageWarning:false}}),'build.history':()=>new Promise(r=>release=r)});await open(ui);
 assert.ok(ui.d.getElementById('build-history-open'));ui.d.getElementById('build-history-open').click();await tick();
 ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,available:true},cwd:'/B'});release({workspaceKey:'A',history:{record:{output:'old project log',status:'interrupted'}}});await tick();
 assert.equal(ui.d.getElementById('build-history-dialog').open,false);assert.equal(ui.d.getElementById('build-history-output').textContent,'');
});
test('changing project closes and clears already-open history',async()=>{
 const ui=setup({'build.inspect':()=>({...ui.inspected,history:{record:{status:'succeeded',task:'assembleDebug'}}}),'build.history':()=>({workspaceKey:'A',history:{record:{output:'previous project log',status:'succeeded'}}})});await open(ui);
 assert.ok(ui.d.getElementById('build-history-open'));ui.d.getElementById('build-history-open').click();await tick();
 ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,available:true},cwd:'/B'});assert.equal(ui.d.getElementById('build-history-dialog').open,false);assert.equal(ui.d.getElementById('build-history-output').textContent,'');
});

function infoButton(ui){return [...ui.d.querySelectorAll('#build-apks button')].find(b=>b.textContent==='정보 확인');}
test('selected APK metadata is scoped and displayed as readonly text',async()=>{
 const ui=setup({'build.metadata':()=>({workspaceKey:'A',path:'app/build/outputs/apk/debug/a.apk',packageName:'dev.example.app',versionName:'<b>3</b>',versionCode:3,size:123,modified:1000})});await open(ui);
 assert.ok(infoButton(ui));infoButton(ui).click();await tick();
 assert.deepEqual(ui.calls.find(m=>m.action==='build.metadata').args,{workspaceKey:'A',path:'app/build/outputs/apk/debug/a.apk'});
 assert.equal(ui.d.getElementById('build-metadata-dialog').open,true);assert.match(ui.d.getElementById('build-metadata-output').textContent,/dev.example.app/);assert.match(ui.d.getElementById('build-metadata-output').textContent,/<b>3<\/b>/);assert.equal(ui.d.querySelectorAll('#build-metadata-output b').length,0);
 assert.match(ui.d.getElementById('build-metadata-output').textContent,/123/);assert.equal(ui.calls.some(m=>m.action==='build.install'),false);
 ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,available:true},cwd:'/B'});assert.equal(ui.d.getElementById('build-metadata-dialog').open,false);assert.equal(ui.d.getElementById('build-metadata-output').textContent,'');
});
test('APK metadata parse failure shows error without opening details',async()=>{
 const ui=setup({'build.metadata':()=>{throw Error('APK 정보를 읽을 수 없습니다.');}});await open(ui);assert.ok(infoButton(ui));infoButton(ui).click();await tick();assert.match(ui.d.getElementById('toast').textContent,/읽을 수 없습니다/);assert.equal(ui.d.getElementById('build-metadata-dialog').open,false);assert.equal(infoButton(ui).disabled,false);
});
test('late APK metadata from previous project is discarded',async()=>{
 let release;const ui=setup({'build.metadata':()=>new Promise(r=>release=r)});await open(ui);assert.ok(infoButton(ui));infoButton(ui).click();await tick();
 ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,available:true},cwd:'/B'});release({workspaceKey:'A',packageName:'private.old',versionCode:3});await tick();assert.equal(ui.d.getElementById('build-metadata-dialog').open,false);assert.equal(ui.d.getElementById('build-metadata-output').textContent,'');
});
for(const status of ['failed','cancelled','interrupted','succeeded'])test('APK notice is conservative for '+status,async()=>{
 const ui=setup({'build.inspect':()=>({...ui.inspected,history:{record:{status,task:'assembleDebug'}}})});await open(ui);const warning=ui.d.getElementById('build-artifact-warning');assert.ok(warning);if(status==='succeeded')assert.equal(warning.textContent,'');else assert.match(warning.textContent,/이전 APK/);
});

async function openNewAndroid(ui){await tick();ui.d.getElementById('add-project').click();await tick();const choice=[...ui.d.querySelectorAll('#project-link-choices button')].find(b=>b.textContent.includes('Android 앱 만들기'));assert.ok(choice);choice.click();await tick();}
function fillNewAndroid(ui){ui.d.getElementById('android-project-name').value='나의 앱';ui.d.getElementById('android-project-package').value='com.example.myapp';ui.d.getElementById('android-project-folder').value='나의 앱';}
test('named Android project form creates once and closes only on success',async()=>{
 let release;const ui=setup({'projects.android.create':()=>new Promise(r=>release=r)});await openNewAndroid(ui);fillNewAndroid(ui);
 const form=ui.d.getElementById('android-project-form');form.dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();form.dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();assert.equal(ui.calls.filter(m=>m.action==='projects.android.create').length,1);assert.deepEqual(ui.calls.find(m=>m.action==='projects.android.create').args,{name:'나의 앱',packageName:'com.example.myapp',folder:'나의 앱',template:'java'});assert.equal(ui.d.getElementById('android-project-submit').disabled,true);
 release({key:'new-project'});await tick();assert.equal(ui.d.getElementById('android-project-dialog').open,false);assert.match(ui.d.getElementById('toast').textContent,/만들/);
});
test('named Android project errors preserve input and allow retry',async()=>{
 const ui=setup({'projects.android.create':()=>{throw Error('같은 이름의 폴더가 이미 있습니다.');}});await openNewAndroid(ui);fillNewAndroid(ui);ui.d.getElementById('android-project-form').dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();assert.equal(ui.d.getElementById('android-project-dialog').open,true);assert.match(ui.d.getElementById('android-project-error').textContent,/이미/);assert.equal(ui.d.getElementById('android-project-name').value,'나의 앱');assert.equal(ui.d.getElementById('android-project-submit').disabled,false);
});
test('named Android project cancellation performs no creation',async()=>{const ui=setup();await openNewAndroid(ui);ui.d.querySelector('[data-close="android-project-dialog"]').click();await tick();assert.equal(ui.calls.some(m=>m.action==='projects.android.create'),false);});
test('named Android project remains unavailable in readonly mode',async()=>{const ui=setup();await openNewAndroid(ui);fillNewAndroid(ui);ui.w.mobileCodexEvent('state',{...ui.state,permissions:'read-only'});ui.d.getElementById('android-project-form').dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();assert.equal(ui.calls.some(m=>m.action==='projects.android.create'),false);});
test('source ZIP export is scoped and cancel never announces saved',async()=>{
 const ui=setup({'ui.source.export':()=>({cancelled:true})});ui.w.confirm=()=>true;await open(ui);const button=ui.d.getElementById('build-source-export');assert.ok(button);button.click();await tick();assert.equal(ui.calls.find(m=>m.action==='ui.source.export').args.workspaceKey,'A');assert.doesNotMatch(ui.d.getElementById('build-status').textContent,/저장했습니다/);
});
test('source ZIP restore previews then applies only explicit new folder',async()=>{
 const ui=setup({'ui.source.import':()=>({token:'one',name:'원본',entries:2,bytes:5,workspaceKey:'A'})});await tick();ui.d.getElementById('add-project').click();await tick();const choice=[...ui.d.querySelectorAll('#project-link-choices button')].find(b=>b.textContent.includes('소스 ZIP'));assert.ok(choice);choice.click();await tick();assert.ok(ui.d.getElementById('source-restore-dialog').open);assert.equal(ui.calls.some(m=>m.action==='source.restore'),false);ui.d.getElementById('source-restore-folder').value='restored';ui.d.getElementById('source-restore-apply').click();await tick();assert.deepEqual(ui.calls.find(m=>m.action==='source.restore').args,{workspaceKey:'A',token:'one',name:'원본',folder:'restored'});assert.equal(ui.calls.some(m=>m.action==='build.run'),false);
});
for(const outcome of ['saved','error'])test('source ZIP export '+outcome+' is reported accurately',async()=>{
 const ui=setup({'ui.source.export':()=>{if(outcome==='error')throw Error('저장 실패');return {saved:true};}});ui.w.confirm=()=>true;await open(ui);ui.d.getElementById('build-source-export').click();await tick();assert.match(ui.d.getElementById('build-status').textContent,outcome==='error'?/저장 실패/:/저장했습니다/);
});
test('source preview cancellation revokes token and readonly prevents applying',async()=>{
 const ui=setup({'ui.source.import':()=>({token:'one',name:'원본',entries:2,bytes:5,workspaceKey:'A'})});await tick();ui.w.mobileCodexEvent('state',{...ui.state,permissions:'read-only'});ui.d.getElementById('add-project').click();await tick();[...ui.d.querySelectorAll('#project-link-choices button')].find(b=>b.textContent.includes('소스 ZIP')).click();await tick();assert.equal(ui.d.getElementById('source-restore-apply').disabled,true);ui.d.querySelector('[data-close="source-restore-dialog"]').click();await tick();assert.ok(ui.calls.find(m=>m.action==='source.cancel'));assert.equal(ui.calls.some(m=>m.action==='source.restore'),false);
});
for(const outcome of ['saved','cancelled','error'])test('APK file save '+outcome+' uses scoped path and accurate status',async()=>{
 const ui=setup({'build.export':()=>{if(outcome==='error')throw Error('저장 오류');return {[outcome]:true};}});await open(ui);const button=[...ui.d.querySelectorAll('#build-apks button')].find(b=>b.textContent==='APK 저장');assert.ok(button);button.click();await tick();assert.deepEqual(ui.calls.find(m=>m.action==='build.export').args,{workspaceKey:'A',path:'app/build/outputs/apk/debug/a.apk'});const status=ui.d.querySelector('.build-apk-status').textContent;assert.match(status,outcome==='saved'?/저장했습니다/:outcome==='cancelled'?/취소/:/저장 오류/);if(outcome!=='saved')assert.doesNotMatch(status,/저장했습니다/);assert.equal(ui.calls.some(m=>m.action==='build.install'),false);
});
test('APK save blocks duplicate across refreshed rows and discards late previous-scope result',async()=>{
 let finish;const ui=setup({'build.export':()=>new Promise(r=>finish=r)});await open(ui);let button=[...ui.d.querySelectorAll('#build-apks button')].find(b=>b.textContent==='APK 저장');assert.ok(button);button.click();button.click();await tick();ui.d.getElementById('build-refresh').click();await tick();button=[...ui.d.querySelectorAll('#build-apks button')].find(b=>b.textContent==='APK 저장');button.click();await tick();assert.equal(ui.calls.filter(m=>m.action==='build.export').length,1);ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,available:true},cwd:'/B'});finish({saved:true});await tick();assert.doesNotMatch(ui.d.getElementById('build-status').textContent,/저장했습니다/);
});

for(const outcome of ['saved','cancelled'])test('APK save waits beyond ordinary RPC deadline for '+outcome,async()=>{
 const timers=[];let finish;const ui=setup({'build.export':()=>new Promise(r=>finish=r)},{timers});await open(ui);const start=timers.length;const save=[...ui.d.querySelectorAll('#build-apks button')].find(b=>b.textContent==='APK 저장');save.click();await tick();for(const timer of timers.slice(start).filter(timer=>timer.delay===150000||timer.delay===610000))timer.fn();await tick();assert.equal(save.disabled,true);finish({[outcome]:true});await tick();assert.match(ui.d.querySelector('.build-apk-status').textContent,outcome==='saved'?/저장했습니다/:/취소/);assert.equal(save.disabled,false);
});

async function failedHistory(output='MainActivity.java: error: broken',status='failed') {
 const record={jobId:'failure-1',task:'assembleDebug',status,exitCode:1,output};
 const ui=setup({'build.inspect':()=>({...ui.inspected,history:{record}}),'build.history':()=>({workspaceKey:'A',history:{record}})});
 await open(ui);ui.d.getElementById('build-history-open').click();await tick();return ui;
}
test('failed log preview appends edited text to latest draft without sending',async()=>{
 const ui=await failedHistory();const d=ui.d;
 d.getElementById('prompt').value='existing draft';d.getElementById('prompt').dispatchEvent(new ui.w.Event('input'));
 assert.ok(d.getElementById('build-error-preview'));d.getElementById('build-error-preview').click();await tick();
 assert.equal(d.getElementById('build-error-dialog').open,true);assert.match(d.getElementById('build-error-detail').textContent,/failure-1/);
 assert.match(d.getElementById('build-error-text').value,/MainActivity.java/);
 d.getElementById('build-error-text').value='reviewed error';d.getElementById('prompt').value='newer draft';
 d.getElementById('build-error-add').click();await tick();
 assert.equal(d.getElementById('prompt').value,'newer draft\n\nreviewed error');
 assert.equal(d.getElementById('build-error-dialog').open,false);assert.equal(d.getElementById('build-dialog').open,false);
 assert.equal(ui.calls.some(m=>m.action==='chat.send'||m.action==='build.run'),false);
 d.getElementById('build-error-add').click();await tick();assert.equal(d.getElementById('prompt').value,'newer draft\n\nreviewed error');
 d.getElementById('build-error-preview').click();await tick();assert.equal(d.getElementById('build-error-dialog').open,false);
});
for(const status of ['succeeded','running','cancelled','interrupted'])test('handoff unavailable for '+status,async()=>{
 const ui=await failedHistory('some log',status);assert.ok(ui.d.getElementById('build-error-preview'));assert.equal(ui.d.getElementById('build-error-preview').disabled,true);
});
test('handoff masks known credentials and bounds log tail before editable preview',async()=>{
 const secret='sk-'+ 'a'.repeat(80);const ui=await failedHistory('x'.repeat(22000)+'\nAuthorization: Bearer '+secret+'\nerror at end');
 ui.d.getElementById('build-error-preview').click();await tick();const value=ui.d.getElementById('build-error-text').value;
 assert.doesNotMatch(value,/sk-aaaa|Bearer sk/);assert.match(value,/REDACTED/);assert.match(value,/error at end/);assert.ok(value.length<20500);assert.match(ui.d.getElementById('build-error-detail').textContent,/끝부분/);
});
for(const scope of ['thread','project'])test('handoff refuses changed '+scope+' and preserves new draft',async()=>{
 const ui=await failedHistory();ui.d.getElementById('build-error-preview').click();await tick();
 ui.w.mobileCodexEvent('state',{...ui.state,threadId:'new-thread',...(scope==='project'?{workspace:{key:'B',selected:true,available:true},cwd:'/B'}:{})});
 ui.d.getElementById('prompt').value='other conversation';ui.d.getElementById('build-error-add').click();await tick();assert.equal(ui.d.getElementById('prompt').value,'other conversation');
});
test('cancelling handoff preserves draft and does not consume record',async()=>{
 const ui=await failedHistory();ui.d.getElementById('prompt').value='keep';ui.d.getElementById('build-error-preview').click();await tick();ui.d.querySelector('[data-close="build-error-dialog"]').click();await tick();assert.equal(ui.d.getElementById('prompt').value,'keep');ui.d.getElementById('build-error-preview').click();await tick();assert.equal(ui.d.getElementById('build-error-dialog').open,true);
});
test('empty failed output cannot become an AI request',async()=>{const ui=await failedHistory('');assert.ok(ui.d.getElementById('build-error-preview'));assert.equal(ui.d.getElementById('build-error-preview').disabled,true);});
test('new build invalidates captured failure before it can be added',async()=>{
 const ui=await failedHistory();ui.d.getElementById('build-error-preview').click();await tick();ui.d.getElementById('build-run').click();await tick();ui.d.getElementById('build-error-add').click();await tick();assert.equal(ui.d.getElementById('prompt').value,'');
});

test('thread switch during history fetch cannot authorize old request',async()=>{
 let release;const record={jobId:'old',status:'failed',output:'private log'};
 const ui=setup({'build.inspect':()=>({...ui.inspected,history:{record}}),'build.history':()=>new Promise(r=>release=r)});await open(ui);ui.d.getElementById('build-history-open').click();await tick();ui.w.mobileCodexEvent('state',{...ui.state,threadId:'B'});release({workspaceKey:'A',history:{record}});await tick();assert.equal(ui.d.getElementById('build-error-preview').disabled,true);
});
test('empty edited request remains in preview and attachments are preserved on addition',async()=>{
 const ui=await failedHistory();const storage=ui.w.localStorage;
 ui.w.mobileCodexEvent('attachments.picked',{receiptId:'handoff-attachment',draftKey:ui.w.UiCore.draftKey('A','A'),attachments:[{id:'keep',name:'keep.txt'}],errors:[]});await tick();
 ui.d.getElementById('build-error-preview').click();await tick();ui.d.getElementById('build-error-text').value=' ';ui.d.getElementById('build-error-add').click();await tick();assert.equal(ui.d.getElementById('build-error-dialog').open,true);assert.equal(ui.d.getElementById('prompt').value,'');
 ui.d.getElementById('build-error-text').value='fix';ui.d.getElementById('build-error-add').click();await tick();assert.equal(ui.d.getElementById('prompt').value,'fix');
 assert.ok(Object.keys(storage).some(k=>k.startsWith('draft:')&&storage.getItem(k)==='fix'));
 assert.match(ui.d.getElementById('draft-context').textContent,/keep.txt/);
 assert.ok(Object.keys(storage).some(k=>k.endsWith(':context')&&storage.getItem(k).includes('keep.txt')));
});

test('new build blocks an older pending history response from reviving failure handoff',async()=>{
 let release;const record={jobId:'old-failure',task:'assembleDebug',status:'failed',output:'old error'};
 const ui=setup({'build.inspect':()=>({...ui.inspected,history:{record}}),'build.history':()=>new Promise(r=>release=r)});await open(ui);ui.d.getElementById('build-history-open').click();await tick();ui.d.getElementById('build-run').click();await tick();await tick();release({workspaceKey:'A',history:{record}});await tick();ui.d.getElementById('build-error-preview').click();await tick();assert.equal(ui.d.getElementById('build-error-dialog').open,false);
});

for(const [modified,status,label] of [[1000,'succeeded','최근 성공 빌드 시간대'],[2000,'succeeded','최근 성공 빌드 시간대'],[999,'succeeded','이전 APK'],[2001,'succeeded','관계 미확인'],[null,'succeeded','관계 미확인'],[1500,'failed','관계 미확인']])test('APK modification classification '+modified+' '+status,async()=>{
 const ui=setup({'build.inspect':()=>({...ui.inspected,apks:[{path:'a.apk',size:12,modified}],history:{record:{jobId:'job-1',task:'assembleDebug',status,startedAt:1000,updatedAt:2000,exitCode:0}}})});await open(ui);assert.match(ui.d.getElementById('build-apks').textContent,new RegExp(label));assert.match(ui.d.getElementById('build-apks').textContent,/수정 시각/);assert.match(ui.d.getElementById('build-artifact-basis').textContent,/수정 시각|산출물/);
});

test('clone form scopes current source, suppresses duplicates and keeps failure input',async()=>{
 let reject;const ui=setup({'build.clone':()=>new Promise((resolve,r)=>reject=r)});await open(ui);const d=ui.d;assert.ok(d.getElementById('build-clone'));d.getElementById('build-clone').click();await tick();assert.equal(d.getElementById('build-clone-dialog').open,true);d.getElementById('build-clone-name').value='copy';d.getElementById('build-clone-folder').value='copy-folder';const form=d.getElementById('build-clone-form');form.dispatchEvent(new ui.w.Event('submit',{cancelable:true}));form.dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();assert.equal(ui.calls.filter(m=>m.action==='build.clone').length,1);assert.deepEqual(ui.calls.find(m=>m.action==='build.clone').args,{workspaceKey:'A',name:'copy',folder:'copy-folder'});assert.equal(d.getElementById('build-clone-submit').disabled,true);reject(Error('folder exists'));await tick();assert.match(d.getElementById('build-clone-error').textContent,/folder exists/);assert.equal(d.getElementById('build-clone-name').value,'copy');assert.equal(d.getElementById('build-clone-submit').disabled,false);
});
for(const scenario of ['readonly','busy','indirect'])test('clone unavailable for '+scenario,async()=>{
 const ui=setup();Object.assign(ui.state,scenario==='readonly'?{permissions:'read-only'}:scenario==='busy'?{busy:true}:{directWorkspace:false});await open(ui);assert.ok(ui.d.getElementById('build-clone'));assert.equal(ui.d.getElementById('build-clone').disabled,true);
});
test('clone scope change and cancel never create a new project',async()=>{
 const ui=setup();await open(ui);const d=ui.d;d.getElementById('build-clone').click();await tick();d.querySelector('[data-close="build-clone-dialog"]').click();await tick();assert.equal(ui.calls.some(m=>m.action==='build.clone'),false);d.getElementById('build-clone').click();await tick();ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',selected:true,available:true},cwd:'/B'});d.getElementById('build-clone-form').dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();assert.equal(ui.calls.some(m=>m.action==='build.clone'),false);
});
test('clone success closes source panel without automatic build or installation',async()=>{
 const ui=setup({'build.clone':()=>({key:'new-copy'})});await open(ui);const d=ui.d;d.getElementById('build-clone').click();await tick();d.getElementById('build-clone-name').value='copy';d.getElementById('build-clone-folder').value='copy';d.getElementById('build-clone-form').dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();assert.equal(d.getElementById('build-clone-dialog').open,false);assert.equal(d.getElementById('build-dialog').open,false);assert.equal(ui.calls.some(m=>m.action==='build.run'||m.action==='build.install'),false);
});

for(const free of [0,1073741824,null])test('environment summary reports configured paths and available storage '+free,async()=>{
 const ui=setup({'build.inspect':()=>({...ui.inspected,usableSpaceBytes:free})});await open(ui);const summary=ui.d.getElementById('build-environment-summary');assert.ok(summary);assert.match(summary.textContent,/\/jdk/);assert.match(summary.textContent,/\/sdk/);assert.match(summary.textContent,/\/aapt2/);assert.match(summary.textContent,free===null?/공간 확인되지 않음/:free===0?/0 MiB/:/1024 MiB/);assert.match(summary.textContent,/환경 확인/);assert.match(summary.textContent,/실행 검증/);
});

for(const template of ['java','kotlin','compose'])test('new Android template selection sends explicit profile '+template,async()=>{
 const ui=setup({'projects.android.create':()=>({key:'created'})});await openNewAndroid(ui);fillNewAndroid(ui);const select=ui.d.getElementById('android-project-template');assert.ok(select);assert.equal(select.value,'java');select.value=template;ui.d.getElementById('android-project-form').dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();assert.equal(ui.calls.find(m=>m.action==='projects.android.create').args.template,template);assert.equal(ui.calls.some(m=>m.action==='build.run'),false);
});

test('history request remains stale after leaving and returning to the same conversation',async()=>{
 let release;const record={jobId:'aba-failure',task:'assembleDebug',status:'failed',output:'old failure'};
 const ui=setup({'build.inspect':()=>({...ui.inspected,history:{record}}),'build.history':()=>new Promise(r=>release=r)});await open(ui);ui.d.getElementById('build-history-open').click();await tick();ui.w.mobileCodexEvent('state',{...ui.state,threadId:'B'});ui.w.mobileCodexEvent('state',{...ui.state,threadId:'A'});release({workspaceKey:'A',history:{record}});await tick();assert.equal(ui.d.getElementById('build-error-preview').disabled,true);
});

for(const outcome of ['success','failure'])test('delayed clone '+outcome+' preserves another workspace build screen and draft',async()=>{
 let complete,reject;
 const ui=setup({
  'build.clone':()=>new Promise((resolve,fail)=>{complete=resolve;reject=fail;}),
  'build.inspect':m=>({...ui.inspected,workspaceKey:m.args.workspaceKey,project:'/'+m.args.workspaceKey})
 });
 await open(ui);const d=ui.d;
 d.getElementById('build-clone').click();await tick();
 d.getElementById('build-clone-name').value='copy A';d.getElementById('build-clone-folder').value='copy-a';
 d.getElementById('build-clone-form').dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();
 assert.equal(ui.calls.filter(m=>m.action==='build.clone').length,1);
 ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'B',name:'B',selected:true,available:true},threadId:'B',cwd:'/B'});
 d.getElementById('show-build').click();await tick();
 assert.equal(d.getElementById('build-project').textContent,'/B');
 d.getElementById('build-task').value=':other:assembleDebug';d.getElementById('build-java-home').value='/B/jdk';
 d.getElementById('prompt').value='B draft';d.getElementById('prompt').dispatchEvent(new ui.w.Event('input'));
 if(outcome==='success')complete({key:'copy-A'});else reject(Error('old clone failure'));
 await tick();
 assert.equal(d.getElementById('build-dialog').open,true);
 assert.equal(d.getElementById('build-clone-dialog').open,false);
 assert.equal(d.getElementById('build-project').textContent,'/B');
 assert.equal(d.getElementById('build-task').value,':other:assembleDebug');
 assert.equal(d.getElementById('build-java-home').value,'/B/jdk');assert.equal(d.getElementById('prompt').value,'B draft');
 assert.equal(d.getElementById('build-clone-error').textContent.includes('old clone failure'),false);
 assert.equal(ui.calls.some(m=>m.action==='build.run'||m.action==='build.install'),false);
});

test('clone workspace publish before RPC reply completes normally without extra creation',async()=>{
 let complete;const ui=setup({
  'build.clone':()=>new Promise(resolve=>complete=resolve),
  'build.inspect':m=>({...ui.inspected,workspaceKey:m.args.workspaceKey,project:'/'+m.args.workspaceKey})
 });
 await open(ui);const d=ui.d;d.getElementById('build-clone').click();await tick();
 d.getElementById('build-clone-name').value='copy';d.getElementById('build-clone-folder').value='copy';
 d.getElementById('build-clone-form').dispatchEvent(new ui.w.Event('submit',{cancelable:true}));await tick();
 ui.w.mobileCodexEvent('state',{...ui.state,workspace:{key:'copy',name:'copy',selected:true,available:true},threadId:'',cwd:'/copy'});
 assert.equal(d.getElementById('build-clone-dialog').open,false);
 complete({key:'copy'});await tick();assert.equal(d.getElementById('build-dialog').open,false);
 d.getElementById('show-build').click();await tick();assert.equal(d.getElementById('build-project').textContent,'/copy');
 assert.equal(d.getElementById('build-clone').disabled,false);
 assert.equal(ui.calls.filter(m=>m.action==='build.clone').length,1);
 assert.equal(ui.calls.some(m=>m.action==='build.run'||m.action==='build.install'),false);
});
test('build inspection spinner clears on failure and terminal spinner follows live state',async()=>{let reject;const ui=setup({'build.inspect':()=>new Promise((_,r)=>reject=r)});await open(ui);assert.equal(ui.d.getElementById('build-status').getAttribute('aria-busy'),'true');reject(Error('inspection failed'));await tick();assert.equal(ui.d.getElementById('build-status').getAttribute('aria-busy'),'false');ui.w.mobileCodexEvent('terminal.state',{id:'live',revision:10,running:true,output:'running'});assert.equal(ui.d.getElementById('terminal-status').getAttribute('aria-busy'),'true');ui.w.mobileCodexEvent('terminal.state',{id:'live',revision:11,running:false,exitCode:1});assert.equal(ui.d.getElementById('terminal-status').getAttribute('aria-busy'),'false');});
test('APK export status spinner clears after cancellation',async()=>{let release;const ui=setup({'build.export':()=>new Promise(r=>release=r)});await open(ui);ui.d.querySelector('[data-apk-save]').click();await tick();assert.equal(ui.d.getElementById('build-status').getAttribute('aria-busy'),'true');release({cancelled:true});await tick();assert.equal(ui.d.getElementById('build-status').getAttribute('aria-busy'),'false');});
