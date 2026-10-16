package app.loop.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.loop.core.designsystem.theme.LoopTheme
import app.loop.core.model.Area
import app.loop.core.model.TrackerId

@Composable
fun TrackerId.label(): String = stringResource(
    when (this) {
        TrackerId.SCREEN_TIME -> R.string.tracker_screen_time
        TrackerId.MOVEMENT -> R.string.tracker_movement
        TrackerId.SLEEP -> R.string.tracker_sleep
        TrackerId.FOOD -> R.string.tracker_food
        TrackerId.WATER -> R.string.tracker_water
        TrackerId.CHECK_INS -> R.string.tracker_check_ins
        TrackerId.MUSIC -> R.string.tracker_music
        TrackerId.PLACES -> R.string.tracker_places
    },
)

@Composable
fun TrackerId.detail(): String = stringResource(
    when (this) {
        TrackerId.SCREEN_TIME -> R.string.tracker_screen_time_detail
        TrackerId.MOVEMENT -> R.string.tracker_movement_detail
        TrackerId.SLEEP -> R.string.tracker_sleep_detail
        TrackerId.FOOD -> R.string.tracker_food_detail
        TrackerId.WATER -> R.string.tracker_water_detail
        TrackerId.CHECK_INS -> R.string.tracker_check_ins_detail
        TrackerId.MUSIC -> R.string.tracker_music_detail
        TrackerId.PLACES -> R.string.tracker_places_detail
    },
)

@Composable
fun Area.label(): String = stringResource(
    when (this) {
        Area.MOVE -> R.string.area_move
        Area.SLEEP -> R.string.area_sleep
        Area.SCREEN -> R.string.area_screen
        Area.FUEL -> R.string.area_fuel
    },
)

@Composable
fun Area.color(): Color {
    val areas = LoopTheme.areas
    return when (this) {
        Area.MOVE -> areas.move
        Area.SLEEP -> areas.sleep
        Area.SCREEN -> areas.screen
        Area.FUEL -> areas.fuel
    }
}
