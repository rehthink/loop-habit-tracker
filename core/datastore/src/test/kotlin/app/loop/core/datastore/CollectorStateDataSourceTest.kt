package app.loop.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CollectorStateDataSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.settingsStore() = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(tmp.root, "settings.preferences_pb") },
    )

    @Test
    fun `old state entries move out of the settings file, per-day ones are dropped`() = runTest(UnconfinedTestDispatcher()) {
        val settings = settingsStore()
        settings.edit {
            it[longPreferencesKey("state_screentime_cursor")] = 42L
            it[longPreferencesKey("state_med_3_20500_28800")] = 1L
            it[longPreferencesKey("state_rings_celebrated_20500")] = 2L
            it[booleanPreferencesKey("app_lock")] = true
        }
        val state = PreferenceDataStoreFactory.create(
            migrations = listOf(MoveCollectorStateOutOfSettings(settings)),
            scope = backgroundScope,
            produceFile = { File(tmp.root, "state.preferences_pb") },
        )
        val source = CollectorStateDataSource(state)

        assertEquals(42L, source.getLong("screentime_cursor"))
        assertNull(source.getLong("med_3_20500_28800"))
        assertNull(source.getLong("rings_celebrated_20500"))

        val left = settings.data.first().asMap().keys.map { it.name }
        assertFalse(left.any { it.startsWith("state_") })
        assertTrue("app_lock" in left)
    }

    @Test
    fun `values round-trip and clear`() = runTest(UnconfinedTestDispatcher()) {
        val source = CollectorStateDataSource(
            PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "s.preferences_pb") }),
        )
        source.setLong("a", 7)
        assertEquals(7L, source.getLong("a"))

        source.clear()
        assertNull(source.getLong("a"))
    }
}
