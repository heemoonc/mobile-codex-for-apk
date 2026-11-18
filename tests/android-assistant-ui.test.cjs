const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {JSDOM} = require('jsdom');
const base = 'app/src/main/assets/web/';
function setup(call) {
 const dom = new JSDOM(fs.readFileSync(base+'index.html','utf8'), {runScripts:'outside-only'});
 const notices=[];
 const state={workspace:{key:'A'},directWorkspace:true,permissions:'full-access',linux:{enabled:true}};
 assert.ok(fs.existsSync(base+'android-assistant.js'),'assistant module must exist');
 dom.window.eval(fs.readFileSync(base+'android-assistant.js','utf8'));
 dom.window.AndroidAssistantUI.init({call,getState:()=>state,t:s=>s,show:id=>dom.window.document.getElementById(id).setAttribute('open',''),close:()=>{},toast:s=>notices.push(s),openTerminal:()=>{}});
 return {dom,state,notices,$:id=>dom.window.document.getElementById(id),tick:()=>new Promise(r=>setTimeout(r,0))};
}
const snapshot={workspaceKey:'A',fingerprint:'f1',sdk:{min:23,target:35,compile:35},tools:{kotlin:'2.0'},catalog:[{id:'room',name:'Room',version:'2.7',coordinates:['androidx.room:room-runtime:2.7']}],validation:{matrix:[{api:29,status:'unverified'}]},issues:[]};
test('inspection is read-only and never applies changes or runs tests automatically',async()=>{
 const calls=[];const x=setup(async(a,p)=>{calls.push([a,p]);return snapshot});
 x.$('build-assistant-open').click();await x.tick();assert.deepEqual(calls.map(c=>c[0]),['android.inspect']);
 assert.match(x.$('aa-diagnostics').textContent,/23/);assert.equal(x.$('aa-apply').disabled,true);
});
test('apply requires a preview fingerprint and preserves exact preview text safely',async()=>{
 const calls=[];const x=setup(async(a,p)=>{calls.push([a,p]);if(a==='android.preview')return {workspaceKey:'A',fingerprint:'f1',canApply:true,changes:[{path:'build.gradle',before:'old <script>',after:'new'}]};return snapshot});
 x.$('build-assistant-open').click();await x.tick();x.$('aa-library-preview').click();await x.tick();
 assert.match(x.$('aa-preview').textContent,/old <script>/);assert.equal(x.$('aa-preview').querySelector('script'),null);
 x.$('aa-apply').click();await x.tick();const apply=calls.find(c=>c[0]==='android.apply');assert.equal(apply[1].fingerprint,'f1');assert.equal(apply[1].request.libraryId,'room');
});
test('late results from an earlier workspace including ABA are ignored',async()=>{
 let finish;const x=setup(()=>new Promise(r=>finish=r));x.$('build-assistant-open').click();x.state.workspace.key='B';x.dom.window.AndroidAssistantUI.invalidate();x.state.workspace.key='A';x.dom.window.AndroidAssistantUI.invalidate();finish(snapshot);await x.tick();assert.equal(x.$('aa-diagnostics').textContent,'');assert.equal(x.$('aa-apply').disabled,true);
});
test('read-only projects cannot apply or execute but can inspect',async()=>{
 const x=setup(async()=>snapshot);x.state.permissions='read-only';x.$('build-assistant-open').click();await x.tick();assert.equal(x.$('aa-local').disabled,true);assert.equal(x.$('aa-refresh').disabled,false);assert.equal(x.$('aa-library-preview').disabled,false);
});
test('edited SDK inputs invalidate preview and evidence imports are explicit JSON only',async()=>{
 const calls=[];const x=setup(async(a,p)=>{calls.push([a,p]);return a==='android.preview'?{workspaceKey:'A',fingerprint:'f1',canApply:true,changes:[]}:snapshot});x.$('build-assistant-open').click();await x.tick();x.$('aa-sdk-preview').click();await x.tick();assert.equal(x.$('aa-apply').disabled,false);x.$('aa-sdk-min').value='24';x.$('aa-sdk-min').dispatchEvent(new x.dom.window.Event('input'));assert.equal(x.$('aa-apply').disabled,true);
 x.$('aa-evidence').value='invalid';x.$('aa-import').click();await x.tick();assert.equal(calls.some(c=>c[0]==='android.evidence.import'),false);
});
test('general workspace empty key works and external execution requires explicit click with numeric API',async()=>{
 const calls=[];const x=setup(async(a,p)=>{calls.push([a,p]);return {...snapshot,workspaceKey:''}});x.state.workspace.key='';x.$('build-assistant-open').click();await x.tick();assert.equal(calls.length,1);assert.equal(calls[0][1].workspaceKey,'');
 x.$('aa-device-api').value='35';x.$('aa-adb').click();await x.tick();assert.equal(calls[1][0],'android.verify');assert.equal(calls[1][1].options.api,35);
});
test('account and thread switches and closing invalidate preview',async()=>{
 for(const change of [x=>x.state.accountId='other',x=>x.state.threadId='other',x=>x.$('android-assistant-dialog').dispatchEvent(new x.dom.window.Event('close'))]){
 const x=setup(async a=>a==='android.preview'?{fingerprint:'f',canApply:true}:snapshot);x.$('build-assistant-open').click();await x.tick();x.$('aa-library-preview').click();await x.tick();assert.equal(x.$('aa-apply').disabled,false);change(x);x.dom.window.AndroidAssistantUI.invalidate();assert.equal(x.$('aa-apply').disabled,true);
 }
});
test('terminal running and absent Linux prevent execution; imported pass is labeled external',async()=>{
 const x=setup(async()=>({...snapshot,validation:{matrix:[{api:35,status:'passed',imported:true}]}}));x.state.linux.enabled=false;x.$('build-assistant-open').click();await x.tick();assert.equal(x.$('aa-local').disabled,true);assert.match(x.$('aa-matrix').textContent,/직접 검증하지 않음/);
 x.state.linux.enabled=true;x.state.terminal={running:true};x.dom.window.AndroidAssistantUI.invalidate();assert.equal(x.$('aa-local').disabled,true);assert.equal(x.$('aa-refresh').disabled,true);
});
test('invalid API targets never reach native bridge and assistant loads before app',async()=>{
 const calls=[];const x=setup(async(a,p)=>{calls.push(a);return snapshot});x.$('build-assistant-open').click();await x.tick();for(const v of ['15','101','16, 200']){x.$('aa-apis').value=v;x.$('aa-save-matrix').click();}assert.deepEqual(calls,['android.inspect']);
 const html=fs.readFileSync(base+'index.html','utf8');assert.ok(html.indexOf('src="android-assistant.js"')<html.indexOf('src="app.js"'));
});
test('external APK success remains visibly separate from current-source proof',async()=>{
 const x=setup(async()=>({...snapshot,validation:{matrix:[{api:35,status:'artifact-passed'}]}}));
 x.$('build-assistant-open').click();await x.tick();
 assert.match(x.$('aa-matrix').textContent,/APK 통과 · 소스 연결 미확인/);
 assert.doesNotMatch(x.$('aa-matrix').textContent,/artifact-passed/);
 x.dom.window.close();
});

