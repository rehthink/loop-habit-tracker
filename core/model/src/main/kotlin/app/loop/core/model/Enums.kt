package app.loop.core.model

/** The four daily rings. Each area keeps one fixed colour across the app. */
enum class Area { MOVE, SLEEP, SCREEN, FUEL }

enum class RawEventType {
    STEP,
    ACTIVITY,
    APP_OPEN,
    APP_CLOSE,
    UNLOCK,
    NOTIFICATION,
    MUSIC_PLAY,
    MUSIC_PAUSE,
    SCREEN_OFF,
    GEOFENCE_ENTER,
    GEOFENCE_EXIT,
}

enum class SleepSource { SLEEP_API, USAGE_GAP, WATCH, USER }

enum class WorkoutSource { AUTO, GEOFENCE, WATCH, USER }

enum class WorkoutType { WALK, RUN, CYCLE, GYM, YOGA, SPORT, OTHER }

enum class MealType { BREAKFAST, LUNCH, SNACK, DINNER }

enum class PortionUnit { PIECE, KATORI, PLATE, GRAM, CUP, ML }

enum class MoodSlot { MORNING, AFTERNOON, NIGHT }

enum class Place { OFFICE, HOME, OTHER }

enum class GoalDirection { AT_LEAST, AT_MOST }

enum class GoalMetric { STEPS, SLEEP_MIN, SCREEN_MIN, WATER_GLASSES, KCAL_IN, FOCUS_MIN, WEIGHT_KG }

enum class ThemeMode { SYSTEM, LIGHT, DARK }
