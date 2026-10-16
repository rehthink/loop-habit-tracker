package app.loop.core.data.repository

import app.loop.core.database.LoopDatabase
import app.loop.core.database.dao.MealDao
import app.loop.core.database.dao.RawEventDao
import app.loop.core.domain.Dispatcher
import app.loop.core.domain.LoopDispatcher
import app.loop.core.domain.repository.MaintenanceRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class MaintenanceRepositoryImpl @Inject constructor(
    private val db: LoopDatabase,
    private val rawEvents: RawEventDao,
    private val meals: MealDao,
    private val photos: MealPhotoFiles,
    @Dispatcher(LoopDispatcher.IO) private val io: CoroutineDispatcher,
) : MaintenanceRepository {
    override suspend fun deleteRawEventsBefore(cutoffMillis: Long): Int = rawEvents.deleteOlderThan(cutoffMillis)

    override suspend fun pruneMealPhotos(now: Long, maxAgeMillis: Long): Int = withContext(io) {
        val inUse = meals.allPhotoUris().toSet()
        val queued = meals.pending().map { it.path }.toSet()
        photos.prune(inUse, queued, now, maxAgeMillis)
    }

    override suspend fun checkpointDatabase() = withContext(io) {
        // Moves the write-ahead log into loop.db, so Auto Backup (which copies loop.db alone)
        // carries recent entries rather than whatever was last checkpointed.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        Unit
    }
}
