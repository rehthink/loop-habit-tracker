package app.loop.core.network

import app.loop.core.domain.repository.ApiKeyRepository
import app.loop.core.domain.repository.FoodRecognizer
import app.loop.core.model.PortionUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class GeminiFoodRecognizerTest {
    private val server = MockWebServer()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }
    private var key: String? = "test-key"
    private val keys = object : ApiKeyRepository {
        override val savedKeyHint = flowOf<String?>(null)
        override suspend fun getKey() = key
        override suspend fun saveKey(key: String) = Unit
        override suspend fun clearKey() = Unit
    }
    private lateinit var recognizer: GeminiFoodRecognizer

    @Before
    fun setUp() {
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(GeminiApi::class.java)
        recognizer = GeminiFoodRecognizer(api, keys, json, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = server.close()

    private fun respond(code: Int, body: String = "{}") =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `parses structured items and sends the key in a header, not the url`() = runTest {
        val inner = """{"items":[{"name":"Dal tadka","quantity":1,"unit":"katori","confidence":0.9,"kcal":160},{"name":"Roti","quantity":2,"unit":"PIECE","confidence":0.95}]}"""
        respond(200, """{"candidates":[{"content":{"parts":[{"text":${json.encodeToString(kotlinx.serialization.json.JsonPrimitive(inner))}}]}}]}""")

        val result = recognizer.recognize(byteArrayOf(1, 2, 3)) as FoodRecognizer.Result.Success

        assertEquals(listOf("Dal tadka", "Roti"), result.items.map { it.name })
        assertEquals(PortionUnit.KATORI, result.items[0].unit)
        assertEquals(160.0, result.items[0].kcalGuess!!, 0.0)
        val request = server.takeRequest()
        assertEquals("test-key", request.headers["x-goog-api-key"])
        assertFalse(request.url.toString().contains("test-key"))
        assertTrue(request.url.encodedPath.endsWith("${GeminiConfig.MODEL}:generateContent"))
    }

    @Test
    fun `rate limit, bad key and no key map to their own results`() = runTest {
        respond(429)
        assertEquals(FoodRecognizer.Result.RateLimited, recognizer.recognize(byteArrayOf(1)))
        respond(403)
        assertEquals(FoodRecognizer.Result.Failed("key"), recognizer.recognize(byteArrayOf(1)))
        key = null
        assertEquals(FoodRecognizer.Result.NoKey, recognizer.recognize(byteArrayOf(1)))
    }

    @Test
    fun `a 400 is the key's fault only when Google says the key is invalid`() = runTest {
        respond(400, """{"error":{"code":400,"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""")
        assertEquals(FoodRecognizer.Result.Failed("key"), recognizer.recognize(byteArrayOf(1)))
        respond(400, """{"error":{"code":400,"status":"INVALID_ARGUMENT","message":"Image too large"}}""")
        assertEquals(FoodRecognizer.Result.Failed("http 400"), recognizer.recognize(byteArrayOf(1)))
    }

    @Test
    fun `no network is offline`() = runTest {
        server.close()
        assertEquals(FoodRecognizer.Result.Offline, recognizer.recognize(byteArrayOf(1)))
    }
}
