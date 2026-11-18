package com.example.workflow.uiprobe
import org.junit.Assert.*
import org.junit.Test
class ResultLedgerTest {
 @Test fun requiresEveryExpectedSuccess() {
  val x=ResultLedger(setOf("nav","image")); assertFalse(x.complete())
  x.record("nav",true); assertFalse(x.complete())
  x.record("image",false); assertFalse(x.complete())
  x.record("image",true); assertTrue(x.complete())
 }
 @Test(expected=IllegalArgumentException::class) fun rejectsUnknown() { ResultLedger(setOf("nav")).record("fake",true) }
}
