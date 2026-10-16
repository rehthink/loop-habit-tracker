package app.loop.feature.food

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.loop.core.designsystem.component.LoopTopBar
import app.loop.core.designsystem.component.QuietButton
import app.loop.core.designsystem.icon.LoopIcons
import app.loop.core.model.LoopPermission
import app.loop.core.ui.permission.PermissionGateCard
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private suspend fun cameraProvider(context: android.content.Context): ProcessCameraProvider =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ runCatching { future.get() }.onSuccess { cont.resumeWith(Result.success(it)) }.onFailure { cont.resumeWith(Result.failure(it)) } }, ContextCompat.getMainExecutor(context))
    }

/** Live camera preview bound to this screen's lifecycle, with optional capture and analysis use cases. */
@Composable
private fun CameraPreview(
    modifier: Modifier,
    imageCapture: ImageCapture?,
    analysis: ImageAnalysis?,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    LaunchedEffect(lifecycleOwner) {
        val provider = cameraProvider(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        provider.unbindAll()
        val useCases = listOfNotNull(preview, imageCapture, analysis).toTypedArray()
        runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, *useCases) }
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
internal fun MealCameraScreen(
    hasCamera: Boolean,
    onBack: () -> Unit,
    onOpenPermission: (LoopPermission) -> Unit,
    onCaptured: (String) -> Unit,
    onSkip: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            LoopTopBar(stringResource(R.string.cam_title), onBack = onBack)
        }
        if (!hasCamera) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PermissionGateCard(LoopPermission.CAMERA, stringResource(R.string.cam_gate), onOpenPermission)
                QuietButton(stringResource(R.string.cam_skip), onClick = onSkip)
            }
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraPreview(Modifier.fillMaxSize(), capture, null)
        }
        Column(
            Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (failed) Text(stringResource(R.string.cam_failed), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            IconButton(
                onClick = {
                    if (busy) return@IconButton
                    busy = true
                    failed = false
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                scope.launch {
                                    val path = withContext(Dispatchers.Default) {
                                        val rotation = image.imageInfo.rotationDegrees
                                        val bitmap = image.toBitmap()
                                        image.close()
                                        val jpeg = PhotoStore.compress(bitmap, rotation)
                                        PhotoStore.save(context, jpeg, System.currentTimeMillis()).absolutePath
                                    }
                                    busy = false
                                    onCaptured(path)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                busy = false
                                failed = true
                            }
                        },
                    )
                },
                modifier = Modifier.size(76.dp).border(4.dp, Color.White, CircleShape).padding(8.dp).background(Color.White, CircleShape),
            ) {
                Icon(LoopIcons.Camera, contentDescription = stringResource(R.string.cam_capture), tint = Color.Black)
            }
            QuietButton(stringResource(R.string.cam_skip), onClick = onSkip)
        }
    }
}

/** Scans EAN/UPC barcodes on packets with ML Kit (on-device). Calls [onBarcode] once. */
@SuppressLint("UnsafeOptInUsageError")
@Composable
internal fun BarcodeScannerScreen(
    hasCamera: Boolean,
    onBack: () -> Unit,
    onOpenPermission: (LoopPermission) -> Unit,
    onBarcode: (String) -> Unit,
) {
    val executor = remember { Executors.newSingleThreadExecutor() }
    var done by remember { mutableStateOf(false) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build(),
        )
    }
    val analysis = remember {
        ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also { a ->
            a.setAnalyzer(executor) { proxy ->
                val media = proxy.image
                if (media == null || done) {
                    proxy.close()
                    return@setAnalyzer
                }
                scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                    .addOnSuccessListener { codes ->
                        val value = codes.firstNotNullOfOrNull { it.rawValue }
                        if (value != null && !done) {
                            done = true
                            onBarcode(value)
                        }
                    }
                    .addOnCompleteListener { proxy.close() }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }
    Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            LoopTopBar(stringResource(R.string.bc_title), onBack = onBack)
        }
        if (!hasCamera) {
            PermissionGateCard(LoopPermission.CAMERA, stringResource(R.string.cam_gate), onOpenPermission, Modifier.padding(16.dp))
            return@Column
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            CameraPreview(Modifier.fillMaxSize(), null, analysis)
            Box(Modifier.align(Alignment.Center).size(260.dp, 140.dp).border(3.dp, Color.White, MaterialTheme.shapes.medium))
        }
        Text(
            stringResource(R.string.bc_hint),
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(20.dp).align(Alignment.CenterHorizontally),
        )
    }
}
