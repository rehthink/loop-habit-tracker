package app.loop.core.data.repository

import app.loop.core.data.permission.AndroidPermissionChecker
import app.loop.core.domain.ApplicationScope
import app.loop.core.domain.repository.HealthConnectGateway
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.model.LoopPermission
import app.loop.core.model.PermissionState
import app.loop.core.model.PermissionStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
internal class PermissionRepositoryImpl @Inject constructor(
    private val checker: AndroidPermissionChecker,
    private val healthConnect: HealthConnectGateway,
    @ApplicationScope private val scope: CoroutineScope,
) : PermissionRepository {
    // Health Connect can only be asked asynchronously; this holds its last known answer.
    @Volatile private var healthStatus: PermissionStatus = PermissionStatus.UNAVAILABLE

    private val _states = MutableStateFlow(readAll())
    override val states: StateFlow<List<PermissionState>> = _states.asStateFlow()

    init {
        refresh()
    }

    override fun refresh() {
        _states.value = readAll()
        scope.launch {
            healthStatus = when (healthConnect.availability()) {
                HealthConnectGateway.Availability.AVAILABLE ->
                    if (runCatching { healthConnect.hasAllPermissions() }.getOrDefault(false)) PermissionStatus.GRANTED else PermissionStatus.DENIED
                else -> PermissionStatus.UNAVAILABLE
            }
            _states.value = readAll()
        }
    }

    override fun stateOf(permission: LoopPermission): PermissionState = PermissionState(
        permission,
        if (permission == LoopPermission.HEALTH_CONNECT) healthStatus else checker.statusOf(permission),
    )

    private fun readAll() = LoopPermission.entries.map(::stateOf)
}
