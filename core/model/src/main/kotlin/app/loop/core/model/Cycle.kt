package app.loop.core.model

import java.time.LocalDate

/** How heavy the flow was on a day. A day with no flow logged has a null flow, never "zero". */
enum class Flow { SPOTTING, LIGHT, MEDIUM, HEAVY }

enum class Symptom { CRAMPS, HEADACHE, BLOATING, TENDER_BREASTS, BACK_PAIN, ACNE, FATIGUE, CRAVINGS, MOOD_SWINGS, NAUSEA }

/** One day of the period tracker. */
data class CycleDay(
    val date: LocalDate,
    val flow: Flow? = null,
    val symptoms: Set<Symptom> = emptySet(),
    val note: String? = null,
) {
    /** Spotting alone doesn't start or count as a period day. */
    val isPeriodDay: Boolean get() = flow != null && flow != Flow.SPOTTING
    val isEmpty: Boolean get() = flow == null && symptoms.isEmpty() && note.isNullOrBlank()
}
