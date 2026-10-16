package app.loop.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Meal photos on disk (app-private `files/meal_photos`, never backed up). Only files inside that
 * folder are ever deleted, whatever path a row holds.
 */
@Singleton
internal class MealPhotoFiles @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, DIR)

    fun delete(path: String?) {
        val file = path?.let(::File) ?: return
        if (file.isInside(dir)) file.delete()
    }

    /**
     * Deletes photos that no meal or queued photo uses once they're a day old (a review closed
     * without saving), and any photo older than [maxAgeMillis]: once a meal is logged, nothing in
     * the app shows its photo again. Returns how many were removed.
     */
    fun prune(inUse: Set<String>, queued: Set<String>, now: Long, maxAgeMillis: Long): Int {
        val files = dir.listFiles() ?: return 0
        return files.count { file ->
            val age = now - file.lastModified()
            val unused = file.absolutePath !in inUse && file.absolutePath !in queued
            val stale = (unused && age > ORPHAN_AGE_MILLIS) || (file.absolutePath !in queued && age > maxAgeMillis)
            stale && file.delete()
        }
    }

    fun deleteAll() {
        dir.deleteRecursively()
    }

    private fun File.isInside(parent: File): Boolean =
        canonicalFile.parentFile == parent.canonicalFile

    companion object {
        /** Meal photos live here. Must match PhotoStore in :feature:food. */
        const val DIR = "meal_photos"
        private const val ORPHAN_AGE_MILLIS = 24 * 60 * 60_000L
    }
}
