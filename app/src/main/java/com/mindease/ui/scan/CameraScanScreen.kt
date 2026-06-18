package com.mindease.ui.scan

import android.Manifest
import android.content.Context
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.accompanist.permissions.*
import com.mindease.ui.theme.PrimaryBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CameraScanScreen(
    navController: NavController,
    viewModel: CameraScanViewModel,
    scanType: String = "blink"
) {
    val context = LocalContext.current
    val scanState by viewModel.scanState.collectAsState()
    var showGuide by remember { mutableStateOf(true) }

    val permissionState = rememberMultiplePermissionsState(listOf(Manifest.permission.CAMERA))

    LaunchedEffect(Unit) {
        viewModel.resetState()
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    // Show guide popup first
    if (showGuide) {
        MeasurementGuidePopup(
            scanType = scanType,
            onStart = { showGuide = false }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (scanType == "blink") "Blink Scan" else "Heart Rate Scan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (!permissionState.allPermissionsGranted) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                    Text("Grant Camera Permission")
                }
            }
            return@Scaffold
        }

        if (scanType == "hr") {
            HRCameraContent(viewModel, scanState, context, navController, padding)
        } else {
            BlinkCameraContent(viewModel, scanState, context, navController, padding)
        }
    }
}

// ─── Animated Guide Popup ─────────────────────────────────

@Composable
fun MeasurementGuidePopup(scanType: String, onStart: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "guide")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val blink by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(300, delayMillis = 2500, easing = LinearEasing), RepeatMode.Reverse
        ), label = "blink"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            // Animated illustration
            Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                if (scanType == "hr") {
                    // Finger on camera animation
                    Canvas(modifier = Modifier.size(200.dp)) {
                        val cx = size.width / 2; val cy = size.height / 2
                        // Phone outline
                        drawRoundRect(Color(0xFF334155), cornerRadius = androidx.compose.ui.geometry.CornerRadius(20f),
                            style = Stroke(width = 3f), topLeft = Offset(cx - 40, cy - 70), size = androidx.compose.ui.geometry.Size(80f, 140f))
                        // Camera lens
                        drawCircle(Color(0xFF475569), radius = 12f, center = Offset(cx, cy - 35))
                        drawCircle(Color(0xFF1E293B), radius = 8f, center = Offset(cx, cy - 35))
                        // Flash
                        drawCircle(Color(0xFFFBBF24).copy(alpha = pulse), radius = 6f, center = Offset(cx + 18, cy - 35))
                        // Finger
                        drawRoundRect(Color(0xFFE2A07C).copy(alpha = 0.85f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(30f),
                            topLeft = Offset(cx - 35, cy - 55), size = androidx.compose.ui.geometry.Size(70f, 50f))
                        // Pulse ring
                        drawCircle(Color(0xFFEF4444).copy(alpha = (1f - pulse) * 0.5f), radius = 50f * pulse, center = Offset(cx, cy - 35), style = Stroke(2f))
                    }
                } else {
                    // Face with blinking eyes
                    Canvas(modifier = Modifier.size(200.dp)) {
                        val cx = size.width / 2; val cy = size.height / 2
                        // Face outline
                        drawCircle(Color(0xFF334155), radius = 70f, center = Offset(cx, cy), style = Stroke(3f))
                        // Eyes
                        val eyeH = 8f * blink.coerceAtLeast(0.1f)
                        drawOval(Color(0xFF5B8DEF), topLeft = Offset(cx - 30, cy - 10 - eyeH / 2), size = androidx.compose.ui.geometry.Size(16f, eyeH))
                        drawOval(Color(0xFF5B8DEF), topLeft = Offset(cx + 14, cy - 10 - eyeH / 2), size = androidx.compose.ui.geometry.Size(16f, eyeH))
                        // Mouth
                        drawArc(Color(0xFF64748B), startAngle = 0f, sweepAngle = 180f, useCenter = false,
                            topLeft = Offset(cx - 15, cy + 15), size = androidx.compose.ui.geometry.Size(30f, 15f), style = Stroke(2f, cap = StrokeCap.Round))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                if (scanType == "hr") "Heart Rate Measurement" else "Eye Blink Detection",
                fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                if (scanType == "hr")
                    "Place your finger firmly over the rear camera lens, covering both the camera and flashlight.\n\nKeep your finger still for 20 seconds."
                else
                    "Position your face clearly in front of the camera.\n\nBlink naturally for 15 seconds while we detect your blink rate.",
                color = Color(0xAAFFFFFF), textAlign = TextAlign.Center, lineHeight = 22.sp, fontSize = 15.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Tips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (scanType == "hr") {
                    GuideTip("💡", "Ensure good lighting in the room")
                    GuideTip("🤚", "Press finger firmly — no gaps")
                    GuideTip("⏱️", "Stay still for 20 seconds")
                } else {
                    GuideTip("👀", "Keep both eyes visible to camera")
                    GuideTip("💡", "Good lighting helps accuracy")
                    GuideTip("⏱️", "Blink naturally for 15 seconds")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF4ECDC4), Color(0xFF3E8B7A))),
                        RoundedCornerShape(16.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Start Measurement", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun GuideTip(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 16.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = Color(0xCCFFFFFF), fontSize = 13.sp)
    }
}

