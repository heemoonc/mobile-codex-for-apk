package com.example.workflow.notes
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.*
class OperationControllerTest {
 @Test fun observationFailureCannotUnlockDelayedSave()=runBlocking {
  val operation=OperationController();val commit=CompletableDeferred<Unit>()
  assertTrue(operation.begin())
  val pending=launch(start=CoroutineStart.UNDISPATCHED){commit.await();operation.finishSaved()}
  operation.observationFailed("설정 읽기 실패")
  try {
   assertEquals("설정 읽기 실패",operation.state.value.error)
   assertTrue(operation.state.value.busy)
   assertFalse(operation.begin())
  } finally {commit.complete(Unit);pending.join()}
  assertTrue(operation.state.value.completed)
 }
 @Test fun editorCannotExitUntilCommitIsConsumed() {
  val operation=OperationController();assertTrue(operation.begin())
  assertFalse(editorCanExit(operation.state.value.busy,operation.state.value.completed))
  operation.finishSaved()
  assertFalse(editorCanExit(operation.state.value.busy,operation.state.value.completed))
  assertFalse(operation.begin())
  operation.consumeSaved()
  assertTrue(editorCanExit(operation.state.value.busy,operation.state.value.completed))
  assertTrue(operation.begin())
 }
}
