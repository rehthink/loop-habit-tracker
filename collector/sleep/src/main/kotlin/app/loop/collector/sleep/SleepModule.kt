package app.loop.collector.sleep

import app.loop.core.domain.repository.SleepReader
import app.loop.core.domain.tracker.TrackerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SleepModule {
    @Binds @IntoSet abstract fun source(source: SleepSource): TrackerSource
    @Binds abstract fun reader(source: SleepSource): SleepReader
}
