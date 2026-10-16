package app.loop.core.model

/**
 * The local profile. [id] is a random UUID created on first open; it is the hook for an
 * optional login or sync later, without changing any screen.
 */
data class Profile(
    val id: String,
    val name: String?,
    val createdAtMillis: Long,
    val zoneId: String,
    val birthYear: Int? = null,
    val sex: Sex? = null,
    val heightCm: Double? = null,
) {
    enum class Sex { FEMALE, MALE }
}
