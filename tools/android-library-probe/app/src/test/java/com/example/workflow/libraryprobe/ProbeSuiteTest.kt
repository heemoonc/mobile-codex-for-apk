package com.example.workflow.libraryprobe

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProbeSuiteTest {
 @Test fun realAsyncAndLoopbackHttpContractsAllExecute() = runBlocking {
  val results = ProbeSuite.run()
  val expected = setOf("flow-order", "flow-cancel", "coroutine-timeout", "json-roundtrip", "json-invalid", "http-json", "http-error", "http-invalid-json", "http-timeout", "http-cancel", "okhttp-interceptor")
  assertEquals("Every required contract must execute", expected, results.map { it.name }.toSet())
  assertEquals(expected.size, results.size)
  assertTrue(results.filterNot { it.passed }.joinToString("\n"), results.all { it.passed })
 }
}
