package com.example.workflow.notes
import androidx.room.withTransaction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Singleton
class RemoteStore @Inject constructor(private val database:NotesDatabase,val backend:RemoteBackend) {
 val history=database.history().observe()
 val items=database.remote().observe()
 val baseUrl=backend.baseUrl
 suspend fun recordWork(work:List<androidx.work.WorkInfo>) {
  val now=System.currentTimeMillis()
  val records=work.map {SyncRecord(it.id.toString(),it.state.name,it.runAttemptCount,now,it.outputData.getString("message")?:"")}
  database.withTransaction {database.history().upsert(records);database.history().prune()}
 }
 suspend fun start()=backend.start()
 suspend fun refresh():Int {
  val items=validatedRemoteItems(backend.fetch()).map{RemoteItem(it.id,it.title,it.imagePath)}
  currentCoroutineContext().ensureActive()
  database.withTransaction {database.remote().clear();database.remote().insertAll(items)}
  return items.size
 }
}
