package com.example.workflow.notes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.work.WorkInfo

@Composable
fun RemoteScreen(model:RemoteSyncModel,probe:ResilienceModel,onBack:()->Unit) {
 DisposableEffect(probe){onDispose{probe.cancel()}}
 val probing by probe.busy.collectAsStateWithLifecycle()
 val results by probe.results.collectAsStateWithLifecycle()
 val history by model.history.collectAsStateWithLifecycle()
 val items by model.items.collectAsStateWithLifecycle()
 val base by model.baseUrl.collectAsStateWithLifecycle()
 val jobs by model.work.collectAsStateWithLifecycle()
 val enqueuing by model.enqueuing.collectAsStateWithLifecycle()
 val error by model.error.collectAsStateWithLifecycle()
 val active=jobs.any{!it.state.isFinished}
 var scenario by rememberSaveable{mutableStateOf("NORMAL")}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  Text("샘플 API · 이미지 · 백그라운드 동기화",style=MaterialTheme.typography.titleLarge)
  Text("이 기기의 로컬 테스트 서버입니다. 사용자 메모와 분리된 캐시에 저장합니다.")
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
   listOf("NORMAL" to "정상","FAIL_ONCE" to "503 한 번","INVALID_JSON" to "손상 JSON","OFFLINE" to "연결 끊김").forEach{(key,label)->
    FilterChip(selected=scenario==key,onClick={scenario=key},enabled=!active&&!enqueuing,label={Text(label)})
   }
  }
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   Button(enabled=!active&&!enqueuing,onClick={model.sync(ApiScenario.valueOf(scenario))}){Text("동기화")}
   OutlinedButton(enabled=!active&&!enqueuing,onClick={model.sync(ApiScenario.NORMAL,120)}){Text("2분 후 동기화")}
   OutlinedButton(enabled=active,onClick=model::cancel){Text("작업 취소")}
   TextButton(onClick=onBack){Text("메모 목록")}
  }
  error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
  if(enqueuing)Text("작업 등록 중")
  if(active)LinearProgressIndicator(Modifier.fillMaxWidth())
  jobs.sortedByDescending{j->j.tags.firstOrNull{it.startsWith("created:")}?.substringAfter(":")?.toLongOrNull()?:0}.take(5).forEach{job->
   val label=when(job.state){WorkInfo.State.SUCCEEDED->"성공";WorkInfo.State.FAILED->"실패";WorkInfo.State.CANCELLED->"취소됨";WorkInfo.State.RUNNING->"실행 중";WorkInfo.State.ENQUEUED->"대기·재시도";WorkInfo.State.BLOCKED->"선행 작업 대기"}
   Text("작업 ${job.id.toString().take(8)} · $label · 시도 ${job.runAttemptCount}")
   job.outputData.getString("message")?.let{Text(it)}
  }
  Text("동기화 이력 ${history.size}건 · 최근 30건 보관")
  history.take(5).forEach{record->Text("이력 ${record.id.take(8)} · ${record.state} · 시도 ${record.attempt}")}
  Text("저장된 샘플 ${items.size}개")
  items.forEach{item->
   var imageState by remember(item.id,base){mutableStateOf("이미지 로딩 중")}
   Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(12.dp)) {
     Text(item.title)
     if(base!=null)AsyncImage(model=base!!.trimEnd('/')+item.imagePath,contentDescription="샘플 API 이미지",modifier=Modifier.size(128.dp,64.dp),onSuccess={imageState="이미지 표시 성공"},onError={imageState="이미지 표시 실패"})
     Text(imageState)
    }
   }
  }
  HorizontalDivider()
  OutlinedButton(enabled=!probing,onClick=probe::run){Text(if(probing)"진단 중" else "HTTPS·DB 용량 검사")}
  if(probing)OutlinedButton(onClick=probe::cancel){Text("진단 취소")}
  Text("공개 HTTPS 서비스(httpbin.org)에 메모·계정·토큰을 보내지 않습니다. 인증 상태 코드 검사이며 실제 로그인 검사는 아닙니다.")
  results.forEach{Text(it)}
  Text("임시 DB 한도 검사입니다. 기기 저장 공간을 채우지 않습니다. 재부팅·Doze는 별도 기기 검사입니다.")
  Text("실패·연결 끊김에도 마지막 저장 캐시는 유지합니다. 실제 외부 HTTPS·계정 인증은 별도 검증 대상입니다.")
 }
}
