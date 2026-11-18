/* Android entry points and optional, explicit project Git operations. */
(() => {
'use strict';
let api, scope='', epoch=0, busy=false, mode='repository', snapshot=null;
const $=id=>document.getElementById(id), tr=s=>api.t(s);
const context=()=>String(api.getState().workspace?.key||'');
const blocked=()=>busy||api.isBusy()||api.getState().permissions==='read-only'||!!api.getState().terminal?.running;
const workspace=()=>!!context()&&api.getState().workspace?.available!==false&&api.getState().directWorkspace!==false;
function controls(){
 const stop=blocked();
 $('ag-spinner').hidden=!busy;$('ag-status-row').setAttribute('aria-busy',String(busy));
 $('ag-clone').disabled=stop;
 $('ag-clone-form').querySelectorAll('input').forEach(e=>e.disabled=stop);
 $('ag-commit').disabled=stop||!snapshot||!workspace();$('ag-push').disabled=stop||!snapshot?.head||!snapshot?.origin||!snapshot?.branch||!workspace();$('ag-refresh').disabled=busy||api.isBusy()||!workspace();
 $('ag-message').disabled=stop;$('ag-review').disabled=busy||api.isBusy()||!workspace();
 $('ag-files').querySelectorAll('input').forEach(e=>e.disabled=stop||e.dataset.selectable!=='true');
 for(const id of ['ad-create','ad-folder','ad-zip','ad-github'])$(id).disabled=stop;
 for(const id of ['ad-workflow','ad-build','ad-git'])$(id).disabled=stop||!workspace();
 $('ad-current').textContent=context()?tr('현재 프로젝트')+': '+(api.getState().workspace?.name||context()):tr('먼저 새 프로젝트를 만들거나 기존 소스를 가져오십시오.');
}
function invalidate(){if(!api)return;if(scope!==context()){scope=context();epoch++;snapshot=null;$('ag-files').replaceChildren();$('ag-project').textContent='';$('ag-remote').textContent='';$('ag-message').value='';if($('android-git-dialog').open||$('android-git-dialog').hasAttribute('open'))$('ag-status').textContent=tr('프로젝트가 바뀌었습니다. 변경 목록을 새로고침하십시오.');}controls();}
function display(value){snapshot=value;$('ag-project').textContent=(api.getState().workspace?.name||tr('프로젝트'))+' · '+(value.branch||tr('브랜치 없음'));$('ag-remote').textContent=value.origin||tr('GitHub 원격 저장소가 없습니다. 터미널에서 origin을 설정하십시오.');$('ag-files').replaceChildren();
 for(const file of value.files||[]){const label=document.createElement('label'),box=document.createElement('input'),text=document.createElement('span');box.type='checkbox';box.value=file.path;box.dataset.selectable=String(!!file.selectable);box.checked=false;text.textContent=file.code+' '+file.path;label.append(box,text);$('ag-files').append(label);}
 if(!(value.files||[]).length)$('ag-files').textContent=tr('변경된 파일이 없습니다.');
}
async function operation(action,done){if(blocked())return;busy=true;const version=epoch,key=context();$('ag-status').textContent=tr('실행 중…');controls();try{const result=await action();if(version===epoch&&key===context())done(result);}catch(e){if(version===epoch)$('ag-status').textContent=e.message||String(e);}finally{busy=false;controls();}}
async function refresh(){if(!workspace()){snapshot=null;$('ag-status').textContent=tr('먼저 프로젝트를 선택하십시오.');controls();return;}return operation(()=>api.call('project.git.status',{workspaceKey:context()}),value=>{display(value);$('ag-status').textContent=tr('변경 목록을 확인했습니다.');});}
async function openGit(nextMode='repository'){invalidate();mode=nextMode;api.close('android-start-dialog');$('ag-clone-form').hidden=mode!=='clone';$('ag-repository').hidden=mode==='clone';$('ag-title').textContent=tr(mode==='clone'?'GitHub에서 가져오기':'변경 저장 · GitHub 업로드');$('ag-status').textContent='';api.show('android-git-dialog');controls();if(mode==='repository')await refresh();}
function open(){invalidate();api.close('tool-menu-dialog');api.show('android-start-dialog');}
function init(value){api=value;scope=context();
 for(const id of ['welcome-android-start','show-android-start'])$(id).addEventListener('click',open);
 for(const [id,route]of [['ad-create','create'],['ad-folder','folder'],['ad-zip','zip'],['ad-setup','setup'],['ad-workflow','workflow'],['ad-build','build']])$(id).addEventListener('click',async()=>{if($(id).disabled)return;api.close('android-start-dialog');try{await api[route]();}catch(e){api.toast(e.message||String(e));}});
 $('ad-github').addEventListener('click',()=>{if(!blocked())openGit('clone');});$('ad-git').addEventListener('click',()=>{if(!blocked())openGit();});
 $('ag-refresh').addEventListener('click',refresh);
 $('ag-review').addEventListener('click',()=>{if($('ag-review').disabled)return;api.close('android-git-dialog');api.review();});
 $('ag-clone-form').addEventListener('submit',async e=>{e.preventDefault();if(blocked())return;const args={url:$('ag-url').value.trim(),name:$('ag-name').value.trim(),folder:$('ag-folder').value.trim()};if(!/^https:\/\/github\.com\/[A-Za-z0-9-]+\/[A-Za-z0-9_.-]+\/?$/.test(args.url)||!args.name||!args.folder){$('ag-status').textContent=tr('GitHub HTTPS 저장소 주소와 프로젝트 이름·폴더를 확인하십시오.');return;}
 busy=true;controls();$('ag-status').textContent=tr('GitHub 소스를 가져오는 중입니다. 저장소 크기에 따라 시간이 걸립니다.');try{await api.call('projects.github.clone',args);api.close('android-git-dialog');api.toast(tr('소스를 내부 프로젝트로 가져왔습니다. 빌드 전에 코드를 확인하십시오.'));}catch(error){$('ag-status').textContent=error.message||String(error);}finally{busy=false;controls();}});
 $('ag-commit').addEventListener('click',async()=>{if(blocked()||!snapshot||!workspace())return;const paths=[...$('ag-files').querySelectorAll('input:checked')].filter(n=>!n.disabled).map(n=>n.value),message=$('ag-message').value.trim();if(!paths.length||!message){$('ag-status').textContent=tr('저장할 파일과 커밋 메시지를 선택·입력하십시오.');return;}
 if(!confirm(tr('선택한 파일만 기기에 커밋하시겠습니까?')+'\n'+paths.join('\n')+'\n\n'+message))return;
 const args={workspaceKey:context(),paths,message,head:snapshot.head,branch:snapshot.branch};await operation(()=>api.call('project.git.commit',args),value=>{display(value);$('ag-message').value='';$('ag-status').textContent=tr('선택한 파일을 커밋했습니다. 업로드는 별도로 실행하십시오.');});});
 $('ag-push').addEventListener('click',async()=>{if(blocked()||!snapshot?.origin||!snapshot?.head||!workspace())return;const args={workspaceKey:context(),head:snapshot.head,origin:snapshot.origin,branch:snapshot.branch};if(!confirm(tr('현재 브랜치의 커밋을 GitHub에 업로드하시겠습니까?')+'\n'+snapshot.origin+'\n'+snapshot.branch+'\n'+snapshot.head))return;await operation(()=>api.call('project.git.push',args),result=>{$('ag-status').textContent=tr(result.pushed?'GitHub 업로드를 완료했습니다.':'업로드 완료를 확인하지 못했습니다.');});});
 controls();
}
window.AndroidStartUI={init,open,openGit,invalidate};
})();
