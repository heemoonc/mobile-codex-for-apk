const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {JSDOM}=require('jsdom');
const root='app/src/main/assets/web/';
const tick=()=>new Promise(r=>setTimeout(r,20));
async function setup(t,{account={},accounts=[],fail=false,authState}={}) {
 const dom=new JSDOM(fs.readFileSync(root+'index.html','utf8'),{url:'https://appassets.androidplatform.net/index.html',runScripts:'outside-only'});t.after(()=>dom.window.close());
 const w=dom.window,calls=[];w.matchMedia=()=>({matches:false,addEventListener(){}});
 w.HTMLDialogElement.prototype.showModal=function(){this.setAttribute('open','');};w.HTMLDialogElement.prototype.close=function(){this.removeAttribute('open');this.dispatchEvent(new w.Event('close'));};
 const state={ready:true,busy:false,models:[],messages:[],sessions:[],account,accounts,authState:authState || (account.type?'signed_in':'signed_out'),workspace:{key:'A',selected:false,available:true},threadId:'A',cwd:'/A'};
 w.Native={locale:()=>JSON.stringify({choice:'ko',systemLanguage:'ko'}),textSize:()=>JSON.stringify({percent:100}),postMessage(raw){const m=JSON.parse(raw);calls.push(m);queueMicrotask(()=>{const auth=m.action==='auth.add'||m.action==='auth.login';w.mobileCodexEvent('response',{id:m.id,...(auth&&fail?{error:'합성 로그인 오류'}:{result:m.action==='state'?state:auth?{verificationUrl:'https://auth.openai.com/codex/device',userCode:'TEST-ONLY',loginId:'synthetic-login'}:{}})});});}};
 for(const f of ['translations.js','locale.js','ui-core.js','app.js'])w.eval(fs.readFileSync(root+f,'utf8'));
 await tick();return {w,d:w.document,calls};
}
test('account settings first connection uses primary login without requiring an existing profile',async t=>{
 const ui=await setup(t);ui.d.getElementById('account-add').click();await tick();
 assert.ok(ui.calls.find(m=>m.action==='auth.login'));assert.equal(ui.calls.filter(m=>m.action==='auth.add').length,0);
 assert.equal(ui.d.getElementById('device-code').textContent,'TEST-ONLY');assert.equal(ui.d.getElementById('open-login').disabled,false);
});
test('additional account preserves isolated add-login with an existing profile',async t=>{
 const ui=await setup(t,{account:{type:'chatgpt',email:'synthetic@example.test'},accounts:[{key:'synthetic-profile',active:true}]});ui.d.getElementById('account-add').click();await tick();
 assert.ok(ui.calls.find(m=>m.action==='auth.add'));assert.equal(ui.calls.filter(m=>m.action==='auth.login').length,0);
});
test('login failure replaces preparation message and keeps browser disabled',async t=>{
 const ui=await setup(t,{fail:true});ui.d.getElementById('account-add').click();await tick();
 assert.doesNotMatch(ui.d.getElementById('device-code').textContent,/준비 중/);assert.match(ui.d.getElementById('login-help').textContent,/합성 로그인 오류/);assert.equal(ui.d.getElementById('open-login').disabled,true);
});

test('unknown account state preserves add intent until native credential check',async t=>{
 const ui=await setup(t,{authState:'unknown'});ui.d.getElementById('account-add').click();await tick();
 assert.ok(ui.calls.find(m=>m.action==='auth.add'));
});
