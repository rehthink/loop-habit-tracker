package app.loop.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    fun latestExportedSchemaMatchesTheEntities() {
        // Fails if an entity changed without bumping the version and exporting a new schema.
        helper.createDatabase(TEST_DB, LoopDatabase.VERSION).close()
        helper.runMigrationsAndValidate(TEST_DB, LoopDatabase.VERSION, true)
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
