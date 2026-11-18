package com.example.workflow.notes
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

@HiltViewModel
class RemoteSyncModel @Inject constructor(@param:ApplicationContext context:Context,private val store:RemoteStore):ViewModel() {
 private val manager=WorkManager.getInstance(context)
 private val queueing=MutableStateFlow(false)
 private val failure=MutableStateFlow<String?>(null)
 val enqueuing=queueing.asStateFlow()
 val error=failure.asStateFlow()
 val history=store.history.retryWhen {error,_->if(error is CancellationException)throw error;failure.value="이력 읽기 실패 · 재시도 중: "+error.message;delay(1000);true}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val items=store.items.catch {failure.value="캐시 읽기 실패: "+it.message}.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 val baseUrl=store.baseUrl
 val work=persistedSnapshots(manager.getWorkInfosForUniqueWorkFlow(NAME),{failure.value="작업 상태·이력 갱신 실패 · 재시도 중: "+it.message}) {
  withContext(Dispatchers.IO){store.recordWork(it)}
  if(failure.value?.startsWith("작업 상태·이력 갱신 실패")==true)failure.value=null
 }.stateIn(viewModelScope,SharingStarted.Eagerly,emptyList())
 init {viewModelScope.launch(Dispatchers.IO){try{store.start()}catch(e:CancellationException){throw e}catch(e:Exception){failure.value="테스트 서버 준비 실패: "+e.message}}}
 fun sync(scenario:ApiScenario,delaySeconds:Long=0) {
  if(queueing.value || work.value.any{!it.state.isFinished})return
  queueing.value=true;failure.value=null
  viewModelScope.launch {
   try {withContext(Dispatchers.IO) {
    val request=OneTimeWorkRequestBuilder<RemoteSyncWorker>().setInputData(workDataOf("scenario" to scenario.name))
     .setInitialDelay(delaySeconds,TimeUnit.SECONDS).setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS).addTag("notes-sample-sync").addTag("created:"+System.currentTimeMillis()).build()
    manager.enqueueUniqueWork(NAME,ExistingWorkPolicy.KEEP,request).result.get(10,TimeUnit.SECONDS)
   }}catch(e:CancellationException){throw e}catch(e:Exception){failure.value="작업 등록 실패: "+e.message}finally{queueing.value=false}
  }
 }
 fun cancel() {viewModelScope.launch(Dispatchers.IO){try{manager.cancelUniqueWork(NAME).result.get(10,TimeUnit.SECONDS)}catch(e:CancellationException){throw e}catch(e:Exception){failure.value="취소 실패: "+e.message}}}
 companion object {const val NAME="notes-sample-sync"}
}
