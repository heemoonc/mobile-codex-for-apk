package com.example.workflow.notes

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.util.Base64
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET

enum class ApiScenario { NORMAL, FAIL_ONCE, INVALID_JSON, OFFLINE }

@Serializable
data class RemoteDto(val id: Long, val title: String, val imagePath: String)

/** Explicit, process-local test fixture; construction does not open a socket. */
@Singleton
class RemoteBackend @Inject constructor() : Closeable {
    private val lock = Any()
    private val scenario = AtomicReference(ApiScenario.NORMAL)
    private val mutableBaseUrl = MutableStateFlow<String?>(null)
    val baseUrl: StateFlow<String?> = mutableBaseUrl.asStateFlow()
    private var server: MockWebServer? = null
    private var client: OkHttpClient? = null
    private var api: StreamingJsonTransport? = null
    private var closed = false

    suspend fun start() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            check(!closed) { "Backend is closed" }
            if (api != null) return@synchronized
            val candidateServer = MockWebServer()
            var candidateClient: OkHttpClient? = null
            try {
                candidateServer.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                        "/sample.png" -> MockResponse()
                            .setHeader("Content-Type", "image/png")
                            .setBody(Buffer().write(SAMPLE_PNG))
                        "/items" -> when (scenario.get()) {
                            ApiScenario.FAIL_ONCE -> if (scenario.compareAndSet(ApiScenario.FAIL_ONCE, ApiScenario.NORMAL)) {
                                MockResponse().setResponseCode(503).setBody("temporarily unavailable")
                            } else normalResponse()
                            ApiScenario.INVALID_JSON -> MockResponse()
                                .setHeader("Content-Type", "application/json").setBody("[{broken json")
                            ApiScenario.OFFLINE -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
                            ApiScenario.NORMAL -> normalResponse()
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
                candidateServer.start(InetAddress.getByName("127.0.0.1"), 0)
                candidateClient = OkHttpClient.Builder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .callTimeout(5, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(false)
                    .build()
                val url = candidateServer.url("/").newBuilder().host("127.0.0.1").build().toString()
                val candidateApi = StreamingJsonTransport(url, candidateClient)
                server = candidateServer
                client = candidateClient
                api = candidateApi
                mutableBaseUrl.value = url
            } catch (error: Throwable) {
                fun cleanup(block: () -> Unit) {
                    try { block() } catch (cleanupError: Throwable) { error.addSuppressed(cleanupError) }
                }
                cleanup { candidateClient?.dispatcher?.executorService?.shutdownNow() }
                cleanup { candidateClient?.connectionPool?.evictAll() }
                cleanup { candidateClient?.cache?.close() }
                cleanup { candidateServer.shutdown() }
                throw error
            }
        }
    }

    suspend fun fetch(): List<RemoteDto> = withContext(Dispatchers.IO) {
        start()
        val service = synchronized(lock) {
            check(!closed) { "Backend is closed" }
            checkNotNull(api)
        }
        service.fetch()
    }

    fun setScenario(scenario: ApiScenario) { this.scenario.set(scenario) }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            var failure: Throwable? = null
            fun cleanup(block: () -> Unit) {
                try { block() } catch (error: Throwable) {
                    val first = failure
                    if (first == null) failure = error else first.addSuppressed(error)
                }
            }
            cleanup { client?.dispatcher?.cancelAll() }
            cleanup { client?.dispatcher?.executorService?.shutdownNow() }
            cleanup { client?.connectionPool?.evictAll() }
            cleanup { client?.cache?.close() }
            cleanup { server?.shutdown() }
            api = null
            client = null
            server = null
            mutableBaseUrl.value = null
            failure?.let { throw it }
        }
    }

    private fun normalResponse() = MockResponse().setHeader("Content-Type", "application/json")
        .setBody("""[{"id":101,"title":"샘플 API 메모","imagePath":"/sample.png"}]""")

    private companion object {
        const val MAX_BODY_BYTES = 64L * 1024
        val SAMPLE_PNG: ByteArray = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR4nGMwnvn/PwAFmQLLFOU//QAAAABJRU5ErkJggg=="
        )
    }
}

fun validatedRemoteItems(items: List<RemoteDto>): List<RemoteDto> {
    require(items.size <= 100) { "Too many items" }
    val ids = HashSet<Long>()
    items.forEach {
        require(it.id > 0 && ids.add(it.id)) { "Invalid or duplicate ID" }
        require(it.title.isNotBlank() && it.title.length <= 120) { "Invalid title" }
        require(it.imagePath == "/sample.png") { "Invalid image path" }
    }
    return items.toList()
}

fun shouldRetryRemote(error: Throwable, attempt: Int): Boolean =
    attempt >= 0 && attempt < 2 && error !is CancellationException &&
        (error is IOException && error !is ResponseTooLargeException || (error is HttpException && error.code() in 500..599))
