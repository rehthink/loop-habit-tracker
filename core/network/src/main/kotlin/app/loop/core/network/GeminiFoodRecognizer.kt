package app.loop.core.network

import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import app.loop.core.domain.repository.ApiKeyRepository
import app.loop.core.domain.repository.FoodRecognizer
import app.loop.core.model.PortionUnit
import app.loop.core.model.RecognizedItem
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import retrofit2.HttpException

/**
 * Recognises dishes with Gemini (free tier, the user's own key). Calories shown to the user come
 * from the bundled food table; Gemini's kcal is only a fallback for unknown dishes.
 * Never logs the key or the photo.
 */
internal class GeminiFoodRecognizer @Inject constructor(
    private val api: GeminiApi,
    private val keys: ApiKeyRepository,
    private val json: Json,
    @Dispatcher(LoopDispatcher.IO) private val io: CoroutineDispatcher,
) : FoodRecognizer {

    override suspend fun recognize(jpeg: ByteArray): FoodRecognizer.Result = withContext(io) {
        val key = keys.getKey() ?: return@withContext FoodRecognizer.Result.NoKey
        val request = GenerateRequest(
            contents = listOf(
                Content(
                    listOf(
                        Part(text = GeminiConfig.PROMPT),
                        Part(inlineData = InlineData("image/jpeg", Base64.getEncoder().encodeToString(jpeg))),
                    ),
                ),
            ),
            generationConfig = GenerationConfig(responseSchema = SCHEMA),
        )
        try {
            val response = api.generateContent(GeminiConfig.MODEL, key, request)
            val text = response.candidates.firstOrNull()?.content?.parts?.firstNotNullOfOrNull { it.text }
                ?: return@withContext FoodRecognizer.Result.Success(emptyList())
            FoodRecognizer.Result.Success(parse(text))
        } catch (e: HttpException) {
            when {
                e.code() == 429 -> FoodRecognizer.Result.RateLimited
                e.code() == 401 || e.code() == 403 || isBadKey(e) -> FoodRecognizer.Result.Failed("key")
                else -> FoodRecognizer.Result.Failed("http ${e.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            FoodRecognizer.Result.Offline
        } catch (e: Exception) {
            FoodRecognizer.Result.Failed(e::class.simpleName ?: "error")
        }
    }

    /**
     * Google answers a wrong key with 400 and reason API_KEY_INVALID. Other 400s (an image or
     * request it won't take) aren't the key's fault. The body is only searched, never logged.
     */
    private fun isBadKey(e: HttpException): Boolean =
        e.code() == 400 && runCatching { e.response()?.errorBody()?.string() }.getOrNull()?.contains("API_KEY_INVALID") == true

    internal fun parse(text: String): List<RecognizedItem> =
        json.decodeFromString(MealItems.serializer(), text).items
            .filter { it.name.isNotBlank() }
            .map { dto ->
                RecognizedItem(
                    name = dto.name.trim(),
                    quantity = dto.quantity.takeIf { it > 0 } ?: 1.0,
                    unit = PortionUnit.entries.firstOrNull { it.name.equals(dto.unit, ignoreCase = true) } ?: PortionUnit.PIECE,
                    confidence = dto.confidence.coerceIn(0.0, 1.0),
                    kcalGuess = dto.kcal?.takeIf { it >= 0 },
                )
            }

    companion object {
        /** Strict JSON output schema (OpenAPI subset, as Gemini expects). */
        val SCHEMA: JsonObject = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") {
                putJsonObject("items") {
                    put("type", "ARRAY")
                    putJsonObject("items") {
                        put("type", "OBJECT")
                        putJsonObject("properties") {
                            putJsonObject("name") { put("type", "STRING") }
                            putJsonObject("quantity") { put("type", "NUMBER") }
                            putJsonObject("unit") {
                                put("type", "STRING")
                                put("enum", buildJsonArray { PortionUnit.entries.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.name)) } })
                            }
                            putJsonObject("confidence") { put("type", "NUMBER") }
                            putJsonObject("kcal") { put("type", "NUMBER") }
                        }
                        putJsonArray("required") {
                            listOf("name", "quantity", "unit", "confidence").forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                        }
                    }
                }
            }
            putJsonArray("required") { add(kotlinx.serialization.json.JsonPrimitive("items")) }
        }
    }
}
