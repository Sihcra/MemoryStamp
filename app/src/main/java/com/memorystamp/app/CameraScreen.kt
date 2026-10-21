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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.size(8.dp))
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp))
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
            Text(
                look.film.name,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)
                    .background(Color(0x66000000), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }

        val listState = rememberLazyListState()
        LaunchedEffect(filmIndex) { listState.animateScrollToItem(maxOf(0, filmIndex - 1)) }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
            itemsIndexed(FILMS) { i, f ->
                FilterChip(selected = i == filmIndex, onClick = { pick(i) }, label = { Text(f.name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Độ mạnh", style = MaterialTheme.typography.labelMedium)
            Slider(value = look.strength, onValueChange = { look = look.copy(strength = it) }, modifier = Modifier.padding(start = 12.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(look.grain, { look = look.copy(grain = !look.grain) }, { Text("Hạt") })
            FilterChip(look.vignette, { look = look.copy(vignette = !look.vignette) }, { Text("Viền tối") })
            FilterChip(look.leak, { look = look.copy(leak = !look.leak) }, { Text("Lóa sáng") })
            FilterChip(look.date, { look = look.copy(date = !look.date) }, { Text("Ngày") })
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp)) {
                lastStamp?.let { Image(it.asImageBitmap(), "Tem vừa chụp", Modifier.fillMaxSize()) }
            }
            Box(
                Modifier.size(76.dp).border(4.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(8.dp)
                    .background(if (busy) Color.Gray else MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(enabled = !busy, onClickLabel = "Chụp tem") {
                        busy = true
                        val shotLook = look
                        scope.launch {
                            runCatching {
                                val photo = capture.take(context)
                                val stamp = withContext(Dispatchers.Default) {
                                    makeStamp(shotLook.develop(photo.orient(front).centerSquare()))
                                }
                                val saved = withContext(Dispatchers.IO) {
                                    Stamps.add(stamp, shotLook.film.name).also { if (saveToGallery) Stamps.copyToGallery(context, it) }
                                }
                                lastStamp = loadThumb(saved.image, 160)
                            }.onFailure { Toast.makeText(context, "Chụp lỗi: ${it.message}", Toast.LENGTH_SHORT).show() }
                            busy = false
                        }
                    },
            )
            IconButton(onClick = { front = !front }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.Refresh, "Đổi camera")
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
