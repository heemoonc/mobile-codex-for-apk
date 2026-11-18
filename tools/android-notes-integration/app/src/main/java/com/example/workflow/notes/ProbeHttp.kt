package com.example.workflow.notes

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class ProbeStatus(val code:Int,val tls:String?)

/** Callbacks consume bounded bodies off the main thread, including error responses. */
suspend fun probeHttpStatus(client:OkHttpClient,url:String):ProbeStatus = suspendCancellableCoroutine { continuation ->
 val call=client.newCall(Request.Builder().url(url).build())
 continuation.invokeOnCancellation{call.cancel()}
 call.enqueue(object:Callback {
  override fun onFailure(call:Call,error:IOException){if(continuation.isActive)continuation.resumeWithException(error)}
  override fun onResponse(call:Call,response:Response) {
   try {
    val result=response.use {
     it.body?.let{body->limitedBody(body).use{bounded->bounded.string()}}
     ProbeStatus(it.code,it.handshake?.tlsVersion?.javaName)
    }
    if(continuation.isActive)continuation.resume(result)
   }catch(error:Exception){if(continuation.isActive)continuation.resumeWithException(error)}
  }
 })
}
