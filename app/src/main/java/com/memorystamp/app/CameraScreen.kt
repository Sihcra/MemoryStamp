package com.memorystamp.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.media.MediaActionSound
import android.net.Uri
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Stamp window width as a share of the preview width. Must stay centred: [cropStamp] cuts from the centre. */
private const val WINDOW_W = 0.66f

private fun windowRect(w: Float, h: Float): Pair<Offset, Size> {
    val ww = w * WINDOW_W
    val wh = ww / STAMP_ASPECT
    return Offset((w - ww) / 2, (h - wh) / 2) to Size(ww, wh)
}

private val Glass = Color(0x55000000)

@Composable
fun CameraScreen(saveToGallery: Boolean) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    var filmIndex by remember { mutableIntStateOf(1) }
    var look by remember { mutableStateOf(Look(FILMS[1])) }
    fun pick(i: Int) {
        filmIndex = (i + FILMS.size) % FILMS.size
        look = Look(FILMS[filmIndex], look.strength)
    }
    var tune by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val flash = remember { Animatable(0f) }
    val sound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    DisposableEffect(Unit) { onDispose { sound.release() } }

    /** Shared tail for camera shots and gallery picks: develop, cut, save. */
    suspend fun keep(photo: Bitmap, shotLook: Look) {
        val stamp = withContext(Dispatchers.Default) { makeStamp(shotLook.develop(photo)) }
        withContext(Dispatchers.IO) {
            Stamps.add(stamp, shotLook.film.name).also { if (saveToGallery) Stamps.copyToGallery(context, it) }
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            runCatching {
                val bmp = withContext(Dispatchers.IO) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val longest = maxOf(info.size.width, info.size.height)
                        if (longest > 3000) d.setTargetSampleSize(longest / 2000)
                    }
                }
                keep(withContext(Dispatchers.Default) { bmp.cropStamp() }, look)
                Toast.makeText(context, "Đã thêm tem vào Nhật ký", Toast.LENGTH_SHORT).show()
            }.onFailure { Toast.makeText(context, "Không mở được ảnh", Toast.LENGTH_SHORT).show() }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0E0E0E))) {
        Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(30.dp)).background(Color.Black)) {
            if (granted) {
                Viewfinder(
                    look = look,
                    busy = busy,
                    flashAnim = flash.value,
                    onSwipe = { pick(filmIndex + it) },
                    onGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) { capture, front, view ->
                    busy = true
                    val shotLook = look
                    scope.launch {
                        runCatching {
                            val shot = capture.take(context)
                            sound.play(MediaActionSound.SHUTTER_CLICK)
                            launch { flash.snapTo(0.85f); flash.animateTo(0f, tween(380)) }
                            val (_, win) = windowRect(view.width.toFloat(), view.height.toFloat())
                            val photo = withContext(Dispatchers.Default) {
                                shot.orient(front).cropStamp(view.width.toFloat(), view.height.toFloat(), win.width)
                            }
                            keep(photo, shotLook)
                        }.onFailure { Toast.makeText(context, "Chụp lỗi: ${it.message}", Toast.LENGTH_SHORT).show() }
                        busy = false
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Memory Stamp cần quyền camera để chụp tem.", color = Color.White, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.size(16.dp))
                    DarkPill("Cho phép camera", true) { ask.launch(Manifest.permission.CAMERA) }
                }
            }
        }

        // Film strip under the viewfinder.
        val listState = rememberLazyListState()
        LaunchedEffect(filmIndex) { listState.animateScrollToItem(maxOf(0, filmIndex)) }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp)) {
            item { DarkPill(if (tune) "Xong" else "Chỉnh", tune) { tune = !tune } }
            itemsIndexed(FILMS) { i, f -> DarkPill(f.name, i == filmIndex) { pick(i) } }
        }
        if (tune) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Slider(
                    value = look.strength,
                    onValueChange = { look = look.copy(strength = it) },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color(0xFF3A3A3A)),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    DarkPill("Hạt", look.grain) { look = look.copy(grain = !look.grain) }
                    DarkPill("Viền tối", look.vignette) { look = look.copy(vignette = !look.vignette) }
                    DarkPill("Lóa sáng", look.leak) { look = look.copy(leak = !look.leak) }
                    DarkPill("Ngày", look.date) { look = look.copy(date = !look.date) }
                }
            }
        }
    }
}

