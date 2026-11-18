package com.example.workflow.workprobe
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.awaitCancellation

@HiltWorker
class ProbeWorker @AssistedInject constructor(
 @Assisted appContext:Context,
 @Assisted params:WorkerParameters,
 private val repository:ProbeRepository
):CoroutineWorker(appContext,params) {
 override suspend fun doWork(): Result {
  repository.entered(id)
  when(inputData.getString("mode")) {
   "hold" -> try { awaitCancellation() } finally {repository.markStopped(id)}
   "retry" -> if(runAttemptCount==0)return Result.retry()
   "fail" -> return Result.failure(workDataOf("token" to repository.token))
  }
  val payload=(inputData.getString("payload")?:"")+(inputData.getString("suffix")?:"")
  return Result.success(workDataOf("token" to repository.token,"payload" to payload,"attempt" to runAttemptCount))
 }
}
