package com.memorystamp.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.view.View
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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

@Composable
fun CameraScreen(saveToGallery: Boolean) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Memory Stamp cần quyền camera để chụp tem.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.size(16.dp))
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Cho phép camera") }
        }
        return
    }

    var filmIndex by remember { mutableIntStateOf(1) }
    var look by remember { mutableStateOf(Look(FILMS[1])) }
    fun pick(i: Int) {
        filmIndex = (i + FILMS.size) % FILMS.size
        look = Look(FILMS[filmIndex], look.strength)
    }
    var front by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var lastStamp by remember { mutableStateOf<Bitmap?>(null) }
    var lastId by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView mode so the colour filter on the view's layer also tints the camera image.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val capture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }

    DisposableEffect(front) {
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            provider = future.get().apply {
                unbindAll()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                runCatching { bindToLifecycle(lifecycleOwner, selector, preview, capture) }
                    .onFailure { Toast.makeText(context, "Không mở được camera", Toast.LENGTH_SHORT).show() }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose { provider?.unbindAll() }
    }
    previewView.setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = look.toColorFilter() })

    val flash = remember { Animatable(0f) }
    val press = remember { Animatable(1f) }
    fun shoot() {
        if (busy) return
        busy = true
        val shotLook = look
        scope.launch {
            launch { press.animateTo(0.96f, tween(70)); press.animateTo(1f, spring(dampingRatio = 0.4f)) }
            launch { flash.snapTo(0.9f); flash.animateTo(0f, tween(450)) }
            runCatching {
                val photo = capture.take(context)
                val stamp = withContext(Dispatchers.Default) {
                    makeStamp(shotLook.develop(photo.orient(front).centerSquare()))
                }
                val saved = withContext(Dispatchers.IO) {
                    Stamps.add(stamp, shotLook.film.name).also { if (saveToGallery) Stamps.copyToGallery(context, it) }
                }
                lastStamp = loadThumb(saved.image, 200)
                lastId = saved.id
            }.onFailure { Toast.makeText(context, "Chụp lỗi: ${it.message}", Toast.LENGTH_SHORT).show() }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Chụp một khoảnh khắc", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text("vuốt trên màn hình để đổi filter", style = MaterialTheme.typography.bodySmall, color = Palette.InkSoft)
        Spacer(Modifier.size(14.dp))

        // The camera: a pastel body with a viewfinder bump, a flash and a shutter on top; the live preview is its screen.
        Box(Modifier.fillMaxWidth().graphicsLayer { scaleX = press.value; scaleY = press.value }) {
            Box(Modifier.padding(start = 28.dp).size(64.dp, 30.dp).background(Palette.Butter, RoundedCornerShape(12.dp)))
            Box(
                Modifier.align(Alignment.TopEnd).padding(end = 30.dp).size(58.dp, 34.dp)
                    .background(Palette.Rose, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 6.dp))
                    .clickable(enabled = !busy, onClickLabel = "Chụp tem", onClick = ::shoot),
            )
            Column(
                Modifier.padding(top = 18.dp).fillMaxWidth().shadow(10.dp, RoundedCornerShape(36.dp), ambientColor = Palette.Rose, spotColor = Palette.Rose)
                    .background(Palette.Pink, RoundedCornerShape(36.dp)).padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).background(Palette.Paper, CircleShape))
                    Spacer(Modifier.size(8.dp))
                    Box(Modifier.size(8.dp).background(if (busy) Palette.Butter else Palette.Mint, CircleShape))
                    Spacer(Modifier.weight(1f))
                    Text("MEMORY STAMP", style = MaterialTheme.typography.labelSmall, color = Palette.Paper, letterSpacing = 2.sp)
                }
                Spacer(Modifier.size(12.dp))
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).background(Palette.Paper, RoundedCornerShape(24.dp)).padding(8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .pointerInput(Unit) {
                            var dx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dx = 0f },
                                onDragEnd = { if (dx < -80) pick(filmIndex + 1) else if (dx > 80) pick(filmIndex - 1) },
                            ) { _, d -> dx += d }
                        },
                ) {
                    AndroidView({ previewView }, Modifier.fillMaxSize())
                    Canvas(Modifier.fillMaxSize()) { drawIntoCanvas { look.drawExtras(it.nativeCanvas, size.width, size.height) } }
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash.value }.background(Color.White))
                    Text(
                        look.film.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Ink,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                            .background(Palette.Paper.copy(alpha = 0.85f), CircleShape).padding(horizontal = 12.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.size(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        lastStamp?.let {
                            Image(it.asImageBitmap(), "Tem vừa chụp", Modifier.size(48.dp).rotate(tiltOf(lastId)))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier.size(64.dp).background(Palette.Paper, CircleShape).padding(6.dp)
                            .background(if (busy) Palette.InkSoft else Palette.Rose, CircleShape)
                            .clickable(enabled = !busy, onClickLabel = "Chụp tem", onClick = ::shoot),
                    )
                    Spacer(Modifier.weight(1f))
                    Bubble(R.drawable.ic_flip, "Đổi camera", color = Palette.Paper, size = 48.dp) { front = !front }
                }
            }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(filmIndex) { listState.animateScrollToItem(maxOf(0, filmIndex - 1)) }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 18.dp)) {
            itemsIndexed(FILMS) { i, f -> Pill(f.name, i == filmIndex) { pick(i) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text("Độ mạnh", style = MaterialTheme.typography.labelMedium, color = Palette.InkSoft)
            Slider(
                value = look.strength,
                onValueChange = { look = look.copy(strength = it) },
                modifier = Modifier.padding(start = 12.dp),
                colors = SliderDefaults.colors(thumbColor = Palette.Rose, activeTrackColor = Palette.Pink, inactiveTrackColor = Palette.PinkSoft),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            Pill("Hạt", look.grain) { look = look.copy(grain = !look.grain) }
            Pill("Viền tối", look.vignette) { look = look.copy(vignette = !look.vignette) }
            Pill("Lóa sáng", look.leak) { look = look.copy(leak = !look.leak) }
            Pill("Ngày", look.date) { look = look.copy(date = !look.date) }
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
