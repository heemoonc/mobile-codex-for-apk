package com.example.workflow.workprobe
fun outputMatches(state: String, token: String?, payload: String?, expectedToken: String, expectedPayload: String): Boolean =
 state == "SUCCEEDED" && expectedToken.isNotBlank() && token == expectedToken && payload == expectedPayload

fun failureMatches(state: String, token: String?, expectedToken: String, executions: Int): Boolean = state == "FAILED" && expectedToken.isNotBlank() && token == expectedToken && executions == 1
