package app.loop.engine.insights

import app.loop.core.model.Area
import app.loop.core.model.DaySummary
import app.loop.core.model.Goal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class Grade(val label: String) { A("A"), B("B"), C("C"), D("D"), E("E") }

data class AreaGrade(val area: Area, val grade: Grade?, val averageScore: Int?)

data class PersonalBest(val title: String, val value: String, val date: LocalDate)

data class ReportCard(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val grades: List<AreaGrade>,
    val habitScore: Int?,
    val previousHabitScore: Int?,
    val daysTracked: Int,
)

/** The Sunday report card and personal bests. Pure functions over summaries. */
object WeeklyReview {

    fun weekOf(date: LocalDate): Pair<LocalDate, LocalDate> {
        val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return start to start.plusDays(6)
    }

    fun grade(score: Int): Grade = when {
        score >= 85 -> Grade.A
        score >= 70 -> Grade.B
        score >= 55 -> Grade.C
        score >= 40 -> Grade.D
        else -> Grade.E
    }

    fun reportCard(days: List<DaySummary>, goals: List<Goal>, anyDayInWeek: LocalDate): ReportCard {
        val (start, end) = weekOf(anyDayInWeek)
        val week = days.filter { it.date in start..end }
        val previous = days.filter { it.date in start.minusDays(7)..end.minusDays(7) }
        val grades = Area.entries.map { area ->
            val scores = week.mapNotNull { Scoring.areaScores(it, goals)[area] }
            val avg = scores.takeIf { it.isNotEmpty() }?.average()?.toInt()
            AreaGrade(area, avg?.let(::grade), avg)
        }
        return ReportCard(
            weekStart = start,
            weekEnd = end,
            grades = grades,
            habitScore = week.mapNotNull { Scoring.habitScore(it, goals) }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            previousHabitScore = previous.mapNotNull { Scoring.habitScore(it, goals) }.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            daysTracked = week.count { it.dataCompleteness > 0 },
        )
    }

    /** All-time bests across every summary. */
    fun personalBests(days: List<DaySummary>): List<PersonalBest> = buildList {
        days.filter { it.steps != null }.maxByOrNull { it.steps!! }?.let {
            add(PersonalBest("Most steps", "${Format.count(it.steps!!.toDouble())} steps", it.date))
        }
        days.filter { it.sleepMin != null }.maxByOrNull { it.sleepMin!! }?.let {
            add(PersonalBest("Longest sleep", Format.minutes(it.sleepMin!!.toDouble()), it.date))
        }
        days.filter { it.focusMin != null && it.focusMin!! > 0 }.maxByOrNull { it.focusMin!! }?.let {
            add(PersonalBest("Most focus time", Format.minutes(it.focusMin!!.toDouble()), it.date))
        }
        days.filter { it.screenMin != null && it.isComplete }.minByOrNull { it.screenMin!! }?.let {
            add(PersonalBest("Lightest screen day", Format.minutes(it.screenMin!!.toDouble()), it.date))
        }
        days.filter { it.waterGlasses != null }.maxByOrNull { it.waterGlasses!! }?.let {
            add(PersonalBest("Most water", Format.value(Metric.WATER, it.waterGlasses!!.toDouble()), it.date))
        }
    }
}