// ─── Blink Camera ─────────────────────────────────────────

@Composable
fun BlinkCameraContent(
    viewModel: CameraScanViewModel, scanState: ScanState,
    context: Context, navController: NavController, padding: PaddingValues
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var blinkCount by remember { mutableStateOf(0) }
    var isLeftEyeClosed by remember { mutableStateOf(false) }
    var isRightEyeClosed by remember { mutableStateOf(false) }
    var lastBlinkTime by remember { mutableStateOf(0L) }
    var measurementProgress by remember { mutableStateOf(0f) }
    var isMeasuring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(padding)) {
        AndroidView(factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val options = FaceDetectorOptions.Builder().setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL).build()
                val detector = FaceDetection.getClient(options)
                val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

                imageAnalysis.setAnalyzer(executor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && isMeasuring) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        detector.process(image)
                            .addOnSuccessListener { faces ->
                                for (face in faces) {
                                    val lp = face.leftEyeOpenProbability
                                    val rp = face.rightEyeOpenProbability
                                    if (lp != null && rp != null) {
                                        val leftClosed = lp < 0.15f
                                        val rightClosed = rp < 0.15f
                                        val now = System.currentTimeMillis()
                                        // Debounce: 200ms between blinks
                                        if (leftClosed && rightClosed && !isLeftEyeClosed && !isRightEyeClosed && (now - lastBlinkTime > 200)) {
                                            blinkCount++
                                            lastBlinkTime = now
                                        }
                                        isLeftEyeClosed = leftClosed
                                        isRightEyeClosed = rightClosed
                                        if (lp > 0.6f) isLeftEyeClosed = false
                                        if (rp > 0.6f) isRightEyeClosed = false
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalysis)
                } catch (_: Exception) {}
            }, executor)
            previewView
        })

        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            when (scanState) {
                is ScanState.Idle -> {
                    if (isMeasuring) {
                        LinearProgressIndicator(progress = { measurementProgress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Detecting blinks... ${(measurementProgress * 15).toInt()}s / 15s", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Blinks detected: $blinkCount", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Position your face clearly in view", fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        FloatingActionButton(onClick = {
                            isMeasuring = true; blinkCount = 0; lastBlinkTime = 0L
                            scope.launch {
                                val startMs = System.currentTimeMillis()
                                while (System.currentTimeMillis() - startMs < 15000) {
                                    measurementProgress = ((System.currentTimeMillis() - startMs) / 15000f).coerceIn(0f, 1f)
                                    delay(100)
                                }
                                measurementProgress = 1f; isMeasuring = false
                                viewModel.setBlinkSuccess(blinkCount * 4)
                            }
                        }) { Icon(Icons.Default.Videocam, contentDescription = "Start") }
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to start 15s blink scan")
                    }
                }
                is ScanState.Success, is ScanState.HRSuccess -> {
                    LaunchedEffect(Unit) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("blink_result", if (scanState is ScanState.Success) scanState.blinkRate else 0)
                        navController.popBackStack()
                    }
                }
                is ScanState.Error -> {
                    Text(scanState.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { isMeasuring = false; measurementProgress = 0f; blinkCount = 0; viewModel.resetState() }) { Text("Retry") }
                }
                else -> {}
            }
        }
    }
}

// ─── HR Camera with improved algorithm ────────────────────

