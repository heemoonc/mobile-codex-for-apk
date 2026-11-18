package com.example.workflow.notes
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
class ProbeHttpTest {
 @Test fun cancellingProbeStopsActiveSocketRead()=runBlocking {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setBody("hello").setBodyDelay(3,TimeUnit.SECONDS));server.start()
   val client=OkHttpClient()
   try {
    val job=async(Dispatchers.IO){runCatching{probeHttpStatus(client,server.url("/").toString())}}
    assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))
    job.cancel();withTimeout(1500){job.join();while(client.dispatcher.runningCallsCount()>0)delay(10)}
    assertTrue(job.isCancelled);assertEquals(0,client.dispatcher.runningCallsCount())
   }finally{client.dispatcher.cancelAll();client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
  }
 }
 @Test fun authenticationStatusRemains401AndBodyIsClosed()=runBlocking {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setResponseCode(401).setBody("unauthorized"));server.start();val client=OkHttpClient()
   try {
    val result=runCatching{probeHttpStatus(client,server.url("/").toString())}.getOrNull()
    assertEquals(401,result?.code)
    withTimeout(1500){while(client.dispatcher.runningCallsCount()>0)delay(10)}
   }finally{client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
  }
 }
 @Test fun excessiveErrorBodyIsRejected()=runBlocking {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setResponseCode(401).setChunkedBody("x".repeat(128*1024),1024));server.start();val client=OkHttpClient()
   try {
    val error=runCatching{probeHttpStatus(client,server.url("/").toString())}.exceptionOrNull()
    assertTrue(error is ResponseTooLargeException)
   }finally{client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
  }
 }
}
