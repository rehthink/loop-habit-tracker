package app.loop.core.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** The DataStore for collector bookkeeping, kept apart from the user's settings. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CollectorStateStore

/**
 * Collector cursors, "already asked today" markers and registration signatures.
 *
 * These change on every tick, so they live in their own file: writing them used to rewrite the
 * settings file and wake every screen watching settings. Not backed up, since none of it means
 * anything on another phone.
 */
@Singleton
class CollectorStateDataSource @Inject constructor(
    @CollectorStateStore private val dataStore: DataStore<Preferences>,
) {
    suspend fun getLong(key: String): Long? = dataStore.data.first()[longPreferencesKey(key)]

    suspend fun setLong(key: String, value: Long) {
        dataStore.edit { it[longPreferencesKey(key)] = value }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}

/**
 * One-time move of the `state_*` entries that older versions kept in the settings file. Entries
 * named per day (one key per medicine dose or celebrated day, never cleaned up) are dropped: the
 * code now keeps one entry per thing instead.
 */
internal class MoveCollectorStateOutOfSettings(
    private val settings: DataStore<Preferences>,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean = currentData[MOVED] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val moved = currentData.toMutablePreferences()
        settings.data.first().asMap().forEach { (key, value) ->
            val name = key.name.removePrefix(PREFIX)
            if (key.name.startsWith(PREFIX) && value is Long && !isPerDay(name)) moved[longPreferencesKey(name)] = value
        }
        moved[MOVED] = true
        return moved.toPreferences()
    }

    override suspend fun cleanUp() {
        settings.edit { prefs ->
            prefs.asMap().keys.filter { it.name.startsWith(PREFIX) }.forEach { prefs.remove(it) }
        }
    }

    internal companion object {
        const val PREFIX = "state_"
        val MOVED = booleanPreferencesKey("moved_from_settings")
        private val PER_DAY = listOf(Regex("""med_\d+_\d+_\d+"""), Regex("""rings_celebrated_\d+"""))

        fun isPerDay(name: String) = PER_DAY.any { it.matches(name) }
    }
}
