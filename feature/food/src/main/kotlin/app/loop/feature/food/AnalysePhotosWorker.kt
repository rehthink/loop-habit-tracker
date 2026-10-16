package app.loop.feature.food

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.loop.core.domain.repository.ConfirmationNotifier
import app.loop.core.domain.repository.FoodRecognizer
import app.loop.core.domain.repository.MealRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** "Analyse later": runs queued meal photos through Gemini once online, then says they're ready. */
@HiltWorker
class AnalysePhotosWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val meals: MealRepository,
    private val recognizer: FoodRecognizer,
    private val notifier: ConfirmationNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var retry = false
        for (photo in meals.pendingPhotos().filter { it.resultJson == null }) {
            val jpeg = PhotoStore.read(photo.path)
            if (jpeg == null) {
                meals.removePendingPhoto(photo.id)
                continue
            }
            when (val result = recognizer.recognize(jpeg)) {
                is FoodRecognizer.Result.Success -> {
                    val stored = result.items.map(StoredItem::from)
                    meals.setPhotoResult(photo.id, Json.encodeToString(ListSerializer(StoredItem.serializer()), stored))
                    notifier.mealPhotoReady(photo.id)
                }
                FoodRecognizer.Result.Offline, FoodRecognizer.Result.RateLimited -> retry = true
                // No key or a rejected key: nothing to retry until the user fixes it.
                else -> Unit
            }
        }
        return if (retry && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        const val NAME = "analyse-meal-photos"
        private const val MAX_ATTEMPTS = 8
    }
}
