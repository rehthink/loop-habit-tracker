package app.loop.collector.steps

import app.loop.core.domain.tracker.TrackerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StepsModule {
    @Binds @IntoSet abstract fun source(source: StepsSource): TrackerSource
}
