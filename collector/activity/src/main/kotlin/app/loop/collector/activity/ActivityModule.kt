package app.loop.collector.activity

import app.loop.core.domain.tracker.TrackerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ActivityModule {
    @Binds @IntoSet abstract fun source(source: ActivitySource): TrackerSource
}
