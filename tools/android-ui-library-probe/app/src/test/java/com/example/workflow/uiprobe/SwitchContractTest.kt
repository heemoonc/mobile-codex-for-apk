package com.example.workflow.uiprobe
import org.junit.Assert.*
import org.junit.Test
class SwitchContractTest {
 @Test fun acceptsBothDirectionsAndRejectsNoChange() {
  assertTrue(switchTransitionSucceeded(false,true))
  assertTrue(switchTransitionSucceeded(true,false))
  assertFalse(switchTransitionSucceeded(false,false))
  assertFalse(switchTransitionSucceeded(true,true))
 }
}
