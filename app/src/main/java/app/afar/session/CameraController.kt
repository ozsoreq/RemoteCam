package app.afar.session

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import app.afar.net.Lens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.atan

/**
 * Owns the CameraX pipeline on the Camera phone:
 *  - Preview → the on-phone viewfinder
 *  - ImageAnalysis (≈640×480 RGBA) → frames for the Remote's live view
 *  - ImageCapture (full sensor resolution) → the real photo
 * plus lens switching (ultrawide / main / front) and tap-to-focus.
 */
class CameraController(private val context: Context) {

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val captureExecutor = Executors.newSingleThreadExecutor()

    private var provider: ProcessCameraProvider? = null
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var camera: Camera? = null

    private var preview: Preview? = null
    private var capture: ImageCapture? = null
    private var analysis: ImageAnalysis? = null

    /** Called on a background thread for every analysis frame; the image is closed afterwards. */
    @Volatile var frameSink: ((ImageProxy) -> Unit)? = null

    @Volatile private var analysisRotation = 0

    /** True while the Camera screen is stopped (incoming call, app switched away). */
    @Volatile var paused = false

    private val _lens = MutableStateFlow(Lens.Main)
    val lens: StateFlow<Lens> = _lens.asStateFlow()

    private val _lenses = MutableStateFlow(listOf(Lens.Main))
    val lenses: StateFlow<List<Lens>> = _lenses.asStateFlow()

    private val _bound = MutableStateFlow(false)
    val bound: StateFlow<Boolean> = _bound.asStateFlow()

    /** Separate ultrawide camera id, when the phone exposes one as its own camera. */
    private var wideCameraId: String? = null

    /** Logical back camera can zoom below 1× (built-in ultrawide). */
    private var mainMinZoom = 1f

    private var targetRotation = Surface.ROTATION_0

