/* Android project diagnostics: native bridge owns parsing, writes and command execution. */
(() => {
  'use strict';
  let api, scope = null, observedScope = null, generation = 0, busy = false, value = null, preview = null, request = null, previewEpoch = 0, simulationPreview = null;
  const $ = id => document.getElementById(id);
  const tr = text => api.t(text);
  const key = () => api.getState().workspace?.key ?? '';
  const context = () => { const s=api.getState(); return JSON.stringify([key(),s.androidAccountScope || s.account?.id || s.account?.email || s.accountId || '',s.threadId || s.thread?.id || '']); };
  const node = (tag, text) => { const n = document.createElement(tag); if (text !== undefined) n.textContent = String(text); return n; };
  function active(k, g) { return scope === k && key() === k && observedScope === context() && generation === g; }
  function writable() { const s = api.getState(); return s.permissions !== 'read-only' && s.directWorkspace !== false; }
  function blocked() { const s=api.getState(); return !!(s.busy || s.terminal?.running); }
  function executable() { const s = api.getState(); return writable() && !!(value?.linux?.enabled ?? s.linux?.enabled); }
  function reportError(error) {
    const message=String(error.message || error);
    $('aa-status').textContent=message;api.toast(message);
    $('aa-status').scrollIntoView?.({block:'nearest'});
  }
  function controls() {
    const valid = scope !== null && scope === key();
    window.UiCore?.busyIndicator($('aa-status'),busy);
    for (const id of ['aa-refresh','aa-library-preview','aa-sdk-preview','aa-save-matrix','aa-plan','aa-import','aa-simulation-preview']) $(id).disabled = !valid || busy || blocked();
    for (const id of ['aa-local','aa-connected','aa-devices','aa-adb','aa-cloud','aa-mock','aa-emulator-probe']) $(id).disabled = !valid || busy || blocked() || !executable();
    for (const id of ['aa-save-matrix','aa-import']) $(id).disabled = !valid || busy || blocked() || !writable();
    $('aa-execution-help').textContent = !valid ? tr('프로젝트를 다시 검사하십시오.') : busy ? tr('작업 중…') : blocked() ? tr('진행 중인 작업이나 터미널을 먼저 마치십시오.') : !writable() ? tr('쓰기 가능한 프로젝트와 권한이 필요합니다.') : !executable() ? tr('설정 → 도구에서 Linux를 설치하고 활성화하십시오.') : value?.validation?.fingerprintWarning ? tr('소스 지문을 만들 수 없어 실행 결과만 기록합니다. 소스 일치 여부는 미확인입니다.') : '';
    $('aa-simulation-apply').disabled = !valid || busy || blocked() || !writable() || !simulationPreview?.canApply || !simulationPreview?.fingerprint;
    $('aa-apply').disabled = !valid || busy || blocked() || !writable() || !preview?.canApply || !preview?.fingerprint;
    $('aa-library-preview').disabled = !valid || busy || blocked() || !value?.catalog?.length;
    for (const input of $('android-assistant-dialog').querySelectorAll('input,select,textarea')) input.disabled = busy || !valid;
  }
  function discardPreview() { previewEpoch++; preview = request = simulationPreview = null; $('aa-preview').replaceChildren(); $('aa-simulation-preview-output').replaceChildren(); controls(); }
  function invalidate() {
    if (!api) return;
    if (observedScope !== context()) {
      observedScope = context(); scope = key(); generation++; busy = false; value = preview = request = simulationPreview = null;
      for (const id of ['aa-diagnostics','aa-issues','aa-preview','aa-matrix','aa-plan-output','aa-status','aa-simulation-preview-output','aa-simulation-limitations','aa-mock-matrix']) $(id).replaceChildren();
      $('aa-library').replaceChildren();
      for (const input of $('android-assistant-dialog').querySelectorAll('input,textarea')) input.value = '';
      $('aa-records').textContent=''; $('aa-plan-output').value='';
    }
    controls();
  }
  async function run(action, payload, onSuccess) {
    if (busy || blocked() || scope === null || observedScope !== context()) return;
    const k = scope, g = generation; busy = true; controls(); $('aa-status').textContent = tr('작업 중…');
    try {
      const result = await api.call(action, {workspaceKey:k, ...payload});
      if (!active(k,g)) return;
      if (result && Object.prototype.hasOwnProperty.call(result,'workspaceKey') && result.workspaceKey !== k) throw new Error(tr('프로젝트가 변경되었습니다. 다시 검사하십시오.'));
      onSuccess(result); $('aa-status').textContent = tr('완료');
    } catch (error) { if (active(k,g)) reportError(error); }
    finally { if (active(k,g)) { busy = false; controls(); } }
  }
  const display = v => v === undefined || v === null || v === '' ? tr('알 수 없음') : typeof v === 'object' ? JSON.stringify(v) : String(v);
  function render(snapshot) {
    value = snapshot; discardPreview();
    const table = node('table'); const body = node('tbody'); table.append(body);
    for (const [label,v] of [[tr('최소 Android API'),snapshot.sdk?.min],[tr('대상 Android API'),snapshot.sdk?.target],[tr('컴파일 Android API'),snapshot.sdk?.compile],['AGP',snapshot.tools?.agp],['Kotlin',snapshot.tools?.kotlin],['Gradle',snapshot.tools?.gradle],[tr('현재 기기'),snapshot.currentDevice],[tr('모듈'),snapshot.modulePath]]) {
      const row=node('tr');row.append(node('th',label),node('td',display(v)));body.append(row);
    }
    const dependencies=node('ul');
    for(const dep of snapshot.dependencies || []) dependencies.append(node('li',`${display(dep.configuration)} · ${display(dep.coordinate)}${dep.dynamic ? ' · '+tr('동적 버전 · 재현성 확인 필요') : ''}`));
    $('aa-diagnostics').replaceChildren(table,node('h3',tr('선언된 라이브러리')),dependencies);
    if(snapshot.validation?.storageWarning || snapshot.validation?.fingerprintWarning) dependencies.append(node('li',tr('검증 기록 저장 또는 소스 지문을 확인하지 못했습니다.')));
    const issues = node('ul');
    for (const item of [...(snapshot.issues || []),...(snapshot.limitations || [])]) issues.append(node('li',typeof item === 'string' ? tr(item) : display(item.message || item)));
    $('aa-issues').replaceChildren(issues);
    for (const part of ['min','target','compile']) $('aa-sdk-'+part).value = snapshot.sdk?.[part] ?? '';
    $('aa-library').replaceChildren();
    for (const lib of snapshot.catalog || []) { const option=node('option',`${lib.name} ${lib.version || ''} · ${tr(lib.category || '라이브러리')}`);option.value=lib.id;$('aa-library').append(option); }
    $('aa-library-preview').disabled = !(snapshot.catalog || []).length;
    const validation=snapshot.validation || {};
    $('aa-apis').value = (validation.requestedApis || []).join(', ');
    const rows=node('ul');
    for (const row of validation.matrix || []) rows.append(node('li',`API ${display(row.api)} · ${tr('검증 상태')}: ${row.imported ? tr('외부 제공 · 직접 검증하지 않음') : tr(row.status === 'artifact-passed' ? 'APK 통과 · 소스 연결 미확인' : (row.status || '미검증'))}${row.imported ? ' · '+tr('외부에서 가져온 결과') : ''}${row.stale ? ' · '+tr('소스 변경 후 재검증 필요') : ''}`));
    if (!rows.childNodes.length) rows.append(node('li',tr('실행 결과 없음 · 미검증')));
    $('aa-matrix').replaceChildren(rows);
    const mockRows=node('ul');
    for(const row of validation.mockMatrix || []) mockRows.append(node('li',`API ${display(row.api)} · ${tr(row.status === 'mock-passed' ? '모의 검사 통과' : row.status === 'mock-failed' ? '모의 검사 실패' : '미검증')}${row.stale ? ' · '+tr('소스 변경 후 재검증 필요') : ''}`));
    if(!mockRows.childNodes.length) mockRows.append(node('li',tr('실행 결과 없음 · 미검증')));
    $('aa-mock-matrix').replaceChildren(mockRows);
    const limitations=node('ul');for(const item of snapshot.simulation?.limitations || []) limitations.append(node('li',tr(typeof item === 'string' ? item : display(item.message || item))));
    $('aa-simulation-limitations').replaceChildren(limitations);
    $('aa-records').textContent = JSON.stringify(validation.records || [],null,2);
  }
  function apis() {
    const raw=$('aa-apis').value.trim();
    if (!/^\d+(?:\s*[,\s]\s*\d+)*$/.test(raw)) throw new Error(tr('API 번호를 쉼표로 구분하여 입력하십시오.'));
    const values=[...new Set(raw.split(/[,\s]+/).map(Number))];
    if (values.some(n=>!Number.isInteger(n)||n<16||n>100)||values.length>20) throw new Error(tr('API 번호를 확인하십시오.'));
    return values;
  }
  function attempt(fn) { try { fn(); } catch(e) { reportError(e); } }
  function previewRequest(next) {
    discardPreview(); const epoch=previewEpoch;
    run('android.preview',{request:next},plan=>{
      if(epoch!==previewEpoch)return; preview=plan;request=next;
      const output=node('div');output.append(node('p',display(plan.summary)));
      for (const change of plan.changes || []) { output.append(node('h4',change.path),node('p',tr('변경 전')),node('pre',change.before),node('p',tr('변경 후')),node('pre',change.after)); }
      for (const issue of plan.issues || []) output.append(node('p',display(issue.message || issue)));
      $('aa-preview').replaceChildren(output);
    });
  }
  function options() {
    const fields={serial:'aa-serial',model:'aa-model',api:'aa-device-api',appApk:'aa-app-apk',testApk:'aa-test-apk',runner:'aa-runner',project:'aa-cloud-project'};
    const result={};for(const [name,id] of Object.entries(fields)) if($(id).value.trim()) result[name]=$(id).value.trim();if(result.api!==undefined){result.api=Number(result.api);if(!Number.isInteger(result.api)||result.api<16||result.api>100)throw new Error(tr('API 번호를 확인하십시오.'));}return result;
  }
  function init(injected) {
    if (api) return; api=injected;
    const on=(id,handler)=>$(id).addEventListener('click',handler);
    on('build-assistant-open',()=>{invalidate();api.show('android-assistant-dialog');run('android.inspect',{},render);});
    on('aa-refresh',()=>{discardPreview();run('android.inspect',{},render);});
    on('aa-library-preview',()=>{if($('aa-library').value)previewRequest({libraryId:$('aa-library').value});});
    on('aa-sdk-preview',()=>attempt(()=>{const sdk={};for(const p of ['min','target','compile']){const n=Number($('aa-sdk-'+p).value);if(!Number.isInteger(n)||n<16||n>100)throw new Error(tr('API 번호를 확인하십시오.'));sdk[p]=n;}previewRequest({sdk});}));
    on('aa-apply',()=>{if(!preview?.canApply||!preview.fingerprint||!writable()||busy)return;run('android.apply',{request,fingerprint:preview.fingerprint},render);});
    for (const id of ['aa-library','aa-sdk-min','aa-sdk-target','aa-sdk-compile']) $(id).addEventListener('input',discardPreview);
    for (const kind of ['local','connected','devices','adb','cloud']) on('aa-'+kind,()=>attempt(()=>{if(!executable())return;const opts=options();discardPreview();run('android.verify',{kind,options:opts},terminal=>api.openTerminal(terminal));}));
    on('aa-simulation-preview',()=>{discardPreview();const epoch=previewEpoch;run('android.simulation.preview',{},plan=>{
      if(epoch!==previewEpoch)return;simulationPreview=plan;
      const output=node('div');if(plan.summary)output.append(node('p',display(plan.summary)));
      for(const change of plan.changes || []) output.append(node('h4',change.path),node('p',tr('변경 전')),node('pre',change.before),node('p',tr('변경 후')),node('pre',change.after));
      for(const issue of plan.issues || [])output.append(node('p',display(issue.message || issue)));
      $('aa-simulation-preview-output').replaceChildren(output);
    });});
    on('aa-simulation-apply',()=>{if(!simulationPreview?.canApply||!simulationPreview.fingerprint||!writable()||busy)return;run('android.simulation.apply',{fingerprint:simulationPreview.fingerprint},render);});
    on('aa-mock',()=>attempt(()=>{if(!executable())return;const selected=Number($('aa-mock-api').value);if(!Number.isInteger(selected)||selected<29||selected>35)throw new Error(tr('모의 검사 API는 29부터 35까지 선택하십시오.'));discardPreview();run('android.verify',{kind:'mock',options:{api:selected}},terminal=>api.openTerminal(terminal));}));
    on('aa-emulator-probe',()=>{if(!executable())return;discardPreview();run('android.verify',{kind:'emulator-probe',options:{}},terminal=>api.openTerminal(terminal));});
    $('aa-mock-api').addEventListener('input',discardPreview);
    on('aa-save-matrix',()=>attempt(()=>run('android.matrix.save',{apis:apis()},render)));
    on('aa-plan',()=>attempt(()=>run('android.plan',{apis:apis(),options:options()},plan=>{$('aa-plan-output').value=JSON.stringify(plan,null,2);})));
    on('aa-import',()=>attempt(()=>{const evidence=JSON.parse($('aa-evidence').value);if(!evidence||Array.isArray(evidence)||typeof evidence!=='object')throw new Error(tr('검증 결과 JSON 객체가 필요합니다.'));run('android.evidence.import',{evidence},render);}));
    // Text remains selectable and can be copied using Android's standard text controls.
    on('aa-plan-select',()=>{$('aa-plan-output').focus();$('aa-plan-output').select();});
    $('android-assistant-dialog').addEventListener('close',()=>{generation++;busy=false;discardPreview();});
    invalidate();
  }
  const translations = {
  "Android 버전별 모의 검사": "Mock checks by Android version",
  "Robolectric으로 생성한 기본 모의 테스트를 실행합니다. 전체 Android OS 실행이나 앱 전체 호환성 검사가 아닙니다.": "Runs generated Robolectric baseline mock tests. This is not a full Android OS run or whole-app compatibility verification.",
  "모의 검사 API": "Mock check API",
  "모의 검사 준비 미리보기": "Preview mock check preparation",
  "미리본 모의 검사 준비 적용": "Apply previewed mock check preparation",
  "모의 검사 실행": "Run mock check",
  "실험용 에뮬레이터 준비 확인": "Check experimental emulator readiness",
  "QEMU 명령 존재 여부와 TCG 지원만 확인합니다. Android 부팅이나 OS 테스트가 아닙니다.": "Checks QEMU command presence and TCG support only. This is not Android boot or an OS test.",
  "모의 검사 통과": "Mock check passed",
  "모의 검사 실패": "Mock check failed",
  "모의 검사 API는 29부터 35까지 선택하십시오.": "Select a mock check API from 29 through 35.",

  "작업 중…": "Working…",
  "소스 지문을 만들 수 없어 실행 결과만 기록합니다. 소스 일치 여부는 미확인입니다.": "Only execution results will be recorded because the source fingerprint is unavailable. Source association remains unverified.",
  "프로젝트를 다시 검사하십시오.": "Inspect the project again.",
  "진행 중인 작업이나 터미널을 먼저 마치십시오.": "Finish the active task or terminal first.",
  "쓰기 가능한 프로젝트와 권한이 필요합니다.": "A writable project and permission are required.",
  "설정 → 도구에서 Linux를 설치하고 활성화하십시오.": "Install and enable Linux in Settings → Tools.",
  "완료": "Done",
  "알 수 없음": "Unknown",
  "최소 Android API": "Minimum Android API",
  "대상 Android API": "Target Android API",
  "컴파일 Android API": "Compile Android API",
  "현재 기기": "Current device",
  "모듈": "Module",
  "라이브러리": "Library",
  "검증 상태": "Verification status",
  "외부에서 가져온 결과": "Imported external result",
  "외부 제공 · 직접 검증하지 않음": "Externally supplied · not verified by this app",
  "소스 변경 후 재검증 필요": "Source changed · verification required",
  "실행 결과 없음 · 미검증": "No execution results · unverified",
  "API 번호를 쉼표로 구분하여 입력하십시오.": "Enter comma-separated API numbers.",
  "API 번호를 확인하십시오.": "Check API numbers (16–100, at most 20 targets).",
  "변경 전": "Before",
  "변경 후": "After",
  "검증 결과 JSON 객체가 필요합니다.": "A verification result JSON object is required.",
  "프로젝트가 변경되었습니다. 다시 검사하십시오.": "Project changed. Inspect it again.",
  "미검증": "Unverified",
  "unverified": "Unverified",
  "passed": "Passed",
  "APK 통과 · 소스 연결 미확인": "APK passed · source association unverified",
  "failed": "Failed",
  "storage": "Storage",
  "network": "Network",
  "images": "Images",
  "ui": "UI",
  "background": "Background work",
  "di": "Dependency injection",
  "Android 개발 도우미": "Android development assistant",
  "정적 진단은 실행 검증이 아닙니다. 다른 Android 버전은 실제 실행 결과를 가져오기 전까지 미검증입니다.": "Static diagnostics are not execution verification. Other Android versions remain unverified until actual results are imported.",
  "프로젝트 다시 검사": "Inspect project again",
  "프로젝트 진단": "Project diagnostics",
  "호환성 변경 미리보기": "Preview compatibility changes",
  "SDK 변경 미리보기": "Preview SDK changes",
  "라이브러리 추가 미리보기": "Preview adding library",
  "미리본 변경 적용": "Apply previewed changes",
  "검증 실행": "Run verification",
  "프로젝트의 빌드 스크립트를 실행합니다. 현재 기기의 결과는 다른 Android 버전의 통과를 의미하지 않습니다.": "Runs project build scripts. Results on the current device do not establish compatibility with other Android versions.",
  "빌드·Lint·단위 테스트 실행": "Run build, Lint and unit tests",
  "연결 기기 목록": "List connected devices",
  "연결 기기 테스트 실행": "Run connected-device tests",
  "Android 버전별 검증": "Verification by Android version",
  "검증할 API 번호": "Target API numbers",
  "검증 대상 저장": "Save verification targets",
  "검증 기록": "Verification records",
  "외부 기기·클라우드 테스트": "External devices and cloud tests",
  "외부 테스트를 직접 실행하면 선택한 기기에 APK가 설치되거나 클라우드 요금이 발생할 수 있습니다. 인증 정보는 이 화면에 입력하지 마십시오.": "Running external tests can install APKs on the selected device or incur cloud charges. Do not enter credentials here.",
  "ADB 기기 일련번호": "ADB device serial",
  "클라우드 기기 모델": "Cloud device model",
  "외부 Android API": "External Android API",
  "앱 APK 경로": "App APK path",
  "테스트 APK 경로": "Test APK path",
  "테스트 실행기": "Test runner",
  "클라우드 프로젝트": "Cloud project",
  "선택한 ADB 기기에 설치·테스트": "Install and test on selected ADB device",
  "선택한 클라우드 테스트 실행": "Run selected cloud test",
  "외부 테스트 계획 생성": "Generate external test plan",
  "선언된 라이브러리": "Declared libraries",
  "동적 버전 · 재현성 확인 필요": "Dynamic version · check reproducibility",
  "검증 기록 저장 또는 소스 지문을 확인하지 못했습니다.": "Verification history storage or source fingerprint could not be confirmed.",
  "외부 테스트 계획": "External test plan",
  "계획 텍스트 선택": "Select plan text",
  "외부 검증 결과 JSON": "External verification result JSON",
  "결과 가져오기": "Import results",
  "가져온 결과는 외부에서 제공한 증거입니다. 이 앱이 직접 실행한 결과와 구분합니다.": "Imported results are externally supplied evidence, separate from results executed by this app."
};
  window.AndroidAssistantUI = {init,invalidate,translations};
})();
