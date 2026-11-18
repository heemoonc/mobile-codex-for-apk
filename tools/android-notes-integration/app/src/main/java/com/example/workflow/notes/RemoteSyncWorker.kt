package com.example.workflow.notes
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class RemoteSyncWorker @AssistedInject constructor(@Assisted context:Context,@Assisted params:WorkerParameters,private val store:RemoteStore):CoroutineWorker(context,params) {
 override suspend fun doWork():Result=withContext(Dispatchers.IO) {
  try {
   val scenario=ApiScenario.valueOf(inputData.getString("scenario")?:"NORMAL")
   store.backend.setScenario(if(scenario==ApiScenario.FAIL_ONCE && runAttemptCount>0)ApiScenario.NORMAL else scenario)
   val count=store.refresh()
   Result.success(workDataOf("count" to count,"attempt" to runAttemptCount,"message" to "샘플 API 캐시 저장 완료"))
  }catch(e:CancellationException){throw e}
  catch(e:Exception) {
   if(shouldRetryRemote(e,runAttemptCount))Result.retry()
   else Result.failure(workDataOf("attempt" to runAttemptCount,"message" to (e.javaClass.simpleName+": "+e.message).take(300)))
  }
 }
}
