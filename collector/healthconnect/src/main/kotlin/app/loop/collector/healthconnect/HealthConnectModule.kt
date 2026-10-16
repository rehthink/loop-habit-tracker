package app.loop.collector.healthconnect

import app.loop.core.domain.repository.HealthConnectGateway
import app.loop.core.domain.repository.HeartRateReader
import app.loop.core.domain.tracker.TrackerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class HealthConnectModule {
    @Binds @IntoSet abstract fun source(source: HealthConnectSource): TrackerSource
    @Binds abstract fun gateway(source: HealthConnectSource): HealthConnectGateway
    @Binds abstract fun heartRate(source: HealthConnectSource): HeartRateReader
}
