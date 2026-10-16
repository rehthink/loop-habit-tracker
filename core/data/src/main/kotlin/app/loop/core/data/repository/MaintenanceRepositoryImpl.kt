package app.loop.core.data.repository

import app.loop.core.database.dao.RawEventDao
import app.loop.core.domain.repository.MaintenanceRepository
import javax.inject.Inject

internal class MaintenanceRepositoryImpl @Inject constructor(
    private val rawEvents: RawEventDao,
) : MaintenanceRepository {
    override suspend fun deleteRawEventsBefore(cutoffMillis: Long): Int = rawEvents.deleteOlderThan(cutoffMillis)
}
