package app.loop.feature.food

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.loop.core.domain.repository.FoodRepository
import app.loop.core.domain.repository.PermissionRepository
import app.loop.core.model.LoopPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object FoodRoute
@Serializable data object FoodCameraRoute
@Serializable data class FoodReviewRoute(val photoPath: String? = null, val pendingId: Long = -1)
@Serializable data object BarcodeRoute
@Serializable data class NewPacketRoute(val barcode: String)

/** Key on the review screen's saved state: a food chosen on the barcode screens. */
internal const val RESULT_FOOD_ID = "result_food_id"

fun NavController.navigateToFoodCamera() = navigate(FoodCameraRoute)
fun NavController.navigateToFood() = navigate(FoodRoute)
fun NavController.navigateToPendingPhoto(id: Long) = navigate(FoodReviewRoute(pendingId = id))

fun NavGraphBuilder.foodScreens(nav: NavController, onOpenPermission: (LoopPermission) -> Unit) {
    composable<FoodRoute> {
        FoodScreen(
            onBack = { nav.popBackStack() },
            onSnap = { nav.navigate(FoodCameraRoute) },
            onSearch = { nav.navigate(FoodReviewRoute()) },
            onOpenPending = { nav.navigate(FoodReviewRoute(pendingId = it)) },
        )
    }
    composable<FoodCameraRoute> {
        val gate: CameraGateViewModel = hiltViewModel()
        val hasCamera by gate.hasCamera.collectAsStateWithLifecycle()
        LifecycleResumeEffect(Unit) {
            gate.refresh()
            onPauseOrDispose { }
        }
        MealCameraScreen(
            hasCamera = hasCamera,
            onBack = { nav.popBackStack() },
            onOpenPermission = onOpenPermission,
            onCaptured = { path -> nav.navigate(FoodReviewRoute(photoPath = path)) { popUpTo<FoodCameraRoute> { inclusive = true } } },
            onSkip = { nav.navigate(FoodReviewRoute()) { popUpTo<FoodCameraRoute> { inclusive = true } } },
        )
    }
    composable<FoodReviewRoute> { entry ->
        val chosen by entry.savedStateHandle.getStateFlow(RESULT_FOOD_ID, -1L).collectAsStateWithLifecycle()
        val viewModel: ReviewViewModel = hiltViewModel()
        LaunchedEffect(chosen) {
            if (chosen >= 0) {
                viewModel.addFoodById(chosen)
                entry.savedStateHandle[RESULT_FOOD_ID] = -1L
            }
        }
        ReviewScreen(
            viewModel = viewModel,
            onBack = { nav.popBackStack() },
            onScan = { nav.navigate(BarcodeRoute) },
            onDone = { nav.popBackStack() },
        )
    }
    composable<BarcodeRoute> {
        val gate: CameraGateViewModel = hiltViewModel()
        val hasCamera by gate.hasCamera.collectAsStateWithLifecycle()
        LifecycleResumeEffect(Unit) {
            gate.refresh()
            onPauseOrDispose { }
        }
        BarcodeScannerScreen(
            hasCamera = hasCamera,
            onBack = { nav.popBackStack() },
            onOpenPermission = onOpenPermission,
            onBarcode = { code ->
                gate.lookUp(code) { foodId ->
                    if (foodId != null) {
                        nav.previousBackStackEntry?.savedStateHandle?.set(RESULT_FOOD_ID, foodId)
                        nav.popBackStack()
                    } else {
                        nav.navigate(NewPacketRoute(code)) { popUpTo<BarcodeRoute> { inclusive = true } }
                    }
                }
            },
        )
    }
    composable<NewPacketRoute> { entry ->
        NewPacketScreen(
            barcode = entry.toRoute<NewPacketRoute>().barcode,
            onBack = { nav.popBackStack() },
            onSaved = { foodId ->
                nav.previousBackStackEntry?.savedStateHandle?.set(RESULT_FOOD_ID, foodId)
                nav.popBackStack()
            },
        )
    }
}

@HiltViewModel
class CameraGateViewModel @Inject constructor(
    private val permissions: PermissionRepository,
    private val foods: FoodRepository,
) : ViewModel() {
    val hasCamera: StateFlow<Boolean> = permissions.states
        .map { list -> list.firstOrNull { it.permission == LoopPermission.CAMERA }?.isSatisfied == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, permissions.stateOf(LoopPermission.CAMERA).isSatisfied)

    fun refresh() = permissions.refresh()

    fun lookUp(barcode: String, onResult: (Long?) -> Unit) = viewModelScope.launch {
        onResult(foods.byBarcode(barcode)?.id)
    }
}
