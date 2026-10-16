package app.loop.core.domain.repository

import java.io.InputStream
import java.io.OutputStream

interface BackupRepository {
    /** Writes versioned JSON of everything the user owns (no secrets) to [out], streamed rather than built in memory. */
    suspend fun exportTo(out: OutputStream)

    /** Replaces all user data with the export in [input]. Throws [IllegalArgumentException] on an unreadable or newer file. */
    suspend fun importFrom(input: InputStream): ImportResult

    /** Wipes every table, settings, secrets and stored photos. */
    suspend fun deleteEverything()
}

data class ImportResult(val days: Int, val meals: Int, val checkIns: Int)
