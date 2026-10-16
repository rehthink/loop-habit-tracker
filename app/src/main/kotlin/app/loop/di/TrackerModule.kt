package app.loop.di

import app.loop.core.domain.tracker.TrackerSource
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

/**
 * Declares the set of trackers. Each :collector:* module contributes its source with
 * `@Binds @IntoSet`; the set is empty until the first collector lands in Phase 2.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TrackerModule {
    @Multibinds
    abstract fun trackerSources(): Set<TrackerSource>
}
