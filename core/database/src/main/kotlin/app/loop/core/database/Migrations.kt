package app.loop.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal object Migrations {
    /** v2: places, drinks, journal, medicine doses, portion memory, photo queue, custom foods; mood energy nullable. */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `saved_place` (`kind` TEXT NOT NULL, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `radiusMeters` REAL NOT NULL, `label` TEXT, PRIMARY KEY(`kind`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `drink_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, `kind` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_drink_log_time` ON `drink_log` (`time`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `journal_entry` (`date` TEXT NOT NULL, `text` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`date`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `medicine_dose` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `medicineId` INTEGER NOT NULL, `time` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, FOREIGN KEY(`medicineId`) REFERENCES `medicine`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_dose_medicineId` ON `medicine_dose` (`medicineId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_dose_time` ON `medicine_dose` (`time`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `portion_memory` (`dishKey` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, PRIMARY KEY(`dishKey`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `pending_photo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `path` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `resultJson` TEXT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `custom_food` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `aliases` TEXT NOT NULL, `defaultUnit` TEXT NOT NULL, `kcalPerUnit` REAL NOT NULL, `gramsPerUnit` REAL NOT NULL, `protein` REAL NOT NULL, `carbs` REAL NOT NULL, `fat` REAL NOT NULL, `barcode` TEXT)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_food_barcode` ON `custom_food` (`barcode`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `mood_check_in_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL, `zoneId` TEXT NOT NULL, `slot` TEXT NOT NULL, `mood` INTEGER NOT NULL, `energy` INTEGER, `note` TEXT)")
            db.execSQL("INSERT INTO `mood_check_in_new` (`id`,`time`,`zoneId`,`slot`,`mood`,`energy`,`note`) SELECT `id`,`time`,`zoneId`,`slot`,`mood`,`energy`,`note` FROM `mood_check_in`")
            db.execSQL("DROP TABLE `mood_check_in`")
            db.execSQL("ALTER TABLE `mood_check_in_new` RENAME TO `mood_check_in`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_mood_check_in_time` ON `mood_check_in` (`time`)")
            db.execSQL("ALTER TABLE `notification_stat` ADD COLUMN `askedToDisable` INTEGER NOT NULL DEFAULT 0")
        }
    }

    /** v3: the period tracker's day logs. */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `cycle_day` (`date` TEXT NOT NULL, `flow` TEXT, `symptoms` TEXT NOT NULL, `note` TEXT, PRIMARY KEY(`date`))")
        }
    }
}
