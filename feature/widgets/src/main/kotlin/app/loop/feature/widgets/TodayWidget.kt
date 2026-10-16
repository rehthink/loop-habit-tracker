package app.loop.feature.widgets

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.loop.core.domain.repository.SettingsRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.usecase.LogDrinkUseCase
import app.loop.core.model.DayClock
import app.loop.core.model.DaySummary
import app.loop.core.model.Routes
import app.loop.core.model.SystemComponents
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun summaries(): SummaryRepository
    fun settings(): SettingsRepository
    fun logDrink(): LogDrinkUseCase
    fun clock(): Clock
}

private fun entry(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

/** Today's rings as numbers, plus one-tap water, meal photo and mood. Refreshed by the background tick. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val e = entry(context)
        val boundary = e.settings().settings.first().dayBoundary
        val clock = e.clock()
        val summary = e.summaries().get(DayClock.today(clock.millis(), clock.zone, boundary))
        provideContent { GlanceTheme { Content(context, summary) } }
    }

    companion object {
        suspend fun refresh(context: Context) = runCatching { TodayWidget().updateAll(context) }
    }
}

private val Move = Color(0xFF2F9E6A)
private val Sleep = Color(0xFF5B5BD6)
private val Screen = Color(0xFFE8743B)
private val Fuel = Color(0xFFD9A21B)

@Composable
private fun Content(context: Context, summary: DaySummary?) {
    val none = context.getString(R.string.widget_none)
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Stat(context.getString(R.string.widget_move), summary?.steps?.let { "%,d".format(it) } ?: none, Move)
            Stat(context.getString(R.string.widget_sleep), summary?.sleepMin?.let(::minutes) ?: none, Sleep)
            Stat(context.getString(R.string.widget_screen), summary?.screenMin?.let(::minutes) ?: none, Screen)
            Stat(context.getString(R.string.widget_fuel), summary?.waterGlasses?.let { "$it gl" } ?: none, Fuel)
        }
        Spacer(GlanceModifier.height(10.dp))
        Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(context.getString(R.string.widget_water), actionRunCallback<AddWaterAction>())
            Spacer(GlanceModifier.width(6.dp))
            Button(context.getString(R.string.widget_meal), actionStartActivity(openApp(context, Routes.FOOD_CAMERA)))
            Spacer(GlanceModifier.width(6.dp))
            Button(context.getString(R.string.widget_mood), actionStartActivity(openApp(context, Routes.MOOD)))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column(GlanceModifier.width(72.dp).padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface))
        Text(label, style = TextStyle(fontSize = 12.sp, color = ColorProvider(color)))
    }
}

private fun minutes(min: Int) = if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

private fun openApp(context: Context, route: String): Intent =
    (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(SystemComponents.EXTRA_ROUTE, route)

class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        entry(context).logDrink().water()
        TodayWidget().update(context, glanceId)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
