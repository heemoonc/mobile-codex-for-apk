package com.example.workflow.notes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
class PersistedSnapshotsTest {
 @Test fun temporaryPersistenceFailureResubscribesAndEmitsOnlySavedStates()=runBlocking {
  var attempts=0;var reported=0;val saved=mutableListOf<String>()
  val output=runCatching {persistedSnapshots(flowOf("RUNNING","SUCCEEDED"),{reported++}){state->
   attempts++;if(attempts==1)throw IOException("quota transient")
   saved+=state
  }.toList()}.getOrNull()
  assertEquals(listOf("RUNNING","SUCCEEDED"),output);assertEquals(output,saved);assertEquals(1,reported);assertEquals(3,attempts)
 }
 @Test fun cancellationIsNeverReportedOrRetried()=runBlocking {
  var attempts=0;var reported=0
  val failure=runCatching {persistedSnapshots(flowOf(1),{reported++}){attempts++;throw CancellationException("stop")}.toList()}.exceptionOrNull()
  assertTrue(failure is CancellationException);assertEquals(1,attempts);assertEquals(0,reported)
 }
}
