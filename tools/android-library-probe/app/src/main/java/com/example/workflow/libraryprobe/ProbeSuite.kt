package com.example.workflow.libraryprobe

import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

@Serializable data class Payload(val message: String, val count: Int)
data class ProbeResult(val name: String, val passed: Boolean, val detail: String = "")
interface ProbeApi { @GET("probe") suspend fun get(): Payload }

/** Binds only loopback, owns all clients/threads, and makes no external requests. */
private class LocalHttp(timeoutMs: Long = 5000) : Closeable {
    val server = MockWebServer()
    val lastCall = AtomicReference<Call>()
    val client = OkHttpClient.Builder().readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(6, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("X-Probe", "local-only").build()) }.build()
    val api: ProbeApi
    init {
        api = CleanupActions.initialize({
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val calls = Call.Factory { request -> client.newCall(request).also { lastCall.set(it) } }
            Retrofit.Builder().baseUrl(server.url("/")).callFactory(calls)
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(ProbeApi::class.java)
        }, { close() })
    }
    override fun close() {
        CleanupActions.runAll(
            { client.dispatcher.cancelAll() },
            { client.connectionPool.evictAll() },
            { client.dispatcher.executorService.shutdownNow(); Unit },
            { server.shutdown() }
        )
    }
}

object ProbeSuite {
    suspend fun run(): List<ProbeResult> {
        val results = mutableListOf<ProbeResult>()
        suspend fun verify(name: String, block: suspend () -> Unit) {
            try { withTimeout(10000) { block() }; results += ProbeResult(name, true) }
            catch (e: TimeoutCancellationException) { results += ProbeResult(name, false, "guard timeout") }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { results += ProbeResult(name, false, "${e.javaClass.simpleName}: ${e.message}") }
        }
        verify("flow-order") { check(flowOf(1, 2, 3).map { it * 2 }.toList() == listOf(2, 4, 6)) }
        verify("flow-cancel") {
            var finalized = false
            val first = flow { try { emit(7); awaitCancellation() } finally { finalized = true } }.take(1).toList()
            check(first == listOf(7) && finalized)
        }
        verify("coroutine-timeout") {
            var finalized = false
            var timedOut = false
            try { withTimeout(30) { try { awaitCancellation() } finally { finalized = true } } }
            catch (e: TimeoutCancellationException) { timedOut = true }
            check(timedOut && finalized)
        }
        verify("json-roundtrip") {
            val input = Payload("한글 · space", 3)
            check(Json.decodeFromString<Payload>(Json.encodeToString(input)) == input)
        }
        verify("json-invalid") {
            var rejected = false
            try { Json.decodeFromString<Payload>("{\"count\":\"wrong\"}") } catch (e: SerializationException) { rejected = true }
            check(rejected)
        }
        verify("http-json") { LocalHttp().use { f ->
            f.server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{\"message\":\"local 한글\",\"count\":3}"))
            check(f.api.get() == Payload("local 한글", 3))
            val req = withContext(Dispatchers.IO) { f.server.takeRequest(2, TimeUnit.SECONDS) }
            check(req?.path == "/probe" && req.method == "GET")
        } }
        verify("http-error") { LocalHttp().use { f ->
            f.server.enqueue(MockResponse().setResponseCode(503).setBody("temporarily unavailable"))
            var code = 0
            try { f.api.get() } catch (e: HttpException) { code = e.code(); e.response()?.errorBody()?.close() }
            check(code == 503)
        } }
        verify("http-invalid-json") { LocalHttp().use { f ->
            f.server.enqueue(MockResponse().setBody("not-json"))
            var rejected = false
            try { f.api.get() } catch (e: SerializationException) { rejected = true }
            check(rejected)
        } }
        verify("http-timeout") { LocalHttp(250).use { f ->
            f.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            var timeout = false
            try { f.api.get() } catch (e: java.net.SocketTimeoutException) { timeout = true }
            check(timeout && f.server.requestCount == 1)
        } }
        verify("http-cancel") { LocalHttp().use { f -> coroutineScope {
            f.server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val work = async { f.api.get() }
            val req = withContext(Dispatchers.IO) { f.server.takeRequest(2, TimeUnit.SECONDS) }
            check(req?.path == "/probe")
            work.cancelAndJoin()
            check(work.isCancelled && f.lastCall.get()?.isCanceled() == true)
        } } }
        verify("okhttp-interceptor") { LocalHttp().use { f ->
            f.server.enqueue(MockResponse().setBody("ok"))
            withContext(Dispatchers.IO) { f.client.newCall(Request.Builder().url(f.server.url("/header")).build()).execute().use { check(it.code == 200 && it.body?.string() == "ok") } }
            val req = withContext(Dispatchers.IO) { f.server.takeRequest(2, TimeUnit.SECONDS) }
            check(req?.getHeader("X-Probe") == "local-only")
        } }
        return results
    }
}