@Composable
fun HRCameraContent(
    viewModel: CameraScanViewModel, scanState: ScanState,
    context: Context, navController: NavController, padding: PaddingValues
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val currentHR by viewModel.currentHeartRate.collectAsState()
    val signalQuality by viewModel.signalQuality.collectAsState()
    var measurementProgress by remember { mutableStateOf(0f) }
    var isMeasuring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().padding(padding)) {
        AndroidView(factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()

                val redChannelHistory = mutableListOf<Double>()
                var frameCount = 0
                var warmupDone = false

                imageAnalysis.setAnalyzer(executor) { image ->
                    val buffer = image.planes[0].buffer
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    var sum = 0L
                    for (b in data) sum += (b.toInt() and 0xFF)
                    val avg = sum.toDouble() / data.size

                    frameCount++

                    // Signal quality check — finger covering camera makes avg very high (>60)
                    val quality = when {
                        avg > 100 -> SignalQuality.GOOD
                        avg > 60 -> SignalQuality.FAIR
                        avg > 30 -> SignalQuality.POOR
                        else -> SignalQuality.NONE
                    }
                    viewModel.updateSignalQuality(quality)

                    // Skip first 90 frames (~3s) for camera warmup
                    if (frameCount < 90) {
                        image.close()
                        return@setAnalyzer
                    }

                    if (quality == SignalQuality.GOOD || quality == SignalQuality.FAIR) {
                        redChannelHistory.add(avg)
                        if (redChannelHistory.size > 600) redChannelHistory.removeAt(0)

                        if (redChannelHistory.size > 150) {
                            val bpm = computeHeartRateImproved(redChannelHistory)
                            if (bpm in 50..170) {
                                viewModel.updateCurrentHeartRate(bpm)
                            }
                        }
                    }
                    image.close()
                }

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis)
                    camera.cameraControl.enableTorch(true)
                } catch (_: Exception) {}
            }, executor)
            previewView
        })

        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
            // Signal quality indicator
            if (isMeasuring) {
                val (qualityText, qualityColor) = when (signalQuality) {
                    SignalQuality.GOOD -> "Signal: Good ✓" to Color(0xFF22C55E)
                    SignalQuality.FAIR -> "Signal: Fair" to Color(0xFFF59E0B)
                    SignalQuality.POOR -> "⚠ Place finger more firmly" to Color(0xFFEF4444)
                    SignalQuality.NONE -> "⚠ Cover camera with finger" to Color(0xFFEF4444)
                }
                Text(qualityText, color = qualityColor, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }

            when (scanState) {
                is ScanState.Idle -> {
                    if (isMeasuring) {
                        LinearProgressIndicator(progress = { measurementProgress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Measuring... ${(measurementProgress * 20).toInt()}s / 20s", fontWeight = FontWeight.Bold)
                        if (currentHR > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text("$currentHR BPM", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    } else {
                        Text("Place your finger firmly over the rear camera", fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        FloatingActionButton(onClick = {
                            isMeasuring = true; viewModel.startHRMeasurement()
                            scope.launch {
                                val startMs = System.currentTimeMillis()
                                while (System.currentTimeMillis() - startMs < 20000) {
                                    measurementProgress = ((System.currentTimeMillis() - startMs) / 20000f).coerceIn(0f, 1f)
                                    delay(200)
                                }
                                measurementProgress = 1f
                            }
                        }) { Icon(Icons.Default.Favorite, contentDescription = "Start HR") }
                        Spacer(Modifier.height(8.dp))
                        Text("Tap to start heart rate measurement")
                    }
                }
                is ScanState.MeasuringHR -> {
                    LinearProgressIndicator(progress = { measurementProgress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Analyzing heart rate...", fontWeight = FontWeight.Medium)
                    if (currentHR > 0) {
                        Text("$currentHR BPM", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
                is ScanState.HRSuccess -> {
                    LaunchedEffect(Unit) {
                        navController.previousBackStackEntry?.savedStateHandle?.set("hr_result", scanState.heartRate)
                        navController.popBackStack()
                    }
                }
                is ScanState.Error -> {
                    Text(scanState.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { isMeasuring = false; measurementProgress = 0f; viewModel.resetState() }) { Text("Retry") }
                }
                else -> {}
            }
        }
    }
}

/**
 * Improved HR algorithm with bandpass filtering and autocorrelation peak detection.
 */
private fun computeHeartRateImproved(signal: List<Double>): Int {
    if (signal.size < 150) return 0

    // 1. Detrend — remove mean
    val mean = signal.average()
    val detrended = signal.map { it - mean }

    // 2. Simple moving average smoothing (window=5) — acts as low-pass
    val smoothed = detrended.windowed(5) { it.average() }
    if (smoothed.size < 100) return 0

    // 3. Bandpass approximation: subtract heavy smooth (high-pass) from light smooth
    val heavySmoothed = smoothed.windowed(15) { it.average() }
    if (heavySmoothed.size < 60) return 0
    val bandpassed = heavySmoothed.indices.map {
        smoothed[it + 7] - heavySmoothed[it] // offset to align centers
    }
    if (bandpassed.size < 60) return 0

    // 4. Autocorrelation to find dominant period
    val fps = 30.0
    val minLag = (fps * 60.0 / 170).toInt() // ~170 BPM max
    val maxLag = (fps * 60.0 / 50).toInt()  // ~50 BPM min
    val effectiveMaxLag = minOf(maxLag, bandpassed.size / 2)

    if (minLag >= effectiveMaxLag) return 0

    var bestLag = minLag
    var bestCorr = -1.0

    for (lag in minLag..effectiveMaxLag) {
        var corr = 0.0
        var norm1 = 0.0
        var norm2 = 0.0
        val n = bandpassed.size - lag
        for (i in 0 until n) {
            corr += bandpassed[i] * bandpassed[i + lag]
            norm1 += bandpassed[i] * bandpassed[i]
            norm2 += bandpassed[i + lag] * bandpassed[i + lag]
        }
        val normFactor = sqrt(norm1 * norm2)
        if (normFactor > 0) {
            val normalized = corr / normFactor
            if (normalized > bestCorr) {
                bestCorr = normalized
                bestLag = lag
            }
        }
    }

    // Require minimum correlation strength
    if (bestCorr < 0.3) return 0

    val bpm = (fps * 60.0 / bestLag).roundToInt()
    return if (bpm in 50..170) bpm else 0
}