@Composable
private fun DarkPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.Black else Color(0xFFE8E6E1),
        modifier = Modifier.clip(CircleShape).background(if (selected) Color.White else Color(0xFF262626))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun Viewfinder(
    look: Look,
    busy: Boolean,
    flashAnim: Float,
    onSwipe: (Int) -> Unit,
    onGallery: () -> Unit,
    onShoot: (ImageCapture, Boolean, IntSize) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var front by remember { mutableStateOf(false) }
    var flashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView mode so the colour filter on the view's layer also tints the camera image.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    capture.flashMode = if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF

    fun setZoom(z: Float) {
        val state = camera?.cameraInfo?.zoomState?.value ?: return
        zoom = z.coerceIn(state.minZoomRatio, minOf(state.maxZoomRatio, 10f))
        camera?.cameraControl?.setZoomRatio(zoom)
    }

    DisposableEffect(front) {
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get().apply {
                unbindAll()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                runCatching {
                    camera = bindToLifecycle(lifecycleOwner, selector, preview, capture)
                    zoom = 1f
                }.onFailure { Toast.makeText(context, "Không mở được camera", Toast.LENGTH_SHORT).show() }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    previewView.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = look.toColorFilter() })

    Box(
        Modifier.fillMaxSize().onSizeChanged { viewSize = it }
            .pointerInput(Unit) {
                // Two fingers zoom the camera; a one-finger swipe changes film.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var zoomed = false
                    do {
                        val e = awaitPointerEvent()
                        if (e.changes.size > 1) {
                            zoomed = true
                            val z = e.calculateZoom()
                            if (z != 1f) setZoom(zoom * z)
                            e.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            dx += e.calculatePan().x
                        }
                    } while (e.changes.any { it.pressed })
                    if (!zoomed && dx < -90) onSwipe(1) else if (!zoomed && dx > 90) onSwipe(-1)
                }
            },
    ) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        // Dim outside the stamp window, draw the film extras inside it, and outline it as a perforated stamp.
        Canvas(Modifier.fillMaxSize()) {
            val (o, s) = windowRect(size.width, size.height)
            val window = Path().apply { addRoundRect(RoundRect(o.x, o.y, o.x + s.width, o.y + s.height, CornerRadius(4.dp.toPx()))) }
            clipPath(window, ClipOp.Difference) { drawRect(Color(0x8C000000)) }
            translate(o.x, o.y) { drawIntoCanvas { look.drawExtras(it.nativeCanvas, s.width, s.height) } }
            val pad = 10.dp.toPx()
            val outer = Size(s.width + pad * 2, s.height + pad * 2)
            val tl = Offset(o.x - pad, o.y - pad)
            drawRoundRect(Color.White, tl, outer, CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            val r = 3.dp.toPx()
            val step = 12.dp.toPx()
            val nx = (outer.width / step).toInt()
            val ny = (outer.height / step).toInt()
            for (i in 1 until nx) {
                val x = tl.x + i * outer.width / nx
                drawCircle(Color.White, r, Offset(x, tl.y))
                drawCircle(Color.White, r, Offset(x, tl.y + outer.height))
            }
            for (i in 1 until ny) {
                val y = tl.y + i * outer.height / ny
                drawCircle(Color.White, r, Offset(tl.x, y))
                drawCircle(Color.White, r, Offset(tl.x + outer.width, y))
            }
            drawRect(Color.White.copy(alpha = flashAnim))
        }

        Text(
            look.film.name,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 26.dp).background(Glass, CircleShape).padding(horizontal = 14.dp, vertical = 5.dp),
        )
        Bubble(
            if (flashOn) R.drawable.ic_flash else R.drawable.ic_flash_off,
            if (flashOn) "Tắt đèn flash" else "Bật đèn flash",
            Modifier.align(Alignment.TopStart).padding(16.dp), color = Glass, tint = Color.White, size = 50.dp,
        ) { flashOn = !flashOn }
        Bubble(R.drawable.ic_flip, "Đổi camera", Modifier.align(Alignment.TopEnd).padding(16.dp), color = Glass, tint = Color.White, size = 50.dp) { front = !front }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Bubble(R.drawable.ic_gallery, "Chọn ảnh có sẵn", color = Glass, tint = Color.White, size = 52.dp, onClick = onGallery)
            Box(
                Modifier.size(80.dp).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                    .background(if (busy) Color(0xFF9A9A9A) else Color.White)
                    .clickable(enabled = !busy && viewSize != IntSize.Zero, onClickLabel = "Chụp tem") { onShoot(capture, front, viewSize) },
            )
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Glass)
                    .clickable(onClickLabel = "Đổi mức zoom") { setZoom(if (zoom < 1.5f) 2f else if (zoom < 2.5f) 3f else 1f) },
                contentAlignment = Alignment.Center,
            ) {
                Text("%.1f×".format(zoom).replace(".0×", "×"), style = MaterialTheme.typography.labelLarge, color = Color.White)
            }
        }
    }
}

private class Shot(val bitmap: Bitmap, val rotation: Int)

private suspend fun ImageCapture.take(context: Context): Shot = suspendCancellableCoroutine { cont ->
    takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val shot = Shot(image.toBitmap(), image.imageInfo.rotationDegrees)
            image.close()
            cont.resume(shot)
        }

        override fun onError(e: ImageCaptureException) = cont.resumeWithException(e)
    })
}

/** Rotates the sensor image upright; mirrors the selfie camera to match what the preview showed. */
private fun Shot.orient(front: Boolean): Bitmap {
    val m = Matrix().apply {
        postRotate(rotation.toFloat())
        if (front) postScale(-1f, 1f)
    }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
}
