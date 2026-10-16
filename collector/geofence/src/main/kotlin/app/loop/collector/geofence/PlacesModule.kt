package app.loop.collector.geofence

import app.loop.core.domain.repository.CurrentLocationProvider
import app.loop.core.domain.repository.GeofenceRegistrar
import app.loop.core.domain.tracker.TrackerSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlacesModule {
    @Binds @IntoSet abstract fun source(source: PlacesSource): TrackerSource
    @Binds abstract fun registrar(source: PlacesSource): GeofenceRegistrar
    @Binds abstract fun location(source: PlacesSource): CurrentLocationProvider
}
