package app.loop.core.network

object GeminiConfig {
    /** Free-tier Flash model. Change this one constant to switch models. */
    const val MODEL = "gemini-3.8-flash"
    const val BASE_URL = "https://generativelanguage.googleapis.com/"

    val PROMPT = """
        You are looking at a photo of a meal, most likely Indian home-cooked or restaurant food.
        List every separate dish or item you can see. For each give:
        - name: the common dish name (e.g. "dal tadka", "roti", "jeera rice", "paneer butter masala")
        - quantity: how many units are on the plate (a number, can be fractional like 0.5)
        - unit: one of PIECE, KATORI, PLATE, GRAM, CUP, ML. Use PIECE for rotis, idlis, eggs and fruit,
          KATORI for a small bowl of dal, sabzi, rice or curd, PLATE for a full plate of one dish
          (biryani, pulao, noodles), CUP for drinks like tea, coffee or lassi
        - confidence: 0 to 1, how sure you are
        - kcal: your best estimate of the item's total calories
        Only include food and drinks. If there is no food, return an empty list.
    """.trimIndent()
}
