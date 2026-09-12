package com.labuda.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class QrScannerActivity : ComponentActivity() {
    private val galleryPick = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            val image = InputImage.fromFilePath(this, uri)
            BarcodeScanning.getClient().process(image)
                .addOnSuccessListener { codes ->
                    codes.firstOrNull()?.rawValue?.takeIf { it.isNotBlank() }?.let { finishWithValue(it) }
                }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = Color.Black) {
                    QrScanScreen(
                        onBack = { finish() },
                        onGallery = { galleryPick.launch("image/*") },
                        onDetected = { finishWithValue(it) }
                    )
                }
            }
        }
    }

    private fun finishWithValue(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("LABUDA subscription", value))
        setResult(Activity.RESULT_OK, Intent().setData(android.net.Uri.parse(value)))
        finish()
    }
}

@Composable
private fun QrScanScreen(onBack: () -> Unit, onGallery: () -> Unit, onDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = remember {
        (context as ComponentActivity).registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionGranted = granted
            if (!granted) onBack()
        }
    }
    DisposableEffect(Unit) {
        if (!permissionGranted) permission.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    Box(Modifier.fillMaxSize()) {
        if (permissionGranted) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val executor = Executors.newSingleThreadExecutor()
                    var handled = false
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                        val analysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        val scanner = BarcodeScanning.getClient()
                        analysis.setAnalyzer(executor) { proxy ->
                            val media = proxy.image
                            if (media == null || handled) {
                                proxy.close()
                                return@setAnalyzer
                            }
                            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener { codes ->
                                    val value = codes.firstOrNull()?.rawValue
                                    if (!value.isNullOrBlank() && !handled) {
                                        handled = true
                                        onDetected(value)
                                    }
                                }
                                .addOnCompleteListener { proxy.close() }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }
        ScanMask()
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
        }
        TextButton(
            onClick = onGallery,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp).background(Color(0x99000000), RoundedCornerShape(20.dp)).padding(horizontal = 8.dp)
        ) {
            Icon(Icons.Filled.Image, contentDescription = null, tint = Color.White)
            Text("  Галерея", color = Color.White, fontSize = 16.sp)
        }
        Text(
            "Наведите QR в рамку",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp)
        )
    }
}

@Composable
private fun ScanMask() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val box = minOf(maxWidth, maxHeight) * 0.66f
        Canvas(Modifier.fillMaxSize()) {
            val side = box.toPx()
            val left = (size.width - side) / 2f
            val top = (size.height - side) / 2f
            drawRect(Color(0x99000000), size = ComposeSize(size.width, top))
            drawRect(Color(0x99000000), topLeft = Offset(0f, top), size = ComposeSize(left, side))
            drawRect(Color(0x99000000), topLeft = Offset(left + side, top), size = ComposeSize(size.width - left - side, side))
            drawRect(Color(0x99000000), topLeft = Offset(0f, top + side), size = ComposeSize(size.width, size.height - top - side))
            val stroke = 6.dp.toPx()
            val arm = side * 0.18f
            val color = Color.White
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(color, Offset(x, y), Offset(x + dx * arm, y), stroke)
                drawLine(color, Offset(x, y), Offset(x, y + dy * arm), stroke)
            }
            corner(left, top, 1f, 1f)
            corner(left + side, top, -1f, 1f)
            corner(left, top + side, 1f, -1f)
            corner(left + side, top + side, -1f, -1f)
        }
    }
}
