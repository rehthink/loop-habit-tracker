package app.loop.core.model

/**
 * Every system access Loop can ask for, in the order the permissions screen lists them.
 * [usedBy] says which trackers need it, so a permission is only requested when one of
 * those features is first used.
 */
enum class LoopPermission(val usedBy: Set<TrackerId>) {
    USAGE_ACCESS(setOf(TrackerId.SCREEN_TIME, TrackerId.SLEEP)),
    PHYSICAL_ACTIVITY(setOf(TrackerId.MOVEMENT, TrackerId.SLEEP)),
    HEALTH_CONNECT(setOf(TrackerId.MOVEMENT, TrackerId.SLEEP)),
    NOTIFICATION_ACCESS(setOf(TrackerId.SCREEN_TIME, TrackerId.MUSIC)),
    POST_NOTIFICATIONS(setOf(TrackerId.CHECK_INS)),
    LOCATION(setOf(TrackerId.PLACES)),
    BACKGROUND_LOCATION(setOf(TrackerId.PLACES)),
    CAMERA(setOf(TrackerId.FOOD)),
    BATTERY_UNRESTRICTED(TrackerId.entries.toSet()),
}

enum class PermissionStatus {
    GRANTED,
    DENIED,

    /** This Android version doesn't need it (e.g. notifications before Android 13). */
    NOT_REQUIRED,

    /** Can't be granted right now: the feature isn't installed yet or a prerequisite is missing. */
    UNAVAILABLE,
}

data class PermissionState(
    val permission: LoopPermission,
    val status: PermissionStatus,
) {
    val isSatisfied: Boolean
        get() = status == PermissionStatus.GRANTED || status == PermissionStatus.NOT_REQUIRED
}
