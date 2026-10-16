package app.loop.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every exported schema version must migrate to the latest one. When you bump the version,
 * add the Migration to [LoopDatabase.MIGRATIONS]; this test then checks every path.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LoopDatabase::class.java,
    )

    @Test
    fun everyVersionMigratesToLatest() {
        for (start in 1 until LoopDatabase.VERSION) {
            helper.createDatabase(TEST_DB, start).close()
            helper.runMigrationsAndValidate(TEST_DB, LoopDatabase.VERSION, true, *LoopDatabase.MIGRATIONS)
        }
    }

    @Test
    fun v4MovesActivityAndPlaceTagsOutOfPackageName() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL("INSERT INTO raw_event (type, packageName, value, startTime, endTime, zoneId) VALUES ('ACTIVITY', 'ENTER', 3, 1, NULL, 'UTC')")
            db.execSQL("INSERT INTO raw_event (type, packageName, value, startTime, endTime, zoneId) VALUES ('GEOFENCE_EXIT', 'GYM', NULL, 2, NULL, 'UTC')")
            db.execSQL("INSERT INTO raw_event (type, packageName, value, startTime, endTime, zoneId) VALUES ('APP_OPEN', 'com.example', NULL, 3, NULL, 'UTC')")
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, Migrations.MIGRATION_3_4)

        db.query("SELECT type, packageName, tag FROM raw_event ORDER BY startTime").use { c ->
            val rows = generateSequence { if (c.moveToNext()) Triple(c.getString(0), c.getString(1), c.getString(2)) else null }.toList()
            assertEquals(
                listOf(
                    Triple("ACTIVITY", null, "ENTER"),
                    Triple("GEOFENCE_EXIT", null, "GYM"),
                    Triple("APP_OPEN", "com.example", null),
                ),
                rows,
            )
        }
    }

    @Test
    fun latestExportedSchemaMatchesTheEntities() {
        // Fails if an entity changed without bumping the version and exporting a new schema.
        helper.createDatabase(TEST_DB, LoopDatabase.VERSION).close()
        helper.runMigrationsAndValidate(TEST_DB, LoopDatabase.VERSION, true)
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
