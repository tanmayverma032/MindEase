package com.mindease.ui.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.health.connect.client.PermissionController
import com.mindease.data.HealthConnectManager
import com.mindease.ui.navigation.Screen
import com.mindease.ui.theme.*
import com.mindease.ui.common.BottomNavigationBar
import com.mindease.ui.common.ChatbotOverlay
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun DashboardScreen(
    navController: NavController,
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showChatbot by remember { mutableStateOf(false) }
    var showStepsPopup by remember { mutableStateOf(false) }
    var showSleepPopup by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectManager.permissions)) {
            viewModel.refreshDashboard()
        }
    }

    val standardPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val canStartService = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissions[android.Manifest.permission.ACTIVITY_RECOGNITION] == true
        } else true
        
        if (canStartService) {
            try {
                com.mindease.data.SleepDetectionService.start(context)
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Failed to start sleep service", e)
            }
            try {
                com.mindease.data.StepCountService.start(context)
            } catch (e: Exception) {
                android.util.Log.e("Dashboard", "Failed to start step service", e)
            }
        }
        permissionLauncher.launch(HealthConnectManager.permissions)
    }

    LaunchedEffect(Unit) {
        val perms = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                perms.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                perms.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (perms.isNotEmpty()) {
            standardPermissionLauncher.launch(perms.toTypedArray())
        } else {
            try {
                com.mindease.data.SleepDetectionService.start(context)
            } catch (e: Exception) {}
            try {
                com.mindease.data.StepCountService.start(context)
            } catch (e: Exception) {}
            permissionLauncher.launch(HealthConnectManager.permissions)
        }
    }

    // refresh + live
    LaunchedEffect(Unit) {
        viewModel.refreshDashboard()
        while (true) {
            delay(10000)
            viewModel.refreshDashboard()
        }
    }

    // Sleep onboarding dialog
    if (uiState.showSleepOnboarding) {
        AlertDialog(
            onDismissRequest = { /* Must select an option */ },
            title = {
                Text(
                    "Sleep Schedule",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
            },
            text = {
                Text(
                    "Are you a day shift sleeper or a night shift sleeper?\n\n" +
                            "This helps us accurately detect your sleep patterns.",
                    color = Color(0xFF64748B)
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.saveShiftType("day") },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("☀\uFE0F Day Shift", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { viewModel.saveShiftType("night") },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("\uD83C\uDF19 Night Shift")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    val greeting = getGreetingOnly()
    val userName = uiState.userName.ifBlank { "User" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp)
        ) {

            // HEADER
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, null, tint = GreenAccent)
                    Spacer(modifier = Modifier.width(6.dp))

                    Column {
                        Text(
                            greeting,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            userName.uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // STRESS CARD
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Text("Current Stress Level", color = Color.White.copy(0.8f))
                    Text(
                        uiState.stressLevel.ifBlank { "—" },
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        when {
                            uiState.stressLevel == "No scan yet" -> "Take a scan to see results"
                            uiState.stressLevel == "Unknown" -> "Take a scan to see results"
                            else -> "Latest scan result"
                        },
                        color = Color.White.copy(0.8f),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // SCAN BUTTON
            Button(
                onClick = { navController.navigate(Screen.Assessment.route) },
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(Icons.Default.MonitorHeart, null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start New Scan", color = Color.White)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Row 1 — Total Scans (clickable → History) + Days Streak
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clickable {
                            navController.navigate(Screen.History.route) {
                                popUpTo(Screen.Dashboard.route)
                                launchSingleTop = true
                            }
                        }
                ) {
                    StatCard(
                        label = "Total Scans",
                        value = uiState.totalScans.toString(),
                        icon = Icons.Default.ChatBubbleOutline
                    )
                }
                Box(Modifier.weight(1f)) {
                    StatCard(
                        label = "Days Streak",
                        value = uiState.dayStreak.toString(),
                        icon = Icons.Default.Psychology
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Row 2 — Steps (clickable → popup) + Sleep (clickable → popup)
            Row(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clickable { showStepsPopup = true }
                ) {
                    StatCard(
                        label = "Steps Today",
                        value = uiState.steps.toString(),
                        icon = Icons.Default.DirectionsWalk
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clickable { showSleepPopup = true }
                ) {
                    StatCard(
                        label = "Sleep",
                        value = String.format("%.1f h", uiState.sleepHours),
                        icon = Icons.Default.Bedtime
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            WellnessTipCard(
                navController = navController,
                onChatClick = { showChatbot = true }
            )
        }

        // Bottom nav is now managed by MainPagerScreen

        if (showChatbot) {
            ChatbotOverlay(
                onDismiss = { showChatbot = false },
                navController = navController
            )
        }

        // Steps Popup
        if (showStepsPopup) {
            HealthDetailPopup(
                title = "Daily Steps",
                subtitle = "Last 7 days activity",
                icon = Icons.Default.DirectionsWalk,
                accentColor = PrimaryBlue,
                currentValue = uiState.steps.toString(),
                currentLabel = "Steps Today",
                data = uiState.stepsHistory.map { Pair(it.first, it.second.toFloat()) },
                unitLabel = "steps",
                maxValue = 10000f,
                onDismiss = { showStepsPopup = false }
            )
        }

        // Sleep Popup
        if (showSleepPopup) {
            HealthDetailPopup(
                title = "Sleep Duration",
                subtitle = "Last 7 days sleep tracking",
                icon = Icons.Default.Bedtime,
                accentColor = Color(0xFF8B5CF6),
                currentValue = String.format("%.1f h", uiState.sleepHours),
                currentLabel = "Last Night",
                data = uiState.sleepHistory.map { Pair(it.first, it.second.toFloat()) },
                unitLabel = "hours",
                maxValue = 12f,
                onDismiss = { showSleepPopup = false }
            )
        }
    }
}

// ─── Health Detail Popup ──────────────────────────────────

@Composable
fun HealthDetailPopup(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    currentValue: String,
    currentLabel: String,
    data: List<Pair<String, Float>>,
    unitLabel: String,
    maxValue: Float,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Animate entry
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        val scale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.85f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
            label = "popupScale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(250),
            label = "popupAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * alpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Don't dismiss when clicking card */ },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accentColor.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = accentColor, modifier = Modifier.size(22.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF1E293B))
                                Text(subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8))
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Current value highlight
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(accentColor.copy(alpha = 0.08f))
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                currentValue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp,
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(currentLabel, fontSize = 14.sp, color = Color(0xFF64748B))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Bar Chart
                    Text("Last 7 Days", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(12.dp))

                    val effectiveMax = maxOf(maxValue, (data.maxOfOrNull { it.second } ?: 0f) * 1.2f)
                    val hasData = data.any { it.second > 0f }

                    if (!hasData) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No data available yet", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    } else {
                        // Animated bar chart
                        val animProgress by animateFloatAsState(
                            targetValue = 1f,
                            animationSpec = tween(800, easing = FastOutSlowInEasing),
                            label = "barAnim"
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                        ) {
                            val barCount = data.size
                            val sidePad = 24f
                            val chartWidth = size.width - sidePad * 2
                            val barSpacing = 10f
                            val barWidth = (chartWidth - barSpacing * (barCount - 1)) / barCount
                            val chartHeight = size.height - 44f  // leave room for labels below

                            data.forEachIndexed { index, (label, value) ->
                                val barHeight = if (effectiveMax > 0) (value / effectiveMax) * chartHeight * animProgress else 0f
                                val x = sidePad + index * (barWidth + barSpacing)
                                val y = chartHeight - barHeight

                                // Bar gradient
                                val barBrush = Brush.verticalGradient(
                                    colors = listOf(accentColor, accentColor.copy(alpha = 0.5f)),
                                    startY = y, endY = chartHeight
                                )

                                drawRoundRect(
                                    brush = barBrush,
                                    topLeft = Offset(x, y),
                                    size = Size(barWidth, barHeight.coerceAtLeast(2f)),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )

                                // Value label on top
                                if (value > 0) {
                                    val valLabel = when {
                                        value >= 1000 -> "${(value / 1000).toInt()}k"
                                        value >= 10 -> "${value.toInt()}"
                                        else -> String.format("%.1f", value)
                                    }
                                    drawContext.canvas.nativeCanvas.drawText(
                                        valLabel, x + barWidth / 2, y - 8f,
                                        android.graphics.Paint().apply {
                                            color = accentColor.hashCode()
                                            textSize = 22f
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            isFakeBoldText = true
                                            isAntiAlias = true
                                        }
                                    )
                                }

                                // Day label directly below bar (aligned)
                                drawContext.canvas.nativeCanvas.drawText(
                                    label, x + barWidth / 2, chartHeight + 28f,
                                    android.graphics.Paint().apply {
                                        color = 0xFF94A3B8.toInt()
                                        textSize = 24f
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        isAntiAlias = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Greeting
fun getGreetingOnly(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..20 -> "Good Evening"
        else -> "Good Night"
    }
}

// ✅ Premium StatCard with icon badge
@Composable
fun StatCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                PrimaryBlue.copy(alpha = 0.15f),
                                TealAccent.copy(alpha = 0.10f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                value,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color(0xFF1E293B)
            )

            Text(
                label,
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

// Daily rotating wellness tips
private val dailyTips = listOf(
    "Try 4-7-8 breathing technique for relaxation.",
    "Take a 10-minute walk to clear your mind.",
    "Practice gratitude — write 3 things you're thankful for today.",
    "Drink 8 glasses of water to stay hydrated and focused.",
    "Do a 5-minute body scan meditation before bed.",
    "Stretch for 5 minutes every hour while working.",
    "Limit screen time 1 hour before sleep for better rest.",
    "Listen to calming music for 15 minutes today.",
    "Try progressive muscle relaxation to release tension.",
    "Write your worries down — it helps reduce anxiety.",
    "Spend 10 minutes in nature or sunlight today.",
    "Practice box breathing: inhale-hold-exhale-hold 4s each.",
    "Eat a balanced meal with fruits and vegetables today.",
    "Connect with a friend or loved one for emotional wellness."
)

@Composable
fun WellnessTipCard(
    navController: NavController,
    onChatClick: () -> Unit
) {
    // Pick tip based on current day of year
    val todayTip = remember {
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        dailyTips[dayOfYear % dailyTips.size]
    }

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text("Today's Wellness Tip", fontWeight = FontWeight.SemiBold)

            Row(verticalAlignment = Alignment.CenterVertically) {

                TextButton(
                    onClick = { navController.navigate(Screen.WellnessTips.route) }
                ) {
                    Text("View All")
                }

                Spacer(modifier = Modifier.width(8.dp))

                FloatingActionButton(
                    onClick = onChatClick,
                    modifier = Modifier.size(48.dp),
                    containerColor = PrimaryBlue
                ) {
                    Icon(Icons.Default.Chat, null, tint = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Card(shape = RoundedCornerShape(16.dp)) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(listOf(GradientStart, GradientEnd))
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text("✨ Today's Tip", color = Color.White.copy(0.9f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        todayTip,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}