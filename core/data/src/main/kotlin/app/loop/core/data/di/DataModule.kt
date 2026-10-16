package app.loop.core.data.di

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import app.loop.core.data.repository.ApiKeyRepositoryImpl
import app.loop.core.data.repository.AppLabelProviderImpl
import app.loop.core.data.repository.BackupRepositoryImpl
import app.loop.core.data.repository.CheckInRepositoryImpl
import app.loop.core.data.repository.CollectorStateRepositoryImpl
import app.loop.core.data.repository.CycleRepositoryImpl
import app.loop.core.data.repository.DrinkRepositoryImpl
import app.loop.core.data.repository.FoodRepositoryImpl
import app.loop.core.data.repository.GoalRepositoryImpl
import app.loop.core.data.repository.HealthRepositoryImpl
import app.loop.core.data.repository.MealRepositoryImpl
import app.loop.core.data.repository.NotificationStatRepositoryImpl
import app.loop.core.data.repository.PlacesRepositoryImpl
import app.loop.core.data.repository.RawEventRepositoryImpl
import app.loop.core.data.repository.SleepRepositoryImpl
import app.loop.core.data.repository.SummaryRepositoryImpl
import app.loop.core.data.repository.WorkoutRepositoryImpl
import app.loop.core.data.repository.MaintenanceRepositoryImpl
import app.loop.core.data.repository.PermissionRepositoryImpl
import app.loop.core.data.repository.PinRepositoryImpl
import app.loop.core.data.repository.ProfileRepositoryImpl
import app.loop.core.data.repository.SettingsRepositoryImpl
import app.loop.core.domain.ApplicationScope
import app.loop.core.data.system.AndroidDeviceEpoch
import app.loop.core.domain.DeviceClock
import app.loop.core.domain.tracker.DeviceEpoch
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import app.loop.core.domain.repository.ApiKeyRepository
import app.loop.core.domain.repository.AppLabelProvider
import app.loop.core.domain.repository.BackupRepository
import app.loop.core.domain.repository.CheckInRepository
import app.loop.core.domain.repository.CollectorStateRepository
import app.loop.core.domain.repository.CycleRepository
import app.loop.core.domain.repository.DrinkRepository
import app.loop.core.domain.repository.FoodRepository
import app.loop.core.domain.repository.GoalRepository
import app.loop.core.domain.repository.HealthRepository
import app.loop.core.domain.repository.MealRepository
import app.loop.core.domain.repository.NotificationStatRepository
import app.loop.core.domain.repository.PlacesRepository
import app.loop.core.domain.repository.RawEventRepository
import app.loop.core.domain.repository.SleepRepository
import app.loop.core.domain.repository.SummaryRepository
import app.loop.core.domain.repository.WorkoutRepository
import app.loop.core.domain.repository.MaintenanceRepository
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.domain.repository.PinRepository
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun profile(impl: ProfileRepositoryImpl): ProfileRepository
    @Binds abstract fun permissions(impl: PermissionRepositoryImpl): PermissionRepository
    @Binds abstract fun pin(impl: PinRepositoryImpl): PinRepository
    @Binds abstract fun apiKey(impl: ApiKeyRepositoryImpl): ApiKeyRepository
    @Binds abstract fun maintenance(impl: MaintenanceRepositoryImpl): MaintenanceRepository
    @Binds abstract fun rawEvents(impl: RawEventRepositoryImpl): RawEventRepository
    @Binds abstract fun collectorState(impl: CollectorStateRepositoryImpl): CollectorStateRepository
    @Binds abstract fun sleep(impl: SleepRepositoryImpl): SleepRepository
    @Binds abstract fun workouts(impl: WorkoutRepositoryImpl): WorkoutRepository
    @Binds abstract fun places(impl: PlacesRepositoryImpl): PlacesRepository
    @Binds abstract fun drinks(impl: DrinkRepositoryImpl): DrinkRepository
    @Binds abstract fun checkIns(impl: CheckInRepositoryImpl): CheckInRepository
    @Binds abstract fun health(impl: HealthRepositoryImpl): HealthRepository
    @Binds abstract fun goals(impl: GoalRepositoryImpl): GoalRepository
    @Binds abstract fun summaries(impl: SummaryRepositoryImpl): SummaryRepository
    @Binds abstract fun notificationStats(impl: NotificationStatRepositoryImpl): NotificationStatRepository
    @Binds abstract fun appLabels(impl: AppLabelProviderImpl): AppLabelProvider
    @Binds abstract fun foods(impl: FoodRepositoryImpl): FoodRepository
    @Binds abstract fun meals(impl: MealRepositoryImpl): MealRepository
    @Binds abstract fun backup(impl: BackupRepositoryImpl): BackupRepository
    @Binds abstract fun cycle(impl: CycleRepositoryImpl): CycleRepository
    @Binds abstract fun deviceEpoch(impl: AndroidDeviceEpoch): DeviceEpoch
}

@Module
@InstallIn(SingletonComponent::class)
internal object CoroutinesModule {
    @Provides
    @Dispatcher(LoopDispatcher.IO)
    fun io(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(LoopDispatcher.DEFAULT)
    fun cpu(): CoroutineDispatcher = Dispatchers.Default

    /**
     * Receivers and the notification listener finish their work here. A failure is logged (by
     * type only in release builds: messages can hold personal data) rather than crashing the app
     * in the background.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(
        @ApplicationContext context: Context,
        @Dispatcher(LoopDispatcher.DEFAULT) dispatcher: CoroutineDispatcher,
    ): CoroutineScope {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val handler = CoroutineExceptionHandler { _, e ->
            if (debuggable) Log.w("Loop", "background work failed", e) else Log.w("Loop", "background work failed: ${e::class.simpleName}")
        }
        return CoroutineScope(SupervisorJob() + dispatcher + handler)
    }

    /** Follows the phone's time zone as it changes (travel), even inside long-lived singletons. */
    @Provides
    @Singleton
    fun clock(): Clock = DeviceClock()
}
