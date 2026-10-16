package app.loop.feature.food

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Meal photos: shrunk so the longest side is ~1024 px and saved as JPEG in app-private storage
 * (never backed up, never logged). Small photos keep Gemini fast and light on tokens.
 */
internal object PhotoStore {
    private const val LONGEST_SIDE = 1024
    private const val QUALITY = 80
    private const val DIR = "meal_photos" // matches BackupRepositoryImpl.MEAL_PHOTO_DIR

    fun compress(source: Bitmap, rotationDegrees: Int): ByteArray {
        val scale = LONGEST_SIDE.toFloat() / maxOf(source.width, source.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
        }
        val scaled = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            if (scaled !== source) scaled.recycle()
            out.toByteArray()
        }
    }

    fun save(context: Context, jpeg: ByteArray, timestamp: Long): File {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        return File(dir, "meal_$timestamp.jpg").apply { writeBytes(jpeg) }
    }

    fun read(path: String): ByteArray? = File(path).takeIf { it.exists() }?.readBytes()

    /** A small preview for the review card. */
    fun thumbnail(path: String, targetPx: Int = 600): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
