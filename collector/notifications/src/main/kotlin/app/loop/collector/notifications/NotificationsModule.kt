package app.loop.collector.notifications

import app.loop.core.domain.repository.MusicReader
import app.loop.core.domain.tracker.TrackerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {
    @Binds @IntoSet abstract fun music(source: MusicSource): TrackerSource
    @Binds abstract fun musicReader(source: MusicSource): MusicReader
}
