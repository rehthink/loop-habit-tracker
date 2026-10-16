package app.loop.feature.permissions

import androidx.lifecycle.ViewModel
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.model.PermissionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class PermissionsViewModel @Inject constructor(
    private val repository: PermissionRepository,
) : ViewModel() {
    val states: StateFlow<List<PermissionState>> = repository.states

    /** Called on every resume: the user may have just changed access in system settings. */
    fun refresh() = repository.refresh()
}
