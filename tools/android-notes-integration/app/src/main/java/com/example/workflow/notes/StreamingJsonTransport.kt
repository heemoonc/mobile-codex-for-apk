package com.example.workflow.notes

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.*
import okio.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.http.GET
import retrofit2.http.Streaming

class ResponseTooLargeException:IOException("Response exceeds 64 KiB")
private const val MAX_RESPONSE=64L*1024

/** Enforces the limit before Retrofit can buffer even an HTTP error body. */
internal fun limitedBody(body:ResponseBody):ResponseBody=object:ResponseBody() {
 override fun contentType()=body.contentType()
 override fun contentLength()=body.contentLength()
 private val limited=object:ForwardingSource(body.source()) {
  var received=0L
  override fun read(sink:Buffer,byteCount:Long):Long {
   val count=super.read(sink,minOf(byteCount,MAX_RESPONSE-received+1))
   if(count>0){received+=count;if(received>MAX_RESPONSE)throw ResponseTooLargeException()}
   return count
  }
 }.buffer()
 override fun source()=limited
}
private interface StreamingApi {@Streaming @GET("items") fun items():Call<ResponseBody>}
class StreamingJsonTransport(baseUrl:String,client:OkHttpClient) {
 private val api=Retrofit.Builder().baseUrl(baseUrl).callbackExecutor(java.util.concurrent.Executor{it.run()}).client(client.newBuilder().addInterceptor { chain ->
  val response=chain.proceed(chain.request())
  response.newBuilder().body(response.body?.let(::limitedBody)).build()
 }.build()).build().create(StreamingApi::class.java)
 suspend fun fetch():List<RemoteDto> = suspendCancellableCoroutine { continuation ->
  val call=api.items()
  continuation.invokeOnCancellation{call.cancel()}
  call.enqueue(object:Callback<ResponseBody> {
   override fun onFailure(call:Call<ResponseBody>,error:Throwable){if(continuation.isActive)continuation.resumeWithException(error)}
   override fun onResponse(call:Call<ResponseBody>,response:Response<ResponseBody>) {
    try {
     if(!response.isSuccessful){response.errorBody()?.close();throw HttpException(response)}
     val text=checkNotNull(response.body()).use{it.string()}
     val items=validatedRemoteItems(Json.decodeFromString<List<RemoteDto>>(text))
     if(continuation.isActive)continuation.resume(items)
    }catch(error:Exception){if(continuation.isActive)continuation.resumeWithException(error)}
   }
  })
 }
}
