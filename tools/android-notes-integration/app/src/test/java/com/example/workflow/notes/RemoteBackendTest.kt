package com.example.workflow.notes

import java.io.IOException
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class RemoteBackendTest {
    private val sample = RemoteDto(101, "샘플 API 메모", "/sample.png")

    @Test fun normalRequestReturnsItemAndValidPngOverLoopback() = runBlocking {
        RemoteBackend().use { backend ->
            assertNull(backend.baseUrl.value)
            backend.start()
            assertNotNull(backend.baseUrl.value)
            assertTrue(backend.baseUrl.value!!.startsWith("http://127.0.0.1:"))
            assertEquals(listOf(sample), backend.fetch())
            val png = URL(backend.baseUrl.value + "sample.png").openStream().use { it.readBytes() }
            assertArrayEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10), png.take(8).toByteArray())
            val chunks = java.nio.ByteBuffer.wrap(png).apply { position(8) }
            while (chunks.hasRemaining()) {
                val size = chunks.int
                val chunk = ByteArray(size + 4).also { chunks.get(it) }
                val expectedCrc = chunks.int.toLong() and 0xffffffffL
                val crc = java.util.zip.CRC32().apply { update(chunk) }
                assertEquals(expectedCrc, crc.value)
            }
        }
    }
    @Test fun serviceUnavailableOccursOnceThenSucceeds() = runBlocking {
        RemoteBackend().use { backend ->
            backend.setScenario(ApiScenario.FAIL_ONCE)
            val error = runCatching { backend.fetch() }.exceptionOrNull()
            assertTrue(error is HttpException)
            assertEquals(503, (error as HttpException).code())
            assertEquals(listOf(sample), backend.fetch())
        }
    }
    @Test fun malformedJsonFailsDeserialization() = runBlocking {
        RemoteBackend().use { backend ->
            backend.setScenario(ApiScenario.INVALID_JSON)
            assertTrue(runCatching { backend.fetch() }.exceptionOrNull() is SerializationException)
        }
    }
    @Test fun disconnectedServerFailsWithIOException() = runBlocking {
        RemoteBackend().use { backend ->
            backend.setScenario(ApiScenario.OFFLINE)
            assertTrue(runCatching { backend.fetch() }.exceptionOrNull() is IOException)
            backend.setScenario(ApiScenario.NORMAL)
            assertEquals(listOf(sample), backend.fetch())
        }
    }
    @Test fun validationAcceptsOnlyBoundedUniqueSafeItems() {
        assertEquals(listOf(sample), validatedRemoteItems(listOf(sample)))
        assertEquals(emptyList<RemoteDto>(), validatedRemoteItems(emptyList()))
        val invalid = listOf(
            listOf(sample, sample), listOf(sample.copy(id = 0)), listOf(sample.copy(id = -1)),
            listOf(sample.copy(title = " ")), listOf(sample.copy(title = "x".repeat(121))),
            listOf(sample.copy(imagePath = "https://foreign.example/sample.png")),
            listOf(sample.copy(imagePath = "/other.png")),
            (1L..101L).map { sample.copy(id = it) }
        )
        invalid.forEach { items ->
            assertTrue(runCatching { validatedRemoteItems(items) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(100, validatedRemoteItems((1L..100L).map { sample.copy(id = it, title = "x".repeat(120)) }).size)
    }
    @Test fun retriesAreLimitedToTransientFailures() {
        val serverError = HttpException(Response.error<Any>(503, "unavailable".toResponseBody()))
        val badRequest = HttpException(Response.error<Any>(400, "bad".toResponseBody()))
        for (error in listOf(IOException("offline"), serverError)) {
            assertTrue(shouldRetryRemote(error, 0))
            assertTrue(shouldRetryRemote(error, 1))
            assertFalse(shouldRetryRemote(error, 2))
            assertFalse(shouldRetryRemote(error, 3))
        }
        for (error in listOf(badRequest, CancellationException(), SerializationException("bad json"), IllegalArgumentException())) {
            assertFalse(shouldRetryRemote(error, 0))
        }
    }
}
