package app.loop.core.model

/** Names shared across modules without a compile dependency on each other. */
object SystemComponents {
    const val NOTIFICATION_LISTENER = "app.loop.collector.notifications.LoopNotificationListener"

    /** Intent extra on the app's launch intent: where to go (see [Routes]). */
    const val EXTRA_ROUTE = "loop.route"

    /** Intent extra: the notification the user tapped, so its ignored streak resets. */
    const val EXTRA_NOTIFICATION_KEY = "loop.notification_key"
}

/** Deep-link routes the app shell understands (from notifications and widgets). */
object Routes {
    const val TODAY = "today"
    const val FOOD_CAMERA = "food/camera"
    const val FOOD_REVIEW_PENDING = "food/pending/" // + pending photo id
    const val WATER = "water"
    const val MOOD = "checkin/mood"
    const val HEALTH = "checkin/health"
    const val SLEEP = "sleep"
    const val MOVEMENT = "movement"
    const val SCREEN_TIME = "screentime"
    const val REPORT = "progress/report"
    const val NOTIFICATION_SETTINGS = "settings/notifications"
    const val CYCLE = "cycle"
}
