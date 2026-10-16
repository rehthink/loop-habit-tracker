package app.loop.core.domain.repository

interface MaintenanceRepository {
    /** Deletes raw events that started before [cutoffMillis]. Daily summaries are never deleted. Returns rows removed. */
    suspend fun deleteRawEventsBefore(cutoffMillis: Long): Int

    /**
     * Deletes meal photos nothing uses any more (after a day) and photos older than [maxAgeMillis].
     * Photos still queued for analysis are kept. Returns files removed.
     */
    suspend fun pruneMealPhotos(now: Long, maxAgeMillis: Long): Int

    /** Folds recent writes into the main database file, which is what Auto Backup copies. */
    suspend fun checkpointDatabase()
}
