package com.example.workflow.storageprobe
import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,30,31,32,33,34,35], application=Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class LibrarySimulationTest {
 private fun checkResults(results:List<ProbeResult>,count:Int) {
  assertEquals(count,results.size)
  results.forEach { assertTrue(it.name+": "+it.detail,it.passed); println("MC_LIBRARY_PASS="+it.name+" API="+RuntimeEnvironment.getApiLevel()) }
 }
 @Test fun dataStorePersistenceAndAtomicUpdates() = runBlocking {
  checkResults(DataStoreVerifier.run(RuntimeEnvironment.getApplication().filesDir),4)
 }
 @Test fun roomCrudFlowRollbackAndMigration() = runBlocking {
  checkResults(RoomVerifier.run(RuntimeEnvironment.getApplication()),6)
 }
 @Test fun httpSuccessAndServerFailure() {
  MockWebServer().use { server ->
   server.enqueue(MockResponse().setResponseCode(200).setBody("local-response"))
   server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))
   server.start()
   val client=OkHttpClient()
   try {
    client.newCall(Request.Builder().url(server.url("/probe")).build()).execute().use { response -> assertTrue(response.isSuccessful); assertEquals("local-response",response.body!!.string()) }
    assertEquals("/probe",server.takeRequest().path)
    client.newCall(Request.Builder().url(server.url("/failure")).build()).execute().use { response -> assertFalse(response.isSuccessful); assertEquals(503,response.code) }
    println("MC_LIBRARY_PASS=http-success-and-503 API="+RuntimeEnvironment.getApiLevel())
   } finally { client.dispatcher.executorService.shutdown();client.connectionPool.evictAll() }
  }
 }
}
