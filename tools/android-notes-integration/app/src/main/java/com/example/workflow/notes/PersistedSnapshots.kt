package com.example.workflow.notes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen

/** Do not publish a snapshot until saved. Re-subscribe after transient persistence/source failure. */
fun <T> persistedSnapshots(upstream:Flow<T>,onFailure:(Throwable)->Unit,persist:suspend(T)->Unit):Flow<T> =
 upstream.onEach(persist).retryWhen { error,attempt ->
  if(error is CancellationException)throw error
  onFailure(error)
  delay(250L*(attempt.coerceAtMost(19)+1))
  true
 }
