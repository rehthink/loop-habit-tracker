package app.loop.core.data.repository

import app.loop.core.database.dao.ProfileDao
import app.loop.core.database.entity.ProfileEntity
import app.loop.core.domain.repository.ProfileRepository
import app.loop.core.model.Profile
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class ProfileRepositoryImpl @Inject constructor(
    private val dao: ProfileDao,
    private val clock: Clock,
) : ProfileRepository {
    private val createLock = Mutex()

    override val profile: Flow<Profile?> = dao.observe().map { it?.toModel() }

    override suspend fun ensureProfile(): Profile = createLock.withLock {
        dao.get()?.toModel() ?: ProfileEntity(
            id = UUID.randomUUID().toString(),
            name = null,
            createdAt = clock.millis(),
            zoneId = clock.zone.id,
            birthYear = null,
            sex = null,
            heightCm = null,
        ).also { dao.upsert(it) }.toModel()
    }

    override suspend fun updateName(name: String?) {
        val current = dao.get() ?: ensureProfile().let { dao.get() } ?: return
        dao.upsert(current.copy(name = name))
    }

    override suspend fun updateBody(birthYear: Int?, sex: Profile.Sex?, heightCm: Double?) {
        val current = dao.get() ?: ensureProfile().let { dao.get() } ?: return
        dao.upsert(current.copy(birthYear = birthYear, sex = sex?.name, heightCm = heightCm))
    }

    override suspend fun current(): Profile? = dao.get()?.toModel()

    override suspend fun updateSex(sex: Profile.Sex?) {
        val current = dao.get() ?: ensureProfile().let { dao.get() } ?: return
        dao.upsert(current.copy(sex = sex?.name))
    }
}

internal fun ProfileEntity.toModel() = Profile(
    id = id,
    name = name,
    createdAtMillis = createdAt,
    zoneId = zoneId,
    birthYear = birthYear,
    sex = sex?.let { s -> Profile.Sex.entries.firstOrNull { it.name == s } },
    heightCm = heightCm,
)
