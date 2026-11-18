package com.example.workflow.workprobe
import android.content.Context
import androidx.work.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

data class ProbeResult(val name:String,val passed:Boolean,val detail:String="")
object WorkVerifier {
 suspend fun run(context:Context,repo:ProbeRepository):List<ProbeResult> {
  val manager=WorkManager.getInstance(context)
  val tag="work-probe-"+UUID.randomUUID()
  val ids=mutableListOf<UUID>()
  val results=mutableListOf<ProbeResult>()
  fun request(mode:String="success",payload:String?="한글 입력",suffix:String=""):OneTimeWorkRequest {
   val data=Data.Builder().putString("mode",mode).putString("suffix",suffix)
   if(payload!=null)data.putString("payload",payload)
   return OneTimeWorkRequestBuilder<ProbeWorker>().setInputData(data.build()).addTag(tag)
    .setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS).build().also{ids+=it.id;repo.own(it.id)}
  }
  suspend fun finished(id:UUID)=manager.getWorkInfoByIdFlow(id).filterNotNull().first{it.state.isFinished}
  suspend fun running(id:UUID) {
   manager.getWorkInfoByIdFlow(id).filterNotNull().first{it.state==WorkInfo.State.RUNNING || it.state.isFinished}.also{check(it.state==WorkInfo.State.RUNNING)}
   while(repo.count(id)==0)delay(10)
  }
  fun matches(info:WorkInfo,payload:String)=outputMatches(info.state.name,info.outputData.getString("token"),info.outputData.getString("payload"),repo.token,payload)
  suspend fun verify(name:String,block:suspend()->Unit) {
   try {withTimeout(45000){block()};results+=ProbeResult(name,true)}
   catch(e:TimeoutCancellationException){results+=ProbeResult(name,false,"45s timeout")}
   catch(e:CancellationException){throw e}
   catch(e:Exception){results+=ProbeResult(name,false,e.javaClass.simpleName+": "+e.message)}
  }
  try {
   verify("work-hilt-success") {
    val work=request();manager.enqueue(work).result.get(10,TimeUnit.SECONDS)
    check(matches(finished(work.id),"한글 입력"));check(repo.count(work.id)==1)
   }
   verify("work-chain-output") {
    val first=request(payload="체인",suffix="|1");val second=request(payload=null,suffix="|2")
    manager.beginWith(first).then(second).enqueue().result.get(10,TimeUnit.SECONDS)
    check(matches(finished(second.id),"체인|1|2"));check(repo.count(first.id)==1 && repo.count(second.id)==1)
   }
   verify("work-failure-blocks-child") {
    val parent=request(mode="fail");val child=request()
    manager.beginWith(parent).then(child).enqueue().result.get(10,TimeUnit.SECONDS)
    val failed=finished(parent.id)
    check(failureMatches(failed.state.name,failed.outputData.getString("token"),repo.token,repo.count(parent.id)))
    check(finished(child.id).state==WorkInfo.State.FAILED);check(repo.count(child.id)==0)
   }
   verify("work-real-retry") {
    val work=request(mode="retry");manager.enqueue(work).result.get(10,TimeUnit.SECONDS)
    val result=finished(work.id)
    check(matches(result,"한글 입력"));check(result.outputData.getInt("attempt",-1)==1);check(repo.count(work.id)==2)
   }
   verify("work-running-cancel") {
    val work=request(mode="hold");manager.enqueue(work).result.get(10,TimeUnit.SECONDS);running(work.id)
    manager.cancelWorkById(work.id).result.get(10,TimeUnit.SECONDS)
    check(finished(work.id).state==WorkInfo.State.CANCELLED)
    while(!repo.isStopped(work.id))delay(10)
   }
   verify("work-unique-keep") {
    val first=request(mode="hold");val second=request();val name=tag+"-keep"
    manager.enqueueUniqueWork(name,ExistingWorkPolicy.KEEP,first).result.get(10,TimeUnit.SECONDS);running(first.id)
    manager.enqueueUniqueWork(name,ExistingWorkPolicy.KEEP,second).result.get(10,TimeUnit.SECONDS)
    val actual=manager.getWorkInfosForUniqueWork(name).get(10,TimeUnit.SECONDS)
    check(actual.size==1 && actual.single().id==first.id);check(repo.count(second.id)==0)
    manager.cancelWorkById(first.id).result.get(10,TimeUnit.SECONDS)
    check(finished(first.id).state==WorkInfo.State.CANCELLED)
    while(!repo.isStopped(first.id))delay(10)
   }
  } finally {
   withContext(NonCancellable+Dispatchers.IO) {
    try {
     manager.cancelAllWorkByTag(tag).result.get(10,TimeUnit.SECONDS)
     withTimeout(10000) { manager.getWorkInfosByTagFlow(tag).first { all -> all.all { it.state.isFinished } } }
    } finally {repo.forget(ids)}
   }
  }
  return results
 }
}
