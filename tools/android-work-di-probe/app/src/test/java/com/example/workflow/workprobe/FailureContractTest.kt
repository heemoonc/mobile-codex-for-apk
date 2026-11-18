package com.example.workflow.workprobe
import org.junit.Assert.*
import org.junit.Test
class FailureContractTest {
 @Test fun rejectsConstructionOrUncaughtFailureWithoutInjectedOutput() {
  assertTrue(failureMatches("FAILED","token-1","token-1",1))
  assertFalse(failureMatches("FAILED",null,"token-1",0))
  assertFalse(failureMatches("FAILED",null,"token-1",1))
  assertFalse(failureMatches("FAILED","other","token-1",1))
  assertFalse(failureMatches("FAILED","token-1","token-1",2))
  assertFalse(failureMatches("SUCCEEDED","token-1","token-1",1))
  assertFalse(failureMatches("FAILED","","",1))
 }
}