    fun attach(owner: LifecycleOwner, view: PreviewView, preferred: Lens) {
        this.owner = owner
        this.previewView = view
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val p = runCatching { future.get() }.getOrNull() ?: return@addListener
            provider = p
            discoverLenses(p)
            if (preferred == Lens.Front && Lens.Front in _lenses.value) {
                bind(Lens.Front)
            } else {
                // Bind the main camera first: its zoom range tells us whether ultrawide is
                // reachable by zooming below 1× (smoothest) or needs a separate camera.
                bind(Lens.Main)
                if (preferred == Lens.Ultrawide) setLens(Lens.Ultrawide)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun detach() {
        provider?.unbindAll()
        camera = null
        owner = null
        previewView = null
        _bound.value = false
    }

    fun setLens(lens: Lens) {
        if (lens !in _lenses.value) return
        ContextCompat.getMainExecutor(context).execute {
            if (lens == _lens.value && camera != null) return@execute
            val current = _lens.value
            val zoomOnly = !useWideCamera && camera != null &&
                current != Lens.Front && lens != Lens.Front
            if (zoomOnly) {
                camera?.cameraControl?.setZoomRatio(if (lens == Lens.Ultrawide) wideZoom else 1f)
                _lens.value = lens
            } else {
                bind(lens)
            }
        }
    }

    private val useWideCamera: Boolean get() = mainMinZoom >= 1f && wideCameraId != null
    private val wideZoom: Float get() = mainMinZoom.coerceAtLeast(0.5f)

    /** Keep photos and preview frames upright however the Camera phone is propped. */
    fun setDeviceRotation(degrees: Int) {
        val r = when (degrees) {
            90 -> Surface.ROTATION_90
            180 -> Surface.ROTATION_180
            270 -> Surface.ROTATION_270
            else -> Surface.ROTATION_0
        }
        if (r == targetRotation) return
        targetRotation = r
        capture?.targetRotation = r
        analysis?.targetRotation = r
    }

    /** Tap-to-focus/expose from the Remote. (x, y) are 0…1 in the upright streamed frame. */
    fun focusUpright(x: Float, y: Float) {
        val cam = camera ?: return
        val useCase = analysis ?: return
        val (bx, by) = when (analysisRotation) {
            90 -> y to 1 - x
            180 -> 1 - x to 1 - y
            270 -> 1 - y to x
            else -> x to y
        }
        val point = SurfaceOrientedMeteringPointFactory(1f, 1f, useCase).createPoint(bx, by)
        runCatching {
            cam.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                    .setAutoCancelDuration(6, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }

    /** Tap-to-focus on the Camera phone's own viewfinder. */
    fun focusOnViewfinder(x: Float, y: Float) {
        val cam = camera ?: return
        val view = previewView ?: return
        val point = view.meteringPointFactory.createPoint(x, y)
        runCatching {
            cam.cameraControl.startFocusAndMetering(
                FocusMeteringAction.Builder(point).setAutoCancelDuration(6, TimeUnit.SECONDS).build(),
            )
        }
    }

    suspend fun takePicture(file: File): Boolean {
        val ic = capture ?: return false
        if (!_bound.value || paused) return false
        return suspendCancellableCoroutine { cont ->
            ic.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                captureExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.w(TAG, "capture failed", exception)
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
        }
    }

    private fun bind(lens: Lens) {
        val p = provider ?: return
        val lifecycleOwner = owner ?: return
        val view = previewView ?: return

        val selector = when (lens) {
            Lens.Front -> CameraSelector.DEFAULT_FRONT_CAMERA
            Lens.Ultrawide -> wideCameraId?.takeIf { useWideCamera }?.let { id ->
                CameraSelector.Builder().addCameraFilter { infos -> infos.filter { cameraId(it) == id } }.build()
            } ?: CameraSelector.DEFAULT_BACK_CAMERA
            Lens.Main -> CameraSelector.DEFAULT_BACK_CAMERA
        }

        val ratio = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)

        val newPreview = Preview.Builder().setResolutionSelector(ratio.build()).build().also {
            it.setSurfaceProvider(view.surfaceProvider)
        }
        val newCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                    .build(),
            )
            .setTargetRotation(targetRotation)
            .build()
        val newAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                    )
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setTargetRotation(targetRotation)
            .build()
            .also { a ->
                a.setAnalyzer(analysisExecutor) { image ->
                    analysisRotation = image.imageInfo.rotationDegrees
                    try {
                        frameSink?.invoke(image)
                    } catch (t: Throwable) {
                        Log.w(TAG, "frame sink failed", t)
                    } finally {
                        image.close()
                    }
                }
            }

        p.unbindAll()
        val cam = runCatching {
            p.bindToLifecycle(lifecycleOwner, selector, newPreview, newCapture, newAnalysis)
        }.recoverCatching {
            // Some LEGACY devices can't run three streams; the Remote's view matters more
            // than the on-phone viewfinder, so drop Preview first.
            Log.w(TAG, "3-stream bind failed, retrying without preview", it)
            p.unbindAll()
            p.bindToLifecycle(lifecycleOwner, selector, newCapture, newAnalysis)
        }.getOrElse {
            Log.e(TAG, "camera bind failed", it)
            _bound.value = false
            return
        }

        camera = cam
        preview = newPreview
        capture = newCapture
        analysis = newAnalysis
        _bound.value = true

        if (selector == CameraSelector.DEFAULT_BACK_CAMERA) {
            if (lens == Lens.Main) {
                mainMinZoom = cam.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                if (mainMinZoom < 1f && Lens.Ultrawide !in _lenses.value) {
                    _lenses.value = listOf(Lens.Ultrawide) + _lenses.value
                }
            }
            cam.cameraControl.setZoomRatio(if (lens == Lens.Ultrawide && mainMinZoom < 1f) wideZoom else 1f)
        }
        _lens.value = if (lens == Lens.Ultrawide && mainMinZoom >= 1f && wideCameraId == null) Lens.Main else lens
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun discoverLenses(p: ProcessCameraProvider) {
        val options = mutableListOf<Lens>()
        val hasBack = runCatching { p.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
        val hasFront = runCatching { p.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)

        if (hasBack) {
            val backs = p.availableCameraInfos.filter { lensFacing(it) == CameraCharacteristics.LENS_FACING_BACK }
            val fovs = backs.associateWith { fov(it) }
            val mainFov = fovs.values.firstOrNull() ?: 0.0
            wideCameraId = fovs.filter { it.value > mainFov * 1.25 }.maxByOrNull { it.value }?.key?.let { cameraId(it) }
            if (wideCameraId != null) options += Lens.Ultrawide
            options += Lens.Main
        }
        if (hasFront) options += Lens.Front
        _lenses.value = options.ifEmpty { listOf(Lens.Main) }
    }

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun cameraId(info: CameraInfo): String? = runCatching { Camera2CameraInfo.from(info).cameraId }.getOrNull()

    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun lensFacing(info: CameraInfo): Int? = runCatching {
        Camera2CameraInfo.from(info).getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
    }.getOrNull()

    /** Horizontal field of view in radians, from focal length and sensor size. */
    @androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
    private fun fov(info: CameraInfo): Double = runCatching {
        val c = Camera2CameraInfo.from(info)
        val focal = c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull()
            ?: return 0.0
        val sensor = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 0.0
        2 * atan(sensor.width / (2.0 * focal))
    }.getOrDefault(0.0)

    private companion object {
        const val TAG = "CameraController"
    }
}
