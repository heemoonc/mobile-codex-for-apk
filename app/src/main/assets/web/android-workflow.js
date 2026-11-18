/* Guided development. Native records are the authority; acknowledgements are self reports. */
(() => {
'use strict';
let api, snapshot=null, scope='', busy=false, stopped=false, owned='', epoch=0, headline='';
const $=id=>document.getElementById(id), tr=s=>api.t(s);
const context=()=>JSON.stringify([api.getState().workspace?.key||'',api.getState().androidAccountScope||api.getState().account?.id||'']);
const fingerprint=()=>snapshot?.validation?.fingerprint||'';
const signature=()=>JSON.stringify([fingerprint(),snapshot?.workflow?.configuration||'']);
const storageKey=()=> 'android-workflow:'+scope;
const node=(tag,text)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=tr(String(text));return n;};
const pause=()=>new Promise(r=>setTimeout(r,api.pollInterval??1000));
function saved(){try{return JSON.parse(localStorage.getItem(storageKey())||'{}');}catch{return {};}}
function save(v){try{localStorage.setItem(storageKey(),JSON.stringify(v));}catch{}}
function status(s){headline=tr(s);$('gw-status').textContent=headline;}
function tab(name){for(const t of ['stages','auto','manual']){$('gw-panel-'+t).hidden=t!==name;$('gw-tab-'+t).setAttribute('aria-pressed',String(t===name));}}
function ready(){const s=api.getState();return !!snapshot?.modulePath && s.permissions!=='read-only'&&s.directWorkspace!==false&&s.linux?.enabled!==false&&snapshot?.linux?.enabled!==false&&!s.busy&&(!s.terminal?.running||s.terminal.id===owned)&&scope===context();}
function controls(){ $('gw-spinner').hidden=!busy;$('gw-status-row').setAttribute('aria-busy',String(busy)); $('gw-run').disabled=busy||!ready();$('gw-stop').disabled=!busy;$('gw-refresh').disabled=busy; $('gw-prepare-mock').disabled=busy||!ready();for(const n of $('gw-panel-auto').querySelectorAll('input'))n.disabled=busy;
 $('gw-run-help').textContent=tr(busy?'자동 검증 중입니다. 중지하면 다음 단계를 실행하지 않습니다.':!ready()?'쓰기 가능한 Android 프로젝트와 Linux가 필요합니다. 진행 중인 터미널을 먼저 마치십시오.':'환경 → 빌드·Lint·단위 테스트 → 선택한 모의 검사 순서로 실행합니다.');}
function valid(r,kind,apiNumber){if(!r||r.kind!==kind||r.fingerprint!==fingerprint()||!fingerprint()||r.configuration!==snapshot?.workflow?.configuration||!r.configuration||r.exitCode!==0)return false;
 if(kind==='workflow-environment')return r.scope==='workflow'&&r.evidence==='environment'&&r.status==='checked';
 if(kind==='workflow-checks')return r.scope==='workflow'&&r.evidence==='local'&&r.status==='passed'&&r.sourceVerified===true&&r.tests>0&&r.failed===0&&r.skipped===0;
 return r.scope==='baseline'&&r.evidence==='simulation'&&r.status==='mock-passed'&&r.tests>=2&&r.failed===0&&r.skipped===0&&r.api===apiNumber&&r.requestedApi===apiNumber;
}
function route(id){api.close('android-workflow-dialog');$(id)?.click();}
function nextAction(records,ack){
 let message,button,action;
 if(busy){message='자동 검증이 실행 중입니다. 실행 상태와 로그를 확인하십시오.';button='자동 검증 보기';action=()=>tab('auto');}
 else if(!snapshot?.modulePath){message='프로젝트 + → Android 앱 만들기에서 새 프로젝트를 만들거나 로컬 폴더를 연결하십시오.';button='프로젝트 만들기·연결';action=()=>route('add-project');}
 else if(!ack.code){message='코드·라이브러리를 확인하고 기능을 검사하는 테스트를 작성한 뒤 직접 확인을 표시하십시오.';button='코드·라이브러리 메뉴';action=()=>route('build-assistant-open');}
 else if(!records.some(r=>valid(r,'workflow-environment'))||!records.some(r=>valid(r,'workflow-checks'))){message='자동 검증에서 환경·빌드·Lint·단위 테스트를 실행하십시오. 실패하면 해결 안내와 로그를 확인합니다.';button='자동 검증 열기';action=()=>tab('auto');}
 else if($('gw-include-mock').checked&&!$('gw-api-list').querySelector('input:checked')){message='모의 검사를 포함하려면 API를 하나 이상 선택하십시오.';button='자동 검증 열기';action=()=>tab('auto');}
 else if($('gw-include-mock').checked&&[...$('gw-api-list').querySelectorAll('input:checked')].some(n=>!records.some(r=>valid(r,'mock',Number(n.value))))){message='선택한 API 모의 검사 결과가 없습니다. 준비 파일을 확인하고 자동 검증을 실행하십시오.';button=snapshot.simulation?.prepared?'자동 검증 열기':'모의 검사 준비 열기';action=()=>snapshot.simulation?.prepared?tab('auto'):route('build-assistant-open');}
 else if(!ack.device){message='APK 정보를 확인해 저장·설치한 뒤 실제 앱의 주요 기능·회전·재실행을 직접 확인하십시오.';button='APK 저장·설치 메뉴';action=()=>route('show-build');}
 else{message='현재 소스의 기본 확인 항목을 마쳤습니다. 모의 검사는 선택 사항이며, 실제 다른 OS 검증은 별도로 필요합니다.';button='사용 매뉴얼';action=()=>tab('manual');}
 $('gw-next-action').disabled=false;$('gw-next').textContent=tr(message);$('gw-next-action').textContent=tr(button);$('gw-next-action').onclick=action;
}
function failureHelp(record,kind){
 let message;
 if(record?.tests===0)message='실행한 테스트가 0개입니다. app/src/test에 테스트 소스와 JUnit 의존성이 있는지 확인하고 다시 실행하십시오.';
 else if(kind==='workflow-checks'&&record?.tests===-1&&record?.exitCode===0)message='테스트 실행 수를 확인하지 못했습니다. NO-SOURCE 여부와 app/src/test의 테스트 소스·JUnit 설정을 확인하십시오. 실제 테스트가 실행되어야 다음 단계로 진행합니다.';
 else if(record?.skipped>0)message='건너뛴 테스트가 있습니다. @Ignore·조건부 제외·SDK/JDK 조건을 확인한 뒤 다시 실행하십시오.';
 else if(record?.failed>0)message='실패한 테스트가 있습니다. 실행 로그의 테스트 이름과 예상값·실제값을 확인하고 코드를 수정하십시오.';
 else if(record?.status==='stale')message='검사 중 소스가 바뀌었습니다. 진행 상태를 새로고침하고 같은 소스로 다시 검사하십시오.';
 else if(kind==='workflow-environment')message='환경 확인을 마치지 못했습니다. Android 빌드 메뉴의 JDK·SDK·aapt2 경로와 Linux 활성화를 확인하십시오.';
 else message='실행 로그에서 컴파일 오류·의존성 다운로드·SDK 경로를 확인하십시오. 명령이 성공해도 테스트 수나 소스·설정 연결이 확인되지 않으면 다음 단계로 진행하지 않습니다.';
 $('gw-failure-text').textContent=tr(message);$('gw-failure').hidden=false;
}

function render(){controls();const records=snapshot?.validation?.records||[],ack=saved();if(ack.signature!==signature()||!fingerprint()){ack.device=false;ack.code=false;ack.signature=signature();save(ack);}
 $('gw-project').textContent=(api.getState().workspace?.name||tr('프로젝트'))+' · '+(snapshot?.modulePath||tr('Android 프로젝트를 선택하십시오.'));
 const stages=[['프로젝트 선택',!!snapshot?.modulePath,'프로젝트를 만들거나 폴더를 선택합니다.','show-build'],['코드·라이브러리 확인',!!ack.code,'미리보기로 변경 내용을 확인하고 필요한 기능과 테스트를 작성합니다.','build-assistant-open','code'],['환경 확인',records.some(r=>valid(r,'workflow-environment')),'Linux·JDK·SDK·aapt2를 확인합니다.'],['빌드·Lint·단위 테스트',records.some(r=>valid(r,'workflow-checks')),'실제 실행한 테스트가 1개 이상이고 실패·건너뜀이 없어야 통과합니다.'],['Android API 모의 검사',records.some(r=>valid(r,'mock',r.requestedApi)),'선택한 API의 기본 동작 검사입니다. 실제 다른 Android OS의 실행 결과가 아닙니다.'],['APK 저장·설치·실사용',!!ack.device,'APK 정보를 확인해 저장·설치하고 회전·재실행·저장·네트워크를 직접 확인합니다.','show-build','device']];
 const list=$('gw-steps');list.replaceChildren();let count=0;
 for(const [title,passed,description,action,manual]of stages){if(passed)count++;const li=node('li');li.className='workflow-step';li.dataset.status=passed?'passed':'pending';li.append(node('h3',title),node('p',passed?(manual?'사용자 직접 확인 · 자동 검증 아님':title==='프로젝트 선택'?'프로젝트 감지됨':'확인된 실행 결과 있음'):'확인 필요'),node('p',description));if(manual){const label=node('label');label.className='workflow-check';const input=node('input');input.type='checkbox';input.id='gw-ack-'+manual;input.checked=!!ack[manual];input.disabled=busy||!fingerprint();input.addEventListener('change',()=>{const v=saved();save({...v,signature:signature(),[manual]:input.checked});render();});label.append(input,node('span','사용자 직접 확인 · 자동 검증 아님'));li.append(label);}if(action){const b=node('button','관련 메뉴 열기');b.className='secondary-button';b.addEventListener('click',()=>route(action));li.append(b);}list.append(li);}
 nextAction(records,ack);
 $('gw-progress').textContent=tr('단계 확인')+': '+count+' / '+stages.length;
 $('gw-mock-help').textContent=tr(snapshot?.simulation?.prepared?'모의 검사 파일이 준비되어 있습니다. 선택한 API만 실행합니다.':'먼저 개발 도우미에서 모의 검사 변경 미리보기와 적용을 진행하십시오. 자동 검증은 소스를 변경하지 않습니다.');
}
async function refresh(){const c=context(),e=epoch;const [s]=await Promise.all([api.call('android.inspect',{workspaceKey:api.getState().workspace?.key}),api.call('build.inspect',{workspaceKey:api.getState().workspace?.key})]);if(c!==context()||e!==epoch)return;if(s.workspaceKey&&s.workspaceKey!==api.getState().workspace?.key)throw Error(tr('프로젝트가 변경되었습니다.'));const previous=signature(),had=!!snapshot;snapshot=s;if(had&&previous!==signature()&&!busy){$('gw-failure').hidden=true;status('소스 또는 도구 설정이 바뀌었습니다. 현재 소스로 다시 확인하십시오.');}render();}
function invalidate(){if(!api)return;if(scope!==context()){epoch++;stopped=true;scope=context();snapshot=null;owned='';headline='';$('gw-include-mock').checked=false;for(const input of $('gw-api-list').querySelectorAll('input'))input.checked=['29','35'].includes(input.value);$('gw-next').textContent='';$('gw-next-action').textContent='';$('gw-next-action').onclick=null;$('gw-next-action').disabled=true;$('gw-failure').hidden=true;$('gw-warning').textContent='';$('gw-steps').replaceChildren();if(busy)status('프로젝트 또는 계정이 변경되어 다음 단계 실행을 중단했습니다.');}controls();}
async function open(){invalidate();for(const id of ['tool-menu-dialog','build-dialog','android-assistant-dialog'])api.close(id);api.show('android-workflow-dialog');const preserve=busy||!!headline;if(!preserve)status('진행 상태 확인 중…');try{await refresh();if(!busy&&!preserve)status('실행할 단계를 선택하십시오.');if(saved().inFlight&&!busy){$('gw-warning').textContent=tr('이전 자동 검증이 중단되었습니다. 결과를 확인한 뒤 직접 다시 실행하십시오.');save({...saved(),inFlight:false});}}catch(e){status(e.message||e);}}

async function run(){if(busy||!ready())return;const include=$('gw-include-mock').checked,apis=[...$('gw-api-list').querySelectorAll('input:checked')].map(n=>Number(n.value));if(include&&(!snapshot.simulation?.prepared||!apis.length)){status('모의 검사 파일과 API 선택을 먼저 확인하십시오.');return;}
 const c=context(),sig=signature();const queue=[['workflow-environment',null,'환경 확인'],['workflow-checks',null,'빌드·Lint·단위 테스트'],...(include?apis.map(n=>['mock',n,'API '+n+' '+tr('모의 검사')]):[])];busy=true;stopped=false;$('gw-failure').hidden=true;$('gw-warning').textContent='';save({...saved(),inFlight:true});$('gw-queue').replaceChildren();controls();
 let currentRow=null,lastRecord=null,activeKind='';
 nextAction(snapshot.validation?.records||[],saved());
 const guard=()=>{if(stopped||c!==context()||sig!==signature())throw Error(tr('자동 검증이 중단되었습니다. 프로젝트·소스·설정을 확인하십시오.'));};
 try{for(const [kind,apiNumber,label]of queue){guard();activeKind=kind;lastRecord=null;const row=node('li',tr(label)+' · '+tr('실행 중…'));currentRow=row;$('gw-queue').append(row);status(tr(label)+' · '+tr('실행 중…'));
 const terminal=await api.call('android.verify',{workspaceKey:api.getState().workspace.key,kind,...(apiNumber?{options:{api:apiNumber}}:{})});owned=terminal.id||'';
 if(stopped&&owned&&c===context()){await api.call('terminal.stop',{id:owned});owned='';}guard();if(!owned||!terminal.validationId)throw Error(tr('실행 식별자를 확인하지 못했습니다.'));
 let terminalExit=null;
 for(;;){await pause();guard();const t=await api.call('terminal.state',{id:owned});guard();if(t.id!==owned)throw Error(tr('다른 터미널이 시작되어 다음 단계 실행을 중단했습니다.'));if(!t.running){terminalExit=t.exitCode;break;}}
 let matched=false;for(let i=0;i<40;i++){guard();await refresh();guard();const r=snapshot.validation?.records?.find(r=>r.id===terminal.validationId);if(r&&r.status!=='running'){lastRecord=r;matched=terminalExit===0&&valid(r,kind,apiNumber);break;}await pause();}if(!matched)throw Error(tr('통과 근거를 확인하지 못했습니다. 테스트 0개·건너뜀·소스 변경·검증 기록을 확인하십시오.'));row.textContent=tr(label)+' · '+tr('통과');currentRow=null;owned='';}
 status('자동 검증 완료 · APK 설치와 실사용은 직접 확인하십시오.');
 }catch(e){if(c===context()&&!stopped)failureHelp(lastRecord,activeKind);if(currentRow)currentRow.textContent=tr('검증 중단 · 실행 로그를 확인하십시오.');status(e.message||e);}finally{busy=false;owned='';if(c===context())save({...saved(),inFlight:false});render();}
}
function manual(){const sections=[
 ['Android 개발 시작','홈 또는 도구의 Android 개발 시작에서 내부 프로젝트 생성, 기존 폴더 복사, 이 앱의 소스 백업 ZIP 복원, 선택적인 GitHub 복제를 시작합니다. 원래 작업 폴더 연결도 유지됩니다. 새 프로젝트는 앱 내부 android-projects 공간에 저장되고 Linux에서는 같은 파일이 /workspace로 연결됩니다. GitHub는 필수가 아니며 코드 작성과 빌드에는 연결이 필요하지 않습니다.'],
 ['Git 변경 저장과 업로드','Android 개발 시작 → 변경 저장 · GitHub 업로드에서 변경 파일을 확인하고 필요한 파일만 선택해 커밋합니다. 업로드는 별도 확인 후 현재 브랜치의 커밋을 전송합니다. 이 앱의 Git 사용자 이름·이메일과 인증이 필요하며 ChatGPT GitHub 연결은 자동 공유되지 않습니다. 소스 백업 ZIP에는 .git 이력이 포함되지 않습니다. 일반 ZIP은 폴더에 풀고 가져오십시오.'],
 ['전체 개발 흐름','프로젝트 선택 → 코드·라이브러리 작성 → 환경 확인 → 빌드·Lint·단위 테스트 → 선택 API 모의 검사 → APK 정보 확인·저장 → 설치·실사용 확인 순서입니다. 자동 검증은 환경부터 선택한 모의 검사까지 진행합니다.'],
 ['개발 환경 준비','도구 → Android 개발 환경 설정에서 Linux 준비 → 도구 다운로드·설치 → 개발 도구 실행 확인을 진행합니다. 계정·프로젝트 선택 전에도 사용할 수 있습니다. 다운로드 출처·용량·SDK 약관 전문을 확인하고 직접 동의하면 고정 버전 도구를 SHA-256 검증 후 설치합니다. 기존 /opt/android는 덮어쓰지 않습니다. 설치 후 실행 확인과 프로젝트 빌드가 필요합니다.'],
 ['프로젝트와 라이브러리','사이드바 프로젝트 + → Android 앱 만들기에서 Java·Kotlin·Compose 프로젝트를 만듭니다. 기존 폴더는 프로젝트 + → 로컬 폴더 연결을 사용합니다. 개발 도우미에서 SDK·라이브러리 변경 미리보기를 확인한 뒤 적용합니다. 카탈로그의 고정 버전은 모든 조합의 호환성을 보장하지 않습니다. 변경 후 반드시 빌드와 테스트를 실행하십시오.'],
 ['코드와 테스트 작성','원하는 동작과 실패 상황을 구체적으로 요청하고 기능을 구현합니다. 저장·재열기·오류 처리 등 실제 동작을 검사하는 단위 테스트를 작성합니다. 자동 검증은 코드를 대신 작성하거나 라이브러리를 자동으로 추가하지 않습니다.'],
 ['자동 검증과 결과 읽기','자동 검증 탭에서 실행을 누르면 환경 → 빌드·Lint·단위 테스트 순서로 진행합니다. 모의 검사는 선택했을 때만 실행합니다. 실패하면 다음 단계가 멈춥니다. 실행 로그 보기에서 원인을 확인합니다. 테스트 0개, NO-SOURCE, 건너뜀은 통과로 취급하지 않습니다. 테스트 소스와 실행 조건을 확인하십시오. 일반 단위 테스트는 app/src/test와 JUnit 의존성이 필요합니다. API 모의 검사도 사용할 경우 개발 도우미의 모의 검사 준비 미리보기 → 미리본 모의 검사 준비 적용으로 JUnit·Robolectric과 기준 테스트를 준비할 수 있습니다.'],
 ['Android 버전별 모의 검사','개발 도우미에서 기본 모의 검사 추가 내용을 미리 보고 적용한 뒤 API를 선택합니다. API 29~35의 기본 Robolectric 검사는 실제 OS·권한 화면·제조사 동작·성능을 보장하지 않습니다. 실제 다른 Android 버전은 별도 기기 또는 실행 환경에서 검증해야 합니다.'],
 ['APK 저장과 설치','Android 빌드에서 APK 정보의 패키지명·버전·수정 시각을 확인하고 APK 저장을 누릅니다. APK가 존재하거나 UP-TO-DATE라고 표시되는 것만으로 최신 소스와 설치 성공을 증명하지 않습니다. 파일을 열어 Android 설치 확인을 직접 진행합니다. 서명이 다르면 기존 앱 업데이트가 거부될 수 있습니다.'],
 ['실사용 직접 확인','설치한 앱에서 주요 기능, 화면 회전, 종료 후 재실행, 저장 데이터 유지, 네트워크 실패를 확인합니다. 필요한 경우 강제 종료·재부팅 후에도 확인합니다. 단계 화면의 직접 확인 체크는 사용자 진술이며 자동 검사나 다른 OS의 통과 결과가 아닙니다. 소스나 도구 설정이 바뀌면 다시 확인합니다.'],
 ['중지와 문제 해결','다른 프로젝트·계정으로 이동하면 다음 자동 단계가 중단됩니다. 중지는 이 화면에서 시작한 터미널만 대상으로 합니다. 화면을 닫아도 진행할 수 있지만 앱을 다시 불러온 뒤 자동 재개하지 않습니다. Linux 비활성화·경로 오류·의존성 다운로드 실패는 환경과 실행 로그를 확인하십시오.']];
 for(const [title,text]of sections){const details=node('details'),summary=node('summary',title);if(title==='자동 검증과 결과 읽기')details.dataset.guide='checks';details.append(summary,node('p',text));$('gw-manual').append(details);}}
window.AndroidWorkflowUI={init(injected){api=injected;for(const id of ['show-workflow','build-workflow-open','aa-workflow-open'])$(id)?.addEventListener('click',open);for(const name of ['stages','auto','manual'])$('gw-tab-'+name).addEventListener('click',()=>tab(name));$('gw-failure-manual').addEventListener('click',()=>{tab('manual');const d=document.querySelector('#gw-manual [data-guide=checks]');d.open=true;d.scrollIntoView?.({block:'nearest'});});$('gw-include-mock').addEventListener('change',()=>render());for(const n of [29,30,31,32,33,34,35]){const l=node('label'),input=node('input');input.type='checkbox';input.value=n;input.checked=n===29||n===35;input.addEventListener('change',()=>render());l.append(input,node('span','API '+n));$('gw-api-list').append(l);}$('gw-refresh').addEventListener('click',()=>refresh().catch(e=>status(e.message||e)));$('gw-run').addEventListener('click',run);$('gw-stop').addEventListener('click',async()=>{stopped=true;const id=owned;if(id)try{await api.call('terminal.stop',{id});}catch(e){status(e.message||e);}});$('gw-open-log').addEventListener('click',()=>api.show('terminal-dialog'));$('gw-prepare-mock').addEventListener('click',()=>route('build-assistant-open'));manual();scope=context();controls();},invalidate,running:()=>busy,evidenceChanged(){if(!busy&&$('android-workflow-dialog').hasAttribute('open'))refresh().catch(e=>status(e.message||e));}};
})();
