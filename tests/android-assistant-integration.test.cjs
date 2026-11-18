const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');const {JSDOM}=require('jsdom');
const root='app/src/main/assets/web/';const tick=()=>new Promise(r=>setTimeout(r,30));
test('real app wires assistant inspection and explicit verification into terminal',async()=>{
 const dom=new JSDOM(fs.readFileSync(root+'index.html','utf8'),{url:'https://appassets.androidplatform.net/index.html',runScripts:'outside-only'});
 try{
 const w=dom.window,calls=[];w.matchMedia=()=>({matches:false,addEventListener(){}});
 w.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};w.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');this.dispatchEvent(new w.Event('close'));};
 const state={ready:false,busy:false,models:[],messages:[],sessions:[],account:{},workspace:{key:'A',selected:true,name:'A',available:true},threadId:'A',cwd:'/A',permissions:'workspace-write',directWorkspace:true,linux:{enabled:true},terminal:{running:false}};
 w.Native={locale:()=>JSON.stringify({choice:'ko',systemLanguage:'ko'}),textSize:()=>JSON.stringify({percent:100}),postMessage(raw){const m=JSON.parse(raw);calls.push(m);queueMicrotask(()=>{let result={};if(m.action==='state')result=state;if(m.action==='android.inspect')result={workspaceKey:'A',sdk:{min:29,target:35,compile:35},linux:{enabled:true},catalog:[],dependencies:[{configuration:'implementation',coordinate:'com.squareup.okhttp3:okhttp:4.12.0'}],issues:[],validation:{requestedApis:[29,35],records:[],matrix:[]}};if(m.action==='android.verify')result={revision:1,id:'validation-1',running:true,output:'checking'};w.mobileCodexEvent('response',{id:m.id,result});});}};
 for(const f of ['translations.js','locale.js','ui-core.js','android-assistant.js','app.js'])w.eval(fs.readFileSync(root+f,'utf8'));
 await tick();w.document.getElementById('build-assistant-open').click();await tick();
 assert.ok(calls.some(m=>m.action==='android.inspect'&&m.args.workspaceKey==='A'),'inspection must reach native bridge');
 assert.match(w.document.getElementById('aa-diagnostics').textContent,/okhttp:4.12.0/);
 assert.equal(calls.some(m=>m.action==='android.verify'),false,'opening cannot run commands');
 w.document.getElementById('aa-local').click();await tick();
 assert.equal(calls.filter(m=>m.action==='android.verify').length,1);
 assert.equal(w.document.getElementById('terminal-dialog').open,true);
 assert.match(w.document.getElementById('terminal-output').textContent,/checking/);
 }finally{dom.window.close();}
});
