package com.mindease.ui.chatbot

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.permissions.*
import java.util.Locale
import kotlin.math.sin

/**
 * Voice-only AI assistant screen.
 * User speaks → AI processes → AI responds with voice.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VoiceAssistantScreen(
    navController: NavController,
    viewModel: ChatbotViewModel
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    var assistantState by remember { mutableStateOf(VoiceState.IDLE) }
    var lastUserText by remember { mutableStateOf("") }
    var lastAiText by remember { mutableStateOf("Tap the mic and ask me anything about health & wellness") }

    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) engine?.language = Locale.getDefault()
        }
        engine
    }

    // Watch for new AI replies
    val lastMsgCount = remember { mutableStateOf(uiState.messages.size) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.size > lastMsgCount.value) {
            val last = uiState.messages.lastOrNull()
            if (last != null && !last.isUser) {
                lastAiText = last.text
                assistantState = VoiceState.SPEAKING
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { assistantState = VoiceState.IDLE }
                    override fun onError(utteranceId: String?) { assistantState = VoiceState.IDLE }
                })
                tts?.speak(last.text, TextToSpeech.QUEUE_FLUSH, null, "voiceReply")
            }
        }
        lastMsgCount.value = uiState.messages.size
    }

    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) assistantState = VoiceState.PROCESSING
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
            tts?.shutdown()
        }
    }

    fun startListening() {
        if (!micPermission.status.isGranted) { micPermission.launchPermissionRequest(); return }
        assistantState = VoiceState.LISTENING
        lastUserText = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    lastUserText = text
                    assistantState = VoiceState.PROCESSING
                    viewModel.sendMessage(text)
                } else {
                    assistantState = VoiceState.IDLE
                }
            }
            override fun onPartialResults(p: Bundle?) {
                lastUserText = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: lastUserText
            }
            override fun onError(e: Int) { assistantState = VoiceState.IDLE }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() { if (assistantState == VoiceState.LISTENING) assistantState = VoiceState.PROCESSING }
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }

    // Animations
    val infiniteTransition = rememberInfiniteTransition(label = "voice")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse"
    )
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 6.28f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "wave"
    )

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F172A)))
        )
    ) {
        // Back button
        IconButton(
            onClick = { tts?.stop(); navController.popBackStack() },
            modifier = Modifier.padding(16.dp).align(Alignment.TopStart)
        ) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White.copy(0.7f)) }

        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Status text
            Text(
                when (assistantState) {
                    VoiceState.IDLE -> "Tap to speak"
                    VoiceState.LISTENING -> "Listening..."
                    VoiceState.PROCESSING -> "Processing..."
                    VoiceState.SPEAKING -> "Speaking..."
                },
                color = Color(0xFF4ECDC4), fontWeight = FontWeight.Medium, fontSize = 14.sp
            )

            Spacer(Modifier.height(24.dp))

            // Animated orb / wave
            Box(modifier = Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                when (assistantState) {
                    VoiceState.LISTENING -> {
                        // Sound wave animation
                        Canvas(Modifier.size(200.dp)) {
                            val cx = size.width / 2; val cy = size.height / 2
                            for (ring in 1..4) {
                                val r = 30f + ring * 20f
                                val alpha = (0.4f - ring * 0.08f).coerceAtLeast(0.05f)
                                drawCircle(Color(0xFFEF4444).copy(alpha = alpha * pulse), radius = r * pulse, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                            }
                        }
                        Box(
                            Modifier.size(80.dp).scale(pulse).clip(CircleShape).background(Color(0xFFEF4444).copy(0.2f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Mic, null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp)) }
                    }
                    VoiceState.SPEAKING -> {
                        // Audio wave bars
                        Canvas(Modifier.size(200.dp)) {
                            val cx = size.width / 2; val cy = size.height / 2
                            val barCount = 12
                            for (i in 0 until barCount) {
                                val angle = (i.toFloat() / barCount) * 6.28f
                                val h = 15f + 25f * ((sin(wavePhase + i * 0.8f) + 1f) / 2f)
                                val x = cx + 55f * kotlin.math.cos(angle).toFloat()
                                val y = cy + 55f * sin(angle).toFloat()
                                drawLine(
                                    Color(0xFF4ECDC4).copy(alpha = 0.7f),
                                    start = Offset(x, y - h / 2), end = Offset(x, y + h / 2),
                                    strokeWidth = 4f, cap = StrokeCap.Round
                                )
                            }
                        }
                        Box(
                            Modifier.size(80.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF4ECDC4).copy(0.3f), Color.Transparent))),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.GraphicEq, null, tint = Color(0xFF4ECDC4), modifier = Modifier.size(36.dp)) }
                    }
                    VoiceState.PROCESSING -> {
                        CircularProgressIndicator(Modifier.size(60.dp), color = Color(0xFF4ECDC4), strokeWidth = 3.dp)
                    }
                    VoiceState.IDLE -> {
                        Box(
                            Modifier.size(90.dp).clip(CircleShape).background(
                                Brush.radialGradient(listOf(Color(0xFF4ECDC4).copy(0.15f), Color.Transparent))
                            ).clickable { startListening() },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Mic, null, tint = Color(0xFF4ECDC4), modifier = Modifier.size(40.dp)) }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Last user message
            if (lastUserText.isNotBlank()) {
                Text("You said:", color = Color.White.copy(0.5f), fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(lastUserText, color = Color.White.copy(0.85f), fontSize = 15.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }

            // AI response
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.08f))
            ) {
                Text(
                    lastAiText, color = Color.White.copy(0.9f),
                    modifier = Modifier.padding(16.dp),
                    fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            // Mic button
            if (assistantState == VoiceState.IDLE || assistantState == VoiceState.SPEAKING) {
                Button(
                    onClick = { tts?.stop(); startListening() },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF4ECDC4), Color(0xFF3E8B7A))), RoundedCornerShape(26.dp)).padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tap to Speak", fontWeight = FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }
            }

            if (assistantState == VoiceState.LISTENING) {
                OutlinedButton(
                    onClick = { speechRecognizer.stopListening(); assistantState = VoiceState.PROCESSING },
                    shape = RoundedCornerShape(26.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444))
                ) {
                    Text("Stop Listening", color = Color(0xFFEF4444))
                }
            }
        }
    }
}

enum class VoiceState { IDLE, LISTENING, PROCESSING, SPEAKING }
