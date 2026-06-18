package com.mindease.ui.chatbot

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.accompanist.permissions.*
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun ChatbotScreen(
    navController: NavController,
    viewModel: ChatbotViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Image state
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                selectedBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
            } catch (_: Exception) { selectedBitmap = null }
        }
    }

    // Voice
    var isListening by remember { mutableStateOf(false) }
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) engine?.language = Locale.getDefault()
        }
        engine
    }

    // Auto-speak bot replies when in voice mode
    val lastMsgCount = remember { mutableStateOf(uiState.messages.size) }
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.size > lastMsgCount.value) {
            val last = uiState.messages.lastOrNull()
            if (last != null && !last.isUser && uiState.voiceMode) {
                tts?.speak(last.text, TextToSpeech.QUEUE_FLUSH, null, "reply")
            }
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
        lastMsgCount.value = uiState.messages.size
    }


    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
            tts?.shutdown()
        }
    }

    fun startListening() {
        if (!micPermission.status.isGranted) { micPermission.launchPermissionRequest(); return }
        isListening = true
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.isNotBlank()) viewModel.sendMessage(text)
                isListening = false
            }
            override fun onPartialResults(p: Bundle?) {
                val text = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (text.isNotBlank()) inputText = text
            }
            override fun onError(e: Int) { isListening = false }
            override fun onReadyForSpeech(p0: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        speechRecognizer.startListening(intent)
    }

    // Mic pulse
    val infiniteTransition = rememberInfiniteTransition(label = "mic")
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse), label = "micPulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF4ECDC4), Color(0xFF3E8B7A)))),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Psychology, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("MindEase AI", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                            Text("Health & Wellness", fontSize = 11.sp, color = Color.White.copy(0.7f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleVoiceMode() }) {
                        Icon(
                            if (uiState.voiceMode) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            "Voice", tint = Color.White.copy(if (uiState.voiceMode) 1f else 0.5f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
            )
        }
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFF8FAFC))) {
            // Messages
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                state = listState, contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(uiState.messages) { msg -> MessageBubble(msg) }
                if (uiState.isLoading) {
                    item {
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF4ECDC4))
                            Spacer(Modifier.width(8.dp))
                            Text("Thinking...", fontSize = 13.sp, color = Color(0xFF94A3B8))
                        }
                    }
                }
            }

            // Image preview
            if (selectedBitmap != null) {
                Card(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                ) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Selected image",
                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Image attached", fontSize = 13.sp, color = Color(0xFF64748B), modifier = Modifier.weight(1f))
                        IconButton(onClick = { selectedBitmap = null; selectedImageUri = null }) {
                            Icon(Icons.Default.Close, "Remove", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Input bar
            Surface(color = Color.White, tonalElevation = 4.dp) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Image upload
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFF1F5F9))
                    ) {
                        Icon(Icons.Default.Image, "Upload Image", tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
                    }

                    Spacer(Modifier.width(6.dp))

                    // Mic button
                    IconButton(
                        onClick = { if (isListening) { speechRecognizer.stopListening(); isListening = false } else startListening() },
                        modifier = Modifier.size(40.dp).scale(if (isListening) micScale else 1f).clip(CircleShape)
                            .background(if (isListening) Color(0xFFEF4444).copy(0.15f) else Color(0xFFF1F5F9))
                    ) {
                        Icon(Icons.Default.Mic, "Voice", tint = if (isListening) Color(0xFFEF4444) else Color(0xFF64748B), modifier = Modifier.size(20.dp))
                    }

                    Spacer(Modifier.width(6.dp))

                    OutlinedTextField(
                        value = inputText, onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask about health...", color = Color(0xFF94A3B8), fontSize = 14.sp) },
                        shape = RoundedCornerShape(20.dp), singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = Color(0xFF1E293B), fontSize = 15.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF1E293B),
                            unfocusedTextColor = Color(0xFF1E293B),
                            cursorColor = Color(0xFF4ECDC4),
                            focusedBorderColor = Color(0xFF4ECDC4),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )

                    Spacer(Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank() || selectedBitmap != null) {
                                val msg = inputText.trim().ifBlank { "Analyze this image for health concerns" }
                                viewModel.sendMessage(msg, selectedBitmap)
                                inputText = ""; selectedBitmap = null; selectedImageUri = null
                            }
                        },
                        modifier = Modifier.size(40.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFF4ECDC4), Color(0xFF3E8B7A))))
                    ) { Icon(Icons.Default.Send, "Send", tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessageUI) {
    val isUser = message.isUser
    val bgBrush = if (isUser) Brush.linearGradient(listOf(Color(0xFF5B8DEF), Color(0xFF3E6FD4)))
        else Brush.linearGradient(listOf(Color.White, Color.White))
    val textColor = if (isUser) Color.White else Color(0xFF1E293B)
    val shape = if (isUser) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
        else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = shape, color = Color.Transparent,
            shadowElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.background(bgBrush).padding(14.dp)) {
                // Show attached image thumbnail
                if (message.imageBitmap != null) {
                    Image(
                        bitmap = message.imageBitmap.asImageBitmap(),
                        contentDescription = "Attached image",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(message.text, color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}
