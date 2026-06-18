package com.mindease.ui.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mindease.ui.navigation.Screen
import com.mindease.ui.theme.BackgroundLight
import com.mindease.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentScreen(
    navController: NavController,
    viewModel: AssessmentViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show result screen if we have a success state
    if (uiState.successStressLevel != null) {
        AssessmentResultScreen(
            stressLevel = uiState.successStressLevel!!,
            stressScore = uiState.successStressScore,
            personalizedTips = uiState.personalizedTips,
            isTipsLoading = uiState.isTipsLoading,
            navController = navController,
            onFinnish = {
                viewModel.resetState()
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    TextButton(onClick = {
                        if (uiState.currentStep > 1) {
                            viewModel.previousStep()
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back", color = PrimaryBlue, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundLight)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundLight)
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                "Stress Assessment",
                fontSize = 28.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Step ${uiState.currentStep} of 6",
                color = Color(0xFF475569),
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { uiState.currentStep / 6f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = PrimaryBlue,
                trackColor = PrimaryBlue.copy(alpha = 0.2f),
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState.error != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        uiState.error!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // Step Content
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.currentStep) {
                    1 -> Step1BasicInfo(uiState, viewModel)
                    2 -> Step2BlinkDetect(uiState, viewModel, navController)
                    3 -> Step3RestingHR(uiState, viewModel, navController)
                    4 -> Step4PostExerciseHR(uiState, viewModel, navController)
                    5 -> Step5Sleep(uiState, viewModel)
                    6 -> Step6Submit(uiState, viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Step1BasicInfo(uiState: AssessmentState, viewModel: AssessmentViewModel) {
    var age by remember { mutableStateOf(uiState.age) }
    var gender by remember { mutableStateOf(uiState.gender.ifEmpty { "Male" }) }
    var balance by remember { mutableStateOf(uiState.workLifeBalance.ifEmpty { "Student" }) }
    var chestPain by remember { mutableStateOf(uiState.chestPain.ifEmpty { "No" }) }
    var chol by remember { mutableStateOf(uiState.cholesterol) }
    var temp by remember { mutableStateOf(uiState.bodyTemperature) }
    var ecg by remember { mutableStateOf(uiState.ecgResult.ifEmpty { "Normal" }) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Basic Information",
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )
                AssessmentField("Age", "Enter your age", age) { age = it }
                DropdownAssessmentField("Gender", listOf("Male", "Female"), gender) { gender = it }
                DropdownAssessmentField("Work Type", listOf("Student", "Working", "Homemaker"), balance) { balance = it }
                DropdownAssessmentField("Chest Pain", listOf("No", "Mild", "Yes"), chestPain) { chestPain = it }
                AssessmentField("Cholesterol (mg/dL)", "e.g., 200", chol) { chol = it }
                AssessmentField("Body Temperature (°C)", "e.g., 36", temp) { temp = it }
                DropdownAssessmentField("ECG Result", listOf("Sinus_Tachycardia", "Normal", "Mild_Tachycardia"), ecg) { ecg = it }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                viewModel.updateBasicInfo(age, gender, balance, chestPain, chol, temp, ecg)
                viewModel.nextStep()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            enabled = age.isNotBlank() && gender.isNotBlank() && balance.isNotBlank() && chestPain.isNotBlank() && chol.isNotBlank() && temp.isNotBlank() && ecg.isNotBlank()
        ) {
            Text("Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun Step2BlinkDetect(uiState: AssessmentState, viewModel: AssessmentViewModel, navController: NavController) {
    var manualInput by remember { mutableStateOf(uiState.blinkRate?.toString() ?: "") }
    val result = navController.currentBackStackEntry?.savedStateHandle?.get<Int>("blink_result")
    
    LaunchedEffect(result) {
        if (result != null) {
            viewModel.updateBlinkRate(result)
            manualInput = result.toString()
            navController.currentBackStackEntry?.savedStateHandle?.remove<Int>("blink_result")
        }
    }

    // Keep manualInput in sync with uiState (for background processing updates)
    LaunchedEffect(uiState.blinkRate) {
        if (uiState.blinkRate != null && manualInput.isEmpty()) {
            manualInput = uiState.blinkRate.toString()
        }
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Eye Blink Detection", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Position your face in the front camera view", color = Color(0xFF475569))
                
                Spacer(modifier = Modifier.height(32.dp))

                // Show processing indicator if blink is being processed in background
                if (uiState.isBlinkProcessing) {
                    CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Processing blink video...", color = PrimaryBlue, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                AssessmentField("Manual Entry (Blinks/min)", "e.g. 15", manualInput) {
                    manualInput = it
                    it.toIntOrNull()?.let { blinks -> viewModel.updateBlinkRate(blinks) }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("OR", color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(onClick = { navController.navigate(Screen.CameraScan.createRoute("blink")) }) {
                    Text("Open Camera to Capture (15s)")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.nextStep() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = manualInput.toIntOrNull() != null || uiState.isBlinkProcessing,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text(
                if (uiState.isBlinkProcessing) "Continue (processing in background)" else "Continue",
                color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun Step3RestingHR(uiState: AssessmentState, viewModel: AssessmentViewModel, navController: NavController) {
    var manualInput by remember { mutableStateOf(uiState.restingHeartRate?.toString() ?: "") }
    val hrResult = navController.currentBackStackEntry?.savedStateHandle?.get<Int>("hr_result")
    
    LaunchedEffect(hrResult) {
        if (hrResult != null) {
            viewModel.updateRestingHR(hrResult)
            manualInput = hrResult.toString()
            navController.currentBackStackEntry?.savedStateHandle?.remove<Int>("hr_result")
        }
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
    "Resting Heart Rate",
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    color = Color.Black
)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Place your finger tightly over the rear camera lens", color = Color(0xFF475569))
                
                Spacer(modifier = Modifier.height(32.dp))
                
                AssessmentField("Manual Entry (BPM)", "e.g. 75", manualInput) {
                    manualInput = it
                    it.toIntOrNull()?.let { bpm -> viewModel.updateRestingHR(bpm) }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("OR", color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(onClick = { navController.navigate(Screen.CameraScan.createRoute("hr")) }) {
                    Text("Measure with Camera (15s)")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.nextStep() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = manualInput.toIntOrNull() != null,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun Step4PostExerciseHR(uiState: AssessmentState, viewModel: AssessmentViewModel, navController: NavController) {
    var manualInput by remember { mutableStateOf(uiState.postExerciseHeartRate?.toString() ?: "") }
    val hrResult = navController.currentBackStackEntry?.savedStateHandle?.get<Int>("hr_result")
    
    LaunchedEffect(hrResult) {
        if (hrResult != null) {
            viewModel.updatePostExerciseHR(hrResult)
            manualInput = hrResult.toString()
            navController.currentBackStackEntry?.savedStateHandle?.remove<Int>("hr_result")
        }
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Post-Exercise Heart Rate", fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Measure after light physical activity", color = Color(0xFF475569))
                
                Spacer(modifier = Modifier.height(32.dp))
                
                AssessmentField("Manual Entry (BPM)", "e.g. 110", manualInput) {
                    manualInput = it
                    it.toIntOrNull()?.let { bpm -> viewModel.updatePostExerciseHR(bpm) }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("OR", color = Color.Gray, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(onClick = { navController.navigate(Screen.CameraScan.createRoute("hr")) }) {
                    Text("Measure using camera (15s)")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.nextStep() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = manualInput.toIntOrNull() != null,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun Step5Sleep(uiState: AssessmentState, viewModel: AssessmentViewModel) {
    var isAuto by remember { mutableStateOf(uiState.isAutoSleepSelected) }
    var hoursSlider by remember { mutableStateOf(uiState.sleepHours?.toFloat() ?: 7f) }

    // Real sensor data
    val autoSleep = uiState.autoSleepHours ?: 0.0
    val displayAutoSleep = if (autoSleep > 0) String.format("%.1f", autoSleep) else "0.0"

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Sleep Duration", fontWeight = FontWeight.SemiBold, fontSize = 20.sp , color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("How many hours did you sleep last night?", color = Color(0xFF475569))
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-detect from device", color = Color.Black, modifier = Modifier.weight(1f))
                    Switch(checked = isAuto, onCheckedChange = { isAuto = it })
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (isAuto) {
                    Text("$displayAutoSleep hours", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    if (autoSleep > 0) {
                        Text("Detected from your device", color = Color(0xFF475569))
                    } else {
                        Text("No sleep data available — using device inactive time", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                } else {
                    // Creative manual sleep input with + / - buttons
                    Text("🌙", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Minus button
                        FilledIconButton(
                            onClick = { if (hoursSlider > 0f) hoursSlider = (hoursSlider - 0.5f).coerceAtLeast(0f) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(0xFFE2E8F0),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("−", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Large hours display
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                String.format("%.1f", hoursSlider),
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            Text("hours", fontSize = 16.sp, color = Color(0xFF64748B))
                        }

                        Spacer(modifier = Modifier.width(24.dp))

                        // Plus button
                        FilledIconButton(
                            onClick = { if (hoursSlider < 14f) hoursSlider = (hoursSlider + 0.5f).coerceAtMost(14f) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = PrimaryBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        if (hoursSlider < 5f) "⚠️ Less than recommended"
                        else if (hoursSlider in 7f..9f) "✅ Recommended range"
                        else if (hoursSlider > 9f) "😴 Above average"
                        else "💤 Just below recommended",
                        fontSize = 13.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { 
                val sleepValue = if (isAuto) {
                    if (autoSleep > 0) autoSleep else 0.0
                } else {
                    String.format("%.1f", hoursSlider).toDouble()
                }
                viewModel.updateSleep(sleepValue, isAuto)
                viewModel.nextStep()
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Continue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun Step6Submit(uiState: AssessmentState, viewModel: AssessmentViewModel) {
    var isAuto by remember { mutableStateOf(uiState.isAutoStepsSelected) }
    var steps by remember { mutableStateOf(uiState.stepsPerDay.ifEmpty { "5000" }) }

    // Real sensor data
    val autoSteps = uiState.autoSteps ?: 0
    val displayAutoSteps = if (autoSteps > 0) {
        String.format("%,d", autoSteps)
    } else {
        "0"
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Daily Steps", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                Text("How many steps did you take today?", color = Color(0xFF475569))
                
                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Auto-detect from device",color = Color.Black,  modifier = Modifier.weight(1f))
                    Switch(checked = isAuto, onCheckedChange = { isAuto = it })
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (isAuto) {
                    Text(displayAutoSteps, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    if (autoSteps > 0) {
                        Text("Detected from your device", color = Color(0xFF475569))
                    } else {
                        Text("No step data available from sensors", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }
                } else {
                    AssessmentField("Steps", "e.g. 6000", steps) { steps = it }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(color = PrimaryBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Analyzing 12 Parameters...")
                } else {
                    Icon(androidx.compose.material.icons.Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ready to Analyze", color = Color.Gray)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { 
                val stepsValue = if (isAuto) autoSteps.toString() else steps
                viewModel.updateSteps(stepsValue, isAuto)
                viewModel.submitAssessment() 
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = !uiState.isSubmitting,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text(if (uiState.isSubmitting) "Submitting..." else "Submit Assessment", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AssessmentResultScreen(
    stressLevel: String,
    stressScore: Int?,
    personalizedTips: List<String>,
    isTipsLoading: Boolean,
    navController: NavController,
    onFinnish: () -> Unit
) {
    var showInfoDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier.fillMaxSize().background(BackgroundLight)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Analysis Complete", fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Card(
                modifier = Modifier.size(200.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("Stress Level", color = Color.White.copy(0.8f))
                        Text(stressLevel, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        
                        if (stressScore != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Score: $stressScore/100", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Info, contentDescription = "How is this calculated?", tint = Color.White.copy(0.8f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Personalized Tips Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "\uD83C\uDFAF Personalized Tips",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.Black
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (isTipsLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = PrimaryBlue
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Generating personalized tips...", color = Color.Gray, fontSize = 14.sp)
                        }
                    } else if (personalizedTips.isNotEmpty()) {
                        personalizedTips.forEachIndexed { index, tip ->
                            Row(
                                modifier = Modifier.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "${index + 1}.",
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue,
                                    fontSize = 15.sp,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    tip,
                                    color = Color(0xFF374151),
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                            if (index < personalizedTips.size - 1) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    } else {
                        Text(
                            "Keep up the good work! Maintain your healthy habits.",
                            color = Color.DarkGray,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            if (stressLevel.equals("High", ignoreCase = true)) {
                Button(
                    onClick = { navController.navigate(Screen.DoctorsMap.route) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Find Nearby Doctors")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = onFinnish,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Return Home")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("How stress is calculated", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Your Stress Score (0-100) is calculated using the following formula:")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("• Sleep Score × 0.35", fontWeight = FontWeight.Medium)
                    Text("• Step Activity Score × 0.25", fontWeight = FontWeight.Medium)
                    Text("• Heart Rate Score × 0.25", fontWeight = FontWeight.Medium)
                    Text("• Lifestyle Score × 0.15", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Higher scores indicate higher levels of stress. The final result combines your physiological parameters and lifestyle inputs.", fontSize = 13.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text("Got it", color = PrimaryBlue)
                }
            },
            containerColor = Color.White
        )
    }
}


@Composable
fun AssessmentField(label: String, placeholder: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, color = Color(0xFF1E293B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Color(0xFF94A3B8)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFE2E8F0),
                focusedBorderColor = PrimaryBlue,
                unfocusedContainerColor = Color(0xFFF8FAFC),
                focusedContainerColor = Color.White,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownAssessmentField(label: String, options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label, color = Color(0xFF1E293B), fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(6.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedOption,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedBorderColor = PrimaryBlue,
                    unfocusedContainerColor = Color(0xFFF8FAFC),
                    focusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                )
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Color.Black) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
