package app.loop.core.database

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalTime

internal class Converters {
    @TypeConverter
    fun localDateToText(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun textToLocalDate(text: String?): LocalDate? = text?.let(LocalDate::parse)

    /** Medicine times, e.g. "08:00,20:30". */
    @TypeConverter
    fun timesToText(times: List<LocalTime>?): String? = times?.joinToString(",")

    @TypeConverter
    fun textToTimes(text: String?): List<LocalTime>? =
        text?.split(',')?.filter { it.isNotBlank() }?.map(LocalTime::parse)
}
