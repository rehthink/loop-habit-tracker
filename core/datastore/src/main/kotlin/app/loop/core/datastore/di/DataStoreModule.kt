package app.loop.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.loop.core.datastore.CollectorStateStore
import app.loop.core.datastore.MoveCollectorStateOutOfSettings
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {

    /** File name is referenced by the Auto Backup rules. */
    const val USER_PREFERENCES = "user_preferences"

    @Provides
    @Singleton
    fun userPreferences(
        @ApplicationContext context: Context,
        @Dispatcher(LoopDispatcher.IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope + io,
        produceFile = { context.preferencesDataStoreFile(USER_PREFERENCES) },
    )

    /** Not in the Auto Backup rules, so never backed up. */
    const val COLLECTOR_STATE = "collector_state"

    @Provides
    @Singleton
    @CollectorStateStore
    fun collectorState(
        @ApplicationContext context: Context,
        @Dispatcher(LoopDispatcher.IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
        settings: DataStore<Preferences>,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        migrations = listOf(MoveCollectorStateOutOfSettings(settings)),
        scope = scope + io,
        produceFile = { context.preferencesDataStoreFile(COLLECTOR_STATE) },
    )
}
