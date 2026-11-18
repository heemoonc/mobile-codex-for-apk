package com.example.workflow.uiprobe
class ResultLedger(private val expected: Set<String>) {
 private val entries = linkedMapOf<String, Boolean>()
 val results: Map<String, Boolean> get() = entries.toMap()
 init { require(expected.isNotEmpty()) }
 fun record(name: String, passed: Boolean) { require(name in expected); entries[name] = passed }
 fun complete() = expected.all { entries[it] == true }
}
