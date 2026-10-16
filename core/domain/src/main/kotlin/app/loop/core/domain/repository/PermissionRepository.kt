package app.loop.core.domain.repository

import app.loop.core.model.LoopPermission
import app.loop.core.model.PermissionState
import kotlinx.coroutines.flow.StateFlow

interface PermissionRepository {
    /** Latest known status of every permission, in display order. */
    val states: StateFlow<List<PermissionState>>

    /**
     * Re-reads every status from the system. Call when the app comes back to the foreground,
     * since the user may have changed access in system settings.
     */
    fun refresh()

    fun stateOf(permission: LoopPermission): PermissionState
}
