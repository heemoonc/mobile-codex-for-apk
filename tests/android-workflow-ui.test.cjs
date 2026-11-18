const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const {JSDOM}=require('jsdom');
const base='app/src/main/assets/web/';const fp='a'.repeat(64),config='b'.repeat(64);
function setup(overrides={}){
 const dom=new JSDOM(fs.readFileSync(base+'index.html','utf8'),{url:'https://appassets.androidplatform.net/index.html',runScripts:'outside-only'});
 const w=dom.window,calls=[],records=[],terminals=new Map();let serial=0;
 const state={workspace:{key:'A',selected:true,name:'Demo'},account:{id:'one'},androidAccountScope:'one',permissions:'workspace-write',directWorkspace:true,linux:{enabled:true},terminal:{running:false}};
 let snapshot={workspaceKey:'A',sdk:{min:29,target:35,compile:35},modulePath:'app/build.gradle',linux:{enabled:true},simulation:{prepared:true},workflow:{configuration:config},validation:{fingerprint:fp,records}};
 const call=async(action,args={})=>{calls.push([action,args]);if(overrides.call){const r=await overrides.call(action,args,{state,records,snapshot,terminals});if(r!==undefined)return r;}
  if(action==='android.inspect')return {...snapshot,workspaceKey:state.workspace.key,validation:{...snapshot.validation,records:[...records]}};
  if(action==='build.inspect')return {workspaceKey:state.workspace.key,apks:[]};
  if(action==='android.verify'){
   const id='t'+(++serial),validationId='v'+serial;const mock=args.kind==='mock',env=args.kind==='workflow-environment';
   records.unshift({id:validationId,kind:args.kind,scope:mock?'baseline':'workflow',evidence:mock?'simulation':env?'environment':'local',status:mock?'mock-passed':env?'checked':'passed',sourceVerified:!mock&&!env,requestedApi:args.options?.api,api:args.options?.api,fingerprint:fp,configuration:config,exitCode:0,tests:mock?2:3,failed:0,skipped:0,completedAt:Date.now()});
   terminals.set(id,{id,revision:serial,running:false,exitCode:0,output:'BUILD SUCCESSFUL'});return {id,validationId,running:true,revision:serial};
  }
  if(action==='terminal.state')return terminals.get('t'+serial)||{id:'',running:false,revision:0};
  if(action==='terminal.stop'){const t=terminals.get(args.id);if(t){t.running=false;t.stopping=true;t.exitCode=130;}return t;}
  return {};
 };
 assert.ok(fs.existsSync(base+'android-workflow.js'),'workflow UI module must exist');w.eval(fs.readFileSync(base+'android-workflow.js','utf8'));
 w.AndroidWorkflowUI.init({call,getState:()=>state,t:overrides.t|| (s=>s),show:id=>w.document.getElementById(id).setAttribute('open',''),close:id=>w.document.getElementById(id)?.removeAttribute('open'),toast:()=>{},renderTerminal:()=>{},pollInterval:0});
 const $=id=>w.document.getElementById(id),tick=()=>new Promise(r=>setTimeout(r,5));
 async function settle(){for(let i=0;i<120;i++){await tick();if(!w.AndroidWorkflowUI.running())return;}throw Error('queue did not settle');}
 return {dom,w,state,calls,records,terminals,$,tick,settle,setSnapshot:s=>{snapshot=s},snapshot};
}
async function open(x){x.$('show-workflow').click();await x.tick();}
test('guide opens read-only and manual is available without executing commands',async()=>{
 const x=setup();try{await open(x);assert.deepEqual(x.calls.map(c=>c[0]).sort(),['android.inspect','build.inspect']);assert.match(x.$('gw-steps').textContent,/프로젝트|코드/);x.$('gw-tab-manual').click();assert.equal(x.$('gw-panel-manual').hidden,false);assert.match(x.$('gw-manual').textContent,/0개|NO-SOURCE/);assert.equal(x.calls.some(c=>c[0]==='android.verify'),false);}finally{x.dom.window.close();}
});
test('explicit automatic run serializes environment, real-count checks and selected baseline APIs',async()=>{
 const x=setup();try{await open(x);x.$('gw-include-mock').checked=true;x.$('gw-run').click();await x.settle();assert.deepEqual(x.calls.filter(c=>c[0]==='android.verify').map(c=>[c[1].kind,c[1].options?.api]),[['workflow-environment',undefined],['workflow-checks',undefined],['mock',29],['mock',35]]);assert.match(x.$('gw-status').textContent,/완료|통과/);assert.equal(x.calls.some(c=>/apply|install|cloud/.test(c[0])),false);}finally{x.dom.window.close();}
});
test('zero tests and missing exact execution records halt follow-up',async()=>{
 for(const mode of ['zero','missing']){const x=setup({call(action,args,{records}){if(action==='android.inspect'&&records[0]?.kind==='workflow-checks'){if(mode==='zero')records[0].tests=0;else records.length=0;}}});try{await open(x);x.$('gw-include-mock').checked=true;x.$('gw-run').click();await x.settle();assert.equal(x.calls.filter(c=>c[0]==='android.verify').length,2);assert.doesNotMatch(x.$('gw-status').textContent,/자동 검증 완료/);assert.doesNotMatch(x.$('gw-queue').lastChild.textContent,/실행 중/);}finally{x.dom.window.close();}}
});
test('workspace/account changes and mismatched terminal IDs cannot advance the queue',async()=>{
 for(const mode of ['workspace','account','terminal']){let x; x=setup({call(action,args,{state}){if(action==='terminal.state'){if(mode==='workspace'){state.workspace.key='B';x.w.AndroidWorkflowUI.invalidate();}else if(mode==='account'){state.androidAccountScope='two';x.w.AndroidWorkflowUI.invalidate();}else return {id:'other',running:false,exitCode:0};}}});try{await open(x);x.$('gw-run').click();await x.settle();assert.equal(x.calls.filter(c=>c[0]==='android.verify').length,1);}finally{x.dom.window.close();}}
});
test('manual confirmations are visibly self reported and invalidated by source changes',async()=>{
 const x=setup();try{await open(x);x.$('gw-ack-device').click();assert.match(x.$('gw-steps').textContent,/직접 확인|사용자 확인/);assert.equal(x.calls.some(c=>c[0]==='android.verify'),false);x.setSnapshot({...x.snapshot,validation:{fingerprint:'c'.repeat(64),records:[]}});x.$('gw-refresh').click();await x.tick();assert.equal(x.$('gw-ack-device').checked,false);}finally{x.dom.window.close();}
});
test('read-only, missing Linux and unrelated running terminal disable automation',async()=>{
 for(const change of [s=>s.permissions='read-only',s=>s.linux.enabled=false,s=>s.terminal={id:'other',running:true}]){const x=setup();try{change(x.state);await open(x);assert.equal(x.$('gw-run').disabled,true);assert.equal(x.$('gw-tab-manual').disabled,false);}finally{x.dom.window.close();}}
});
test('stop during native launch stops only the returned owned terminal and schedules no follow-up',async()=>{
 let release;const x=setup({call(action){if(action==='android.verify')return new Promise(r=>release=r);}});try{await open(x);x.$('gw-run').click();await x.tick();x.$('gw-stop').click();release({id:'owned',validationId:'owned-record',running:true,revision:1});await x.settle();assert.deepEqual(x.calls.filter(c=>c[0]==='terminal.stop').map(c=>c[1].id),['owned']);assert.equal(x.calls.filter(c=>c[0]==='android.verify').length,1);}finally{x.dom.window.close();}
});
test('configuration changes during a run stop subsequent checks',async()=>{
 const x=setup({call(action,args,{snapshot,records}){if(action==='android.inspect'&&records.length)snapshot.workflow.configuration='c'.repeat(64);}});
 try{await open(x);x.$('gw-run').click();await x.settle();assert.equal(x.calls.filter(c=>c[0]==='android.verify').length,1);assert.doesNotMatch(x.$('gw-status').textContent,/자동 검증 완료/);}finally{x.dom.window.close();}
});
test('previous in-flight state warns without automatically restarting commands',async()=>{
 const x=setup();try{x.w.localStorage.setItem('android-workflow:'+JSON.stringify(['A','one']),JSON.stringify({inFlight:true}));await open(x);assert.match(x.$('gw-warning').textContent,/중단/);assert.equal(x.calls.some(c=>c[0]==='android.verify'),false);}finally{x.dom.window.close();}
});
test('records for an old source or missing tool configuration cannot advance',async()=>{
 for(const mode of ['source','config']){const x=setup({call(action,args,{records}){if(action==='android.inspect'&&records.length){if(mode==='source')records[0].fingerprint='c'.repeat(64);else delete records[0].configuration;}}});try{await open(x);x.$('gw-run').click();await x.settle();assert.equal(x.calls.filter(c=>c[0]==='android.verify').length,1);}finally{x.dom.window.close();}}
});
test('next action follows manual code confirmation, automated proof and APK handoff without claiming installation',async()=>{
 const x=setup();try{await open(x);assert.match(x.$('gw-next').textContent,/코드·라이브러리/);x.$('gw-ack-code').click();assert.match(x.$('gw-next').textContent,/자동 검증/);x.$('gw-next-action').click();assert.equal(x.$('gw-panel-auto').hidden,false);x.$('gw-run').click();await x.settle();assert.match(x.$('gw-next').textContent,/APK/);assert.equal(x.$('gw-ack-device').checked,false);assert.equal(x.calls.some(c=>c[0].includes('install')),false);}finally{x.dom.window.close();}
});
test('zero-test failure explains the repair and links to the relevant manual',async()=>{
 const x=setup({call(action,args,{records}){if(action==='android.inspect'&&records[0]?.kind==='workflow-checks')records[0].tests=0;}});try{await open(x);x.$('gw-run').click();await x.settle();assert.equal(x.$('gw-failure').hidden,false);assert.match(x.$('gw-failure-text').textContent,/src\/test|테스트 소스/);x.$('gw-failure-manual').click();assert.equal(x.$('gw-panel-manual').hidden,false);assert.equal(x.w.document.querySelector('#gw-manual [data-guide="checks"]').open,true);}finally{x.dom.window.close();}
});
test('English live labels are translated before status composition',async()=>{
 const dictionary={'환경 확인':'Environment','빌드·Lint·단위 테스트':'Build checks','실행 중…':'Running','통과':'Passed'};
 const headlines=[];let x;x=setup({t:s=>dictionary[s]||s,call(action){if(action==='terminal.state')headlines.push(x.$('gw-status').textContent);}});try{await open(x);x.$('gw-run').click();await x.settle();assert.ok(headlines.length>=2);assert.ok(headlines.every(s=>!s.includes('환경 확인')&&!s.includes('빌드·Lint·단위 테스트')),JSON.stringify(headlines));assert.match(x.$('gw-queue').textContent,/Environment · Passed/);assert.doesNotMatch(x.$('gw-queue').textContent,/환경 확인|빌드·Lint·단위 테스트/);}finally{x.dom.window.close();}
});
test('opening guide closes sibling entry dialogs and reopening preserves active headline',async()=>{
 let release;const x=setup({call(action){if(action==='android.verify')return new Promise(r=>release=r);}});
 try{for(const id of ['tool-menu-dialog','build-dialog','android-assistant-dialog'])x.$(id).setAttribute('open','');await open(x);for(const id of ['tool-menu-dialog','build-dialog','android-assistant-dialog'])assert.equal(x.$(id).hasAttribute('open'),false);x.$('gw-run').click();await x.tick();const before=x.$('gw-status').textContent;x.$('android-workflow-dialog').removeAttribute('open');await open(x);assert.equal(x.$('gw-status').textContent,before);x.$('gw-stop').click();release({id:'owned',validationId:'owned-record',running:true,revision:1});await x.settle();}finally{x.dom.window.close();}
});
test('no Android module points to project creation instead of suggesting a build',async()=>{
 const x=setup();try{x.setSnapshot({...x.snapshot,modulePath:''});await open(x);assert.match(x.$('gw-next').textContent,/프로젝트/);assert.match(x.$('gw-next-action').textContent,/프로젝트/);}finally{x.dom.window.close();}
});
test('nonzero test exit uses the matching native failure record for repair guidance',async()=>{
 const x=setup({call(action,args,{records,terminals}){if(action==='terminal.state'&&records[0]?.kind==='workflow-checks'){records[0].status='failed';records[0].failed=1;records[0].exitCode=1;const t=[...terminals.values()].at(-1);return {...t,exitCode:1};}}});try{await open(x);x.$('gw-run').click();await x.settle();assert.match(x.$('gw-failure-text').textContent,/실패한 테스트/);assert.equal(x.calls.filter(c=>c[0]==='android.verify').length,2);}finally{x.dom.window.close();}
});
test('opting into mocks with no selected API asks for selection rather than APK handoff',async()=>{
 const x=setup();try{await open(x);x.$('gw-ack-code').click();x.$('gw-run').click();await x.settle();for(const i of x.$('gw-api-list').querySelectorAll('input'))i.checked=false;x.$('gw-include-mock').checked=true;x.$('gw-include-mock').dispatchEvent(new x.w.Event('change'));assert.match(x.$('gw-next').textContent,/API.*선택/);}finally{x.dom.window.close();}
});
test('terminal failure cannot borrow a contradictory passing record',async()=>{
 const x=setup({call(action,args,{records,terminals}){if(action==='terminal.state'&&records[0]?.kind==='workflow-checks')return {...[...terminals.values()].at(-1),exitCode:1};}});try{await open(x);x.$('gw-include-mock').checked=true;x.$('gw-run').click();await x.settle();assert.equal(x.calls.filter(c=>c[0]==='android.verify').length,2);assert.doesNotMatch(x.$('gw-status').textContent,/자동 검증 완료/);}finally{x.dom.window.close();}
});
test('successful command without test counts explains NO-SOURCE and test setup without asserting zero execution',async()=>{
 const x=setup({call(action,args,{records}){if(action==='android.inspect'&&records[0]?.kind==='workflow-checks'){records[0].status='unverified';records[0].tests=-1;}}});try{await open(x);x.$('gw-run').click();await x.settle();assert.match(x.$('gw-failure-text').textContent,/NO-SOURCE/);assert.match(x.$('gw-failure-text').textContent,/app\/src\/test/);assert.doesNotMatch(x.$('gw-failure-text').textContent,/0개입니다/);}finally{x.dom.window.close();}
});
test('project changes reset optional mock choices and discard the previous next-action link',async()=>{
 const x=setup();try{await open(x);x.$('gw-include-mock').checked=true;for(const i of x.$('gw-api-list').querySelectorAll('input'))i.checked=true;x.state.workspace.key='B';x.w.AndroidWorkflowUI.invalidate();assert.equal(x.$('gw-next-action').disabled,true);await open(x);assert.equal(x.$('gw-include-mock').checked,false);assert.deepEqual([...x.$('gw-api-list').querySelectorAll('input:checked')].map(i=>Number(i.value)),[29,35]);}finally{x.dom.window.close();}
});
test('automatic workflow spinner follows actual running state',async()=>{let release;const x=setup({call:a=>a==='android.verify'?new Promise((_,reject)=>release=()=>reject(Error('stopped'))):undefined});try{await open(x);assert.equal(x.$('gw-spinner').hidden,true);x.$('gw-run').click();await x.tick();assert.equal(x.$('gw-spinner').hidden,false);assert.equal(x.$('gw-status-row').getAttribute('aria-busy'),'true');release();await x.settle();assert.equal(x.$('gw-spinner').hidden,true);assert.equal(x.$('gw-status-row').getAttribute('aria-busy'),'false');}finally{x.dom.window.close()}});
