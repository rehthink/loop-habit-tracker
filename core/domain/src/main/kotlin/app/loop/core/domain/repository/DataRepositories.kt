package app.loop.core.domain.repository

interface BackupRepository {
    /** Versioned JSON of everything the user owns (no secrets). */
    suspend fun exportJson(): String

    /** Replaces all user data with the export. Throws [IllegalArgumentException] on an unreadable or newer file. */
    suspend fun importJson(json: String): ImportResult

    /** Wipes every table, settings, secrets and stored photos. */
    suspend fun deleteEverything()
}

data class ImportResult(val days: Int, val meals: Int, val checkIns: Int)
