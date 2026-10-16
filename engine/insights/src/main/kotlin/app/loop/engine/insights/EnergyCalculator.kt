package app.loop.engine.insights

import app.loop.core.model.Profile
import app.loop.core.model.Workout
import app.loop.core.model.WorkoutType

/**
 * Calories out = Mifflin-St Jeor resting burn + step-based activity + workouts (MET).
 * Returns null unless weight, height, age and sex are known: no guessing.
 */
object EnergyCalculator {

    fun restingPerDay(weightKg: Double, heightCm: Double, age: Int, sex: Profile.Sex): Double =
        10 * weightKg + 6.25 * heightCm - 5 * age + if (sex == Profile.Sex.MALE) 5 else -161

    /** ~0.57 kcal per step per 1,000 kg of body weight (≈0.04 kcal/step at 70 kg). */
    fun stepsKcal(steps: Int, weightKg: Double): Double = steps * weightKg * 0.00057

    fun met(type: WorkoutType): Double = when (type) {
        WorkoutType.WALK -> 3.5
        WorkoutType.RUN -> 9.8
        WorkoutType.CYCLE -> 7.5
        WorkoutType.GYM -> 5.0
        WorkoutType.YOGA -> 2.5
        WorkoutType.SPORT -> 7.0
        WorkoutType.OTHER -> 4.0
    }

    /**
     * Net extra burn of a workout above resting. Walks and runs are already counted through
     * steps, so they're skipped to avoid double counting.
     */
    fun workoutKcal(workout: Workout, weightKg: Double): Double {
        if (workout.type == WorkoutType.WALK || workout.type == WorkoutType.RUN) return 0.0
        val hours = workout.minutes / 60.0
        return (met(workout.type) - 1) * weightKg * hours
    }

    /**
     * Total for a day. [dayFraction] prorates the resting burn for a day still in progress
     * (1.0 for a finished day).
     */
    fun kcalOut(
        profile: Profile?,
        weightKg: Double?,
        steps: Int?,
        workouts: List<Workout>,
        currentYear: Int,
        dayFraction: Double = 1.0,
    ): Double? {
        val w = weightKg ?: return null
        val h = profile?.heightCm ?: return null
        val sex = profile.sex ?: return null
        val age = profile.birthYear?.let { currentYear - it } ?: return null
        val resting = restingPerDay(w, h, age, sex) * dayFraction.coerceIn(0.0, 1.0)
        val active = (steps?.let { stepsKcal(it, w) } ?: 0.0) + workouts.sumOf { workoutKcal(it, w) }
        return resting + active
    }
}
