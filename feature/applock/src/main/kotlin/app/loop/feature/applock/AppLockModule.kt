package app.loop.feature.applock

import app.loop.core.domain.repository.AppLockGate
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppLockModule {
    /** Other features ask for the PIN before sensitive actions without depending on this module. */
    @Binds abstract fun gate(manager: AppLockManager): AppLockGate
}
