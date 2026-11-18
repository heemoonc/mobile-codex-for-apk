package com.example.workflow.notes
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class StreamingJsonTransportTest {
 @Test fun oversizedChunkedBodyStopsBeforeWholeDownload()=runBlocking {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setChunkedBody("x".repeat(2*1024*1024),1024).throttleBody(2048,10,TimeUnit.MILLISECONDS))
   server.start()
   val client=OkHttpClient.Builder().build()
   try {
    val started=System.nanoTime()
    val failure=runCatching {StreamingJsonTransport(server.url("/").toString(),client).fetch()}.exceptionOrNull()
    assertEquals("ResponseTooLargeException",failure?.javaClass?.simpleName)
    assertTrue("Must reject before full 10 second download",TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)<2500)
   }finally{client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
  }
 }
 @Test fun cancellationInterruptsBodyRead()=runBlocking {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setBody("[]").setBodyDelay(3,TimeUnit.SECONDS));server.start()
   val client=OkHttpClient.Builder().build()
   try {
    val job=async(Dispatchers.IO){runCatching{StreamingJsonTransport(server.url("/").toString(),client).fetch()}}
    assertNotNull("Request must start",server.takeRequest(2,TimeUnit.SECONDS))
    job.cancel();withTimeout(2000){job.join()};assertTrue(job.isCancelled)
    withTimeout(1500){while(client.dispatcher.runningCallsCount()>0)delay(10)}
    assertEquals(0,client.dispatcher.runningCallsCount())
   }finally{client.dispatcher.cancelAll();client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
  }
 }
 @Test fun oversizedGzipSuccessAndErrorAreBoundedAfterInflation()=runBlocking {
  for(status in listOf(200,401))MockWebServer().use { server ->
   val bytes=java.io.ByteArrayOutputStream().also { out -> java.util.zip.GZIPOutputStream(out).use {it.write("x".repeat(1024*1024).toByteArray())} }.toByteArray()
   server.enqueue(MockResponse().setResponseCode(status).setHeader("Content-Encoding","gzip").setBody(okio.Buffer().write(bytes)));server.start()
   val client=OkHttpClient.Builder().build()
   try {
    val failure=runCatching {StreamingJsonTransport(server.url("/").toString(),client).fetch()}.exceptionOrNull()
    assertEquals("status $status must limit decoded body","ResponseTooLargeException",failure?.javaClass?.simpleName)
   }finally{client.dispatcher.executorService.shutdownNow();client.connectionPool.evictAll()}
  }
 }

}
