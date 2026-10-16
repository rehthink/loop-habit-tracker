package app.loop.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

internal interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GenerateRequest,
    ): GenerateResponse
}

@Serializable
internal data class GenerateRequest(
    val contents: List<Content>,
    @SerialName("generationConfig") val generationConfig: GenerationConfig,
)

@Serializable
internal data class Content(val parts: List<Part>)

@Serializable
internal data class Part(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: InlineData? = null,
)

@Serializable
internal data class InlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String,
)

@Serializable
internal data class GenerationConfig(
    @SerialName("response_mime_type") val responseMimeType: String = "application/json",
    @SerialName("response_schema") val responseSchema: JsonObject,
    val temperature: Double = 0.2,
)

@Serializable
internal data class GenerateResponse(val candidates: List<Candidate> = emptyList())

@Serializable
internal data class Candidate(val content: CandidateContent? = null)

@Serializable
internal data class CandidateContent(val parts: List<ResponsePart> = emptyList())

@Serializable
internal data class ResponsePart(val text: String? = null)

/** The structured output we ask for. */
@Serializable
internal data class MealItems(val items: List<MealItemDto> = emptyList())

@Serializable
internal data class MealItemDto(
    val name: String,
    val quantity: Double = 1.0,
    val unit: String = "PIECE",
    val confidence: Double = 0.5,
    val kcal: Double? = null,
)
