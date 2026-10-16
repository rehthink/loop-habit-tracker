package app.loop.collector.screentime

import app.loop.core.domain.repository.ScreenTimeReader
import app.loop.core.domain.tracker.TrackerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ScreenTimeModule {
    @Binds @IntoSet abstract fun source(source: ScreenTimeSource): TrackerSource
    @Binds abstract fun reader(source: ScreenTimeSource): ScreenTimeReader
}
