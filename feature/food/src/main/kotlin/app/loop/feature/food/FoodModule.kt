package app.loop.feature.food

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.loop.core.domain.repository.PhotoAnalysisScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Runs [AnalysePhotosWorker] once there's a connection; repeated calls while it's queued are merged. */
internal class WorkManagerPhotoAnalysis @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoAnalysisScheduler {
    override fun enqueue() {
        val request = OneTimeWorkRequestBuilder<AnalysePhotosWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(AnalysePhotosWorker.NAME, ExistingWorkPolicy.KEEP, request)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FoodModule {
    @Binds abstract fun photoAnalysis(impl: WorkManagerPhotoAnalysis): PhotoAnalysisScheduler
}
