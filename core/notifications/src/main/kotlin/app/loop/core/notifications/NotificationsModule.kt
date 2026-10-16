package app.loop.core.notifications

import app.loop.core.domain.repository.ConfirmationNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NotificationsModule {
    @Binds abstract fun notifier(engine: NotificationEngine): ConfirmationNotifier
}
