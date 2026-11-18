package com.example.workflow.workprobe
import org.junit.Assert.*
import org.junit.Test
class OutputContractTest {
 @Test fun requiresFinishedSuccessAndExactInjectedOutput() {
  assertTrue(outputMatches("SUCCEEDED","scope-123","한글", "scope-123","한글"))
  assertFalse(outputMatches("FAILED","scope-123","한글", "scope-123","한글"))
  assertFalse(outputMatches("RUNNING","scope-123","한글", "scope-123","한글"))
  assertFalse(outputMatches("SUCCEEDED","other","한글", "scope-123","한글"))
  assertFalse(outputMatches("SUCCEEDED",null,"한글", "scope-123","한글"))
  assertFalse(outputMatches("SUCCEEDED","scope-123","changed", "scope-123","한글"))
  assertFalse(outputMatches("SUCCEEDED","","한글", "","한글"))
 }
}