test('execution errors are announced immediately even below the status paragraph',async()=>{
 const x=setup(async a=>{if(a==='android.verify')throw Error('Missing JDK');return snapshot;});
 x.$('build-assistant-open').click();await x.tick();let moved=false;
 x.$('aa-status').scrollIntoView=()=>{moved=true};x.$('aa-local').click();await x.tick();
 assert.deepEqual(x.notices,['Missing JDK']);assert.equal(moved,true);assert.equal(x.$('aa-local').disabled,false);x.dom.window.close();
});
test('unavailable execution explains Linux requirement beside run buttons',async()=>{
 const x=setup(async()=>({...snapshot,linux:{enabled:false}}));x.$('build-assistant-open').click();await x.tick();
 assert.equal(x.$('aa-local').disabled,true);assert.match(x.$('aa-execution-help')?.textContent || '',/Linux/);x.dom.window.close();
});

test('simulation preview is explicit, fingerprint gated and separate from device proof',async()=>{
 const calls=[];const x=setup(async(a,p)=>{calls.push([a,p]);return a==='android.simulation.preview'?{fingerprint:'sim-f',canApply:true,changes:[{path:'test.java',before:'<old>',after:'baseline'}]}:{...snapshot,validation:{matrix:[{api:29,status:'unverified'}],mockMatrix:[{api:29,status:'mock-passed'},{api:35,status:'mock-failed'}]}}});
 x.$('build-assistant-open').click();await x.tick();assert.deepEqual(calls.map(c=>c[0]),['android.inspect']);assert.deepEqual(Array.from(x.$('aa-mock-api').options,o=>Number(o.value)),[29,30,31,32,33,34,35]);
 assert.match(x.$('aa-mock-matrix').textContent,/모의 검사 통과/);assert.doesNotMatch(x.$('aa-matrix').textContent,/모의 검사 통과/);
 assert.equal(x.$('aa-simulation-apply').disabled,true);x.$('aa-simulation-preview').click();await x.tick();assert.match(x.$('aa-simulation-preview-output').textContent,/<old>/);
 x.$('aa-simulation-apply').click();await x.tick();assert.equal(calls.at(-1)[0],'android.simulation.apply');assert.equal(calls.at(-1)[1].fingerprint,'sim-f');
 x.$('aa-mock-api').value='35';x.$('aa-mock').click();await x.tick();assert.equal(calls.at(-1)[1].kind,'mock');assert.equal(calls.at(-1)[1].options.api,35);
 x.$('aa-emulator-probe').click();await x.tick();assert.equal(calls.at(-1)[1].kind,'emulator-probe');x.dom.window.close();
});
test('simulation applies neither stale scope nor read-only, and Linux off prevents execution',async()=>{
 let finish;const calls=[];const x=setup(async(a,p)=>{calls.push(a);if(a==='android.simulation.preview')return new Promise(r=>finish=r);return snapshot;});
 x.$('build-assistant-open').click();await x.tick();x.$('aa-simulation-preview').click();x.state.workspace.key='B';x.dom.window.AndroidAssistantUI.invalidate();finish({fingerprint:'old',canApply:true});await x.tick();assert.equal(x.$('aa-simulation-apply').disabled,true);assert.equal(x.$('aa-simulation-preview-output').textContent,'');
 x.state.permissions='read-only';x.state.linux.enabled=false;x.dom.window.AndroidAssistantUI.invalidate();assert.equal(x.$('aa-mock').disabled,true);assert.equal(x.$('aa-emulator-probe').disabled,true);assert.equal(x.$('aa-simulation-apply').disabled,true);x.dom.window.close();
});
test('simulation preparation rejects unsafe plans and pending input edits',async()=>{
 let finish;const calls=[];const x=setup(async(a,p)=>{calls.push(a);if(a==='android.simulation.preview')return new Promise(r=>finish=r);return snapshot;});
 x.$('build-assistant-open').click();await x.tick();x.$('aa-simulation-preview').click();x.$('aa-sdk-min').dispatchEvent(new x.dom.window.Event('input'));finish({fingerprint:'f',canApply:true});await x.tick();assert.equal(x.$('aa-simulation-apply').disabled,true);
 x.$('aa-simulation-preview').click();finish({fingerprint:'f',canApply:false});await x.tick();assert.equal(x.$('aa-simulation-apply').disabled,true);
 x.$('aa-simulation-preview').click();finish({canApply:true});await x.tick();assert.equal(x.$('aa-simulation-apply').disabled,true);
 x.$('aa-simulation-preview').click();finish({fingerprint:'f',canApply:true});await x.tick();x.state.permissions='read-only';x.dom.window.AndroidAssistantUI.invalidate();x.$('aa-simulation-apply').click();assert.equal(calls.includes('android.simulation.apply'),false);assert.equal(x.$('aa-mock').disabled,true);x.dom.window.close();
});
