package app.loop.core.model

/**
 * Every data source the app can collect from. Each one can be switched off through a
 * feature flag, and each has exactly one TrackerSource implementation once built.
 */
enum class TrackerId(val area: Area?) {
    SCREEN_TIME(Area.SCREEN),
    MOVEMENT(Area.MOVE),
    SLEEP(Area.SLEEP),
    FOOD(Area.FUEL),
    WATER(Area.FUEL),
    CHECK_INS(null),
    MUSIC(null),
    PLACES(null),
}
