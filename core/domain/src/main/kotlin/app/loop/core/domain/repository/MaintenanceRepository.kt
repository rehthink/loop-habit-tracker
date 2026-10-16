package app.loop.core.domain.repository

interface MaintenanceRepository {
    /** Deletes raw events that started before [cutoffMillis]. Daily summaries are never deleted. Returns rows removed. */
    suspend fun deleteRawEventsBefore(cutoffMillis: Long): Int
}
