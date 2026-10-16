package app.loop.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.cash.turbine.test
import app.loop.core.model.ThemeMode
import app.loop.core.model.TrackerId
import app.loop.core.model.UserSettings
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesDataSourceTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun TestScope.dataStore() = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(tmp.root, "test.preferences_pb") },
    )

    @Test
    fun `fresh install gives the defaults`() = runTest(UnconfinedTestDispatcher()) {
        val source = UserPreferencesDataSource(dataStore())

        assertEquals(UserSettings(), source.settings.first())
    }

    @Test
    fun `writes are reflected in the settings flow`() = runTest(UnconfinedTestDispatcher()) {
        val source = UserPreferencesDataSource(dataStore())

        source.settings.test {
            assertFalse(awaitItem().onboardingComplete)

            source.setOnboardingComplete(true)
            assertTrue(awaitItem().onboardingComplete)

            source.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem().themeMode)

            source.setTrackerEnabled(TrackerId.MUSIC, enabled = false)
            assertEquals(setOf(TrackerId.MUSIC), awaitItem().disabledTrackers)

            source.setTrackerEnabled(TrackerId.MUSIC, enabled = true)
            assertEquals(emptySet<TrackerId>(), awaitItem().disabledTrackers)
        }
    }

    @Test
    fun `unknown or corrupt stored values fall back to defaults`() = runTest(UnconfinedTestDispatcher()) {
        val store = dataStore()
        store.edit {
            it[stringPreferencesKey("theme_mode")] = "PURPLE"
            it[stringSetPreferencesKey("disabled_trackers")] = setOf("SLEEP", "TELEPATHY")
            it[stringPreferencesKey("day_boundary")] = "not a time"
        }

        val settings = UserPreferencesDataSource(store).settings.first()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(setOf(TrackerId.SLEEP), settings.disabledTrackers)
        assertEquals(UserSettings.DEFAULT_DAY_BOUNDARY, settings.dayBoundary)
    }
}
