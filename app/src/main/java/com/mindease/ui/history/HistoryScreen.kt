package com.mindease.ui.history

import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mindease.network.HistoryItem
// Bottom nav is managed by MainPagerScreen
import com.mindease.ui.navigation.Screen
import com.mindease.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel
) {
    val historyState by viewModel.state.collectAsState()
    var stressFilter by remember { mutableStateOf("All") }

    val filteredHistory = remember(historyState.history, stressFilter) {
        if (stressFilter == "All") historyState.history
        else historyState.history.filter { item ->
            val level = item.stress_level ?: ""
            when (stressFilter) {
                "Low" -> level.contains("Low", ignoreCase = true)
                "Mid" -> level.contains("Mid", ignoreCase = true) || level.contains("Medium", ignoreCase = true)
                "High" -> level.contains("High", ignoreCase = true)
                else -> true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        if (historyState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = PrimaryBlue
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Header
                item {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            "Assessment History",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.Black
                        )
                        Text(
                            "Your stress tracking journey",
                            color = Color(0xFF475569),
                            fontSize = 14.sp
                        )
                    }
                }

                // Summary stats
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SummaryStat("${historyState.totalScans}", "Total Scans", Color(0xFF1A2332), Modifier.weight(1f).clickable { stressFilter = "All" })
                        SummaryStat("${historyState.lowStressCount}", "Low Stress", GreenAccent, Modifier.weight(1f).clickable { stressFilter = if (stressFilter == "Low") "All" else "Low" })
                        SummaryStat("${historyState.avgScore}", "Avg Score", Color(0xFF1A2332), Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Filter chips
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Low", "Mid", "High").forEach { level ->
                            val isActive = stressFilter == level
                            val chipColor = when(level) {
                                "Low" -> GreenAccent
                                "Mid" -> Color(0xFFF59E0B)
                                "High" -> Color(0xFFEF4444)
                                else -> PrimaryBlue
                            }
                            FilterChip(
                                selected = isActive,
                                onClick = { stressFilter = if (isActive && level != "All") "All" else level },
                                label = { Text(level, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipColor.copy(alpha = 0.15f),
                                    selectedLabelColor = chipColor
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = if (isActive) chipColor else Color(0xFFE2E8F0),
                                    selectedBorderColor = chipColor,
                                    enabled = true,
                                    selected = isActive
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Error
                if (historyState.error != null) {
                    item {
                        Card(
                            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    historyState.error!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(onClick = { viewModel.loadHistory() }) {
                                    Text("Retry", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // ─── GRAPH TOGGLE BUTTONS ───
                item {
                    var selectedGraph by remember { mutableStateOf<String?>(null) }

                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GraphToggleButton(
                                label = "📊 Stress",
                                isSelected = selectedGraph == "stress",
                                color = Color(0xFFEF4444),
                                onClick = { selectedGraph = if (selectedGraph == "stress") null else "stress" },
                                modifier = Modifier.weight(1f)
                            )
                            GraphToggleButton(
                                label = "🚶 Steps",
                                isSelected = selectedGraph == "steps",
                                color = Color(0xFF3B82F6),
                                onClick = { selectedGraph = if (selectedGraph == "steps") null else "steps" },
                                modifier = Modifier.weight(1f)
                            )
                            GraphToggleButton(
                                label = "😴 Sleep",
                                isSelected = selectedGraph == "sleep",
                                color = Color(0xFF8B5CF6),
                                onClick = { selectedGraph = if (selectedGraph == "sleep") null else "sleep" },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        when (selectedGraph) {
                            "stress" -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                StressGraphCard(
                                    data = historyState.last7DaysData
                                )
                            }
                            "steps" -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                GraphCard(
                                    title = "🚶 Daily Steps",
                                    subtitle = "Last 7 days average",
                                    data = historyState.last7DaysData,
                                    valueExtractor = { it.steps },
                                    maxValue = 10000f,
                                    lineColor = Color(0xFF3B82F6),
                                    fillColor = Color(0x203B82F6),
                                    unitLabel = "Steps"
                                )
                            }
                            "sleep" -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                GraphCard(
                                    title = "😴 Sleep Hours",
                                    subtitle = "Last 7 days average",
                                    data = historyState.last7DaysData,
                                    valueExtractor = { it.sleep },
                                    maxValue = 12f,
                                    lineColor = Color(0xFF8B5CF6),
                                    fillColor = Color(0x208B5CF6),
                                    unitLabel = "Hours"
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // ─── SCAN HISTORY LIST ───
                item {
                    Text(
                        if (stressFilter == "All") "All Scan Records" else "$stressFilter Stress Records",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (filteredHistory.isEmpty() && historyState.error == null) {
                    item {
                        Card(
                            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.BarChart, null, modifier = Modifier.size(48.dp), tint = Color(0xFFCBD5E1))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (stressFilter == "All") "No assessments yet" else "No $stressFilter stress records",
                                    fontWeight = FontWeight.Medium, color = Color(0xFF475569)
                                )
                                Text("Take your first stress scan to see results here", fontSize = 13.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }

                itemsIndexed(filteredHistory) { index, item ->
                    val scanNumber = historyState.totalScans - index
                    Spacer(modifier = Modifier.height(6.dp))
                    ExpandableHistoryCard(
                        item = item,
                        scanNumber = scanNumber,
                        onDelete = { viewModel.deleteItem(historyState.history.indexOf(item)) },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }

        // Bottom nav is now managed by MainPagerScreen
    }
}

// ──────────────────────────────────────────────
//            EXPANDABLE HISTORY CARD
// ──────────────────────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ExpandableHistoryCard(
    item: HistoryItem,
    scanNumber: Int,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {

    var expanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val score = item.stress_score?.toInt() ?: 0
    val level = item.stress_level ?: "Unknown"

    val bgColor = when {
        level.contains("Low", true) -> GreenAccent
        level.contains("Medium", true) || level.contains("Mid", true) -> MediumStressColor
        level.contains("High", true) -> HighStressColor
        score < 45 -> GreenAccent
        score < 65 -> MediumStressColor
        else -> if (score > 0) HighStressColor else Color(0xFF94A3B8)
    }

    val displayDate = formatTimestamp(item.timestamp)

    // Delete confirmation dialog
    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Scan #$scanNumber?") },
            text = { Text("This will permanently remove this scan record. This action cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = HighStressColor)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { showDeleteDialog = true }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            // Main row
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(bgColor),
                    contentAlignment = Alignment.Center
                ) {
                    // Show level indicator instead of raw prediction value
                    val circleText = when {
                        level.contains("Low", true) -> "↓"
                        level.contains("Mid", true) || level.contains("Medium", true) -> "~"
                        level.contains("High", true) -> "↑"
                        else -> "#$scanNumber"
                    }
                    Text(
                        circleText,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (circleText.length > 2) 13.sp else 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Scan #$scanNumber", fontWeight = FontWeight.SemiBold, color = Color.Black)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(12.dp), tint = Color(0xFF475569))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(displayDate, fontSize = 12.sp, color = Color(0xFF475569))
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.heart_rate?.let { Chip("HR: $it") }
                        item.sleep_hours?.let { Chip("Sleep: ${String.format("%.1f", it)}h") }
                        item.steps?.let { Chip("Steps: $it") }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Stress level badge
                    Text(level, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = bgColor)
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable detail section
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF8FAFC))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Scan Parameters",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DetailRow("Age", item.age?.toString())
                    DetailRow("Gender", item.gender)
                    DetailRow("Work/Life", item.worklife)
                    DetailRow("Eye Blink Rate", item.blink_rate?.let { "$it /min" })
                    DetailRow("Resting Heart Rate", item.heart_rate?.let { "$it BPM" })
                    DetailRow("Post-Exercise HR", item.post_exercise_hr?.let { "$it BPM" })
                    DetailRow("Sleep Duration", item.sleep_hours?.let { "${String.format("%.1f", it)} hours" })
                    DetailRow("Daily Steps", item.steps?.toString())
                    DetailRow("Chest Pain", when (item.chest_pain) { 2 -> "Yes"; 1 -> "Mild"; 0 -> "No"; else -> null })
                    DetailRow("Cholesterol", item.cholesterol?.let { "${String.format("%.0f", it)} mg/dL" })
                    DetailRow("ECG Result", when (item.ecg_result) { 2 -> "Sinus Tachycardia"; 1 -> "Mild Tachycardia"; 0 -> "Normal"; else -> null })
                    DetailRow("Body Temp", item.body_temp?.let { "${String.format("%.1f", it)} °C" })
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 12.sp, color = Color(0xFF64748B))
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
        }
    }
}

// ──────────────────────────────────────────────
//                 GRAPH COMPOSABLES
// ──────────────────────────────────────────────

@Composable
fun GraphToggleButton(
    label: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else Color.White,
            contentColor = if (isSelected) Color.White else color
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp
        ),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GraphCard(
    title: String,
    subtitle: String,
    data: List<DayData>,
    valueExtractor: (DayData) -> Float,
    maxValue: Float,
    lineColor: Color,
    fillColor: Color,
    unitLabel: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, fontSize = 12.sp, color = Color(0xFF94A3B8))

            Spacer(modifier = Modifier.height(16.dp))

            val hasData = data.any { valueExtractor(it) > 0 }

            if (!hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No data yet", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }
            } else {
                LineChart(
                    data = data,
                    valueExtractor = valueExtractor,
                    maxValue = maxValue,
                    lineColor = lineColor,
                    fillColor = fillColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                data.forEach { day ->
                    Text(day.label, fontSize = 10.sp, color = Color(0xFF94A3B8), modifier = Modifier.width(36.dp))
                }
            }
        }
    }
}

@Composable
fun LineChart(
    data: List<DayData>,
    valueExtractor: (DayData) -> Float,
    maxValue: Float,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    val values = data.map { valueExtractor(it) }
    val effectiveMax = maxOf(maxValue, (values.maxOrNull() ?: 0f) * 1.2f)

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 8f

        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2

        val points = values.mapIndexed { index, value ->
            val x = padding + (index.toFloat() / (values.size - 1).coerceAtLeast(1)) * chartWidth
            val y = padding + chartHeight - (value / effectiveMax) * chartHeight
            Offset(x, y.coerceIn(padding, padding + chartHeight))
        }

        if (points.size < 2) return@Canvas

        val fillPath = Path().apply {
            moveTo(points.first().x, padding + chartHeight)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, padding + chartHeight)
            close()
        }
        drawPath(fillPath, color = fillColor)

        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }
        drawPath(linePath, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

        points.forEachIndexed { index, point ->
            if (values[index] > 0) {
                drawCircle(color = lineColor, radius = 5f, center = point)
                drawCircle(color = Color.White, radius = 3f, center = point)
            }
        }

        points.forEachIndexed { index, point ->
            if (values[index] > 0) {
                val text = when {
                    values[index] >= 1000 -> "${(values[index] / 1000).toInt()}k"
                    values[index] >= 10 -> "${values[index].toInt()}"
                    else -> String.format("%.1f", values[index])
                }
                drawContext.canvas.nativeCanvas.drawText(
                    text, point.x, point.y - 12f,
                    android.graphics.Paint().apply {
                        color = lineColor.hashCode()
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isFakeBoldText = true
                    }
                )
            }
        }
    }
}

// ──────────────────────────────────────────────
//     PREMIUM STRESS GRAPH (REDESIGNED)
// ──────────────────────────────────────────────

@Composable
fun StressGraphCard(data: List<DayData>) {
    // stress field now directly contains level: -1=nodata, 0=Low, 1=Mid, 2=High
    val levels = data.map { it.stress }
    val hasData = levels.any { it >= 0f }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) { Text("📊", fontSize = 18.sp) }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Daily Stress Level", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                    Text("Last 7 days", fontSize = 11.sp, color = Color(0xFF94A3B8))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Legend
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StressZoneLegend(Color(0xFF22C55E), "Low (0)")
                StressZoneLegend(Color(0xFFF59E0B), "Medium (1)")
                StressZoneLegend(Color(0xFFEF4444), "High (2)")
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!hasData) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentAlignment = Alignment.Center
                ) { Text("No stress data yet", color = Color(0xFF94A3B8), fontSize = 14.sp) }
            } else {
                val animProgress by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(800, easing = FastOutSlowInEasing),
                    label = "barAnim"
                )

                // Y-axis labels + Bar chart
                Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                    // Y-axis labels
                    Column(
                        modifier = Modifier.width(48.dp).fillMaxHeight().padding(end = 4.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("High", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                        Text("Mid", fontSize = 10.sp, color = Color(0xFFF59E0B), fontWeight = FontWeight.Medium)
                        Text("Low", fontSize = 10.sp, color = Color(0xFF22C55E), fontWeight = FontWeight.Medium)
                        Text("", fontSize = 10.sp) // baseline spacer
                    }

                    // Chart area
                    Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val w = size.width
                        val h = size.height
                        val padTop = 12f
                        val padBottom = 4f
                        val chartH = h - padTop - padBottom
                        val maxLevel = 2f
                        val barCount = levels.size
                        val barWidth = (w / barCount) * 0.55f
                        val gapWidth = (w - barWidth * barCount) / (barCount + 1)

                        // Zone bands
                        val zones = listOf(
                            Triple(0f, 1f, Color(0x0D22C55E)),    // Low zone
                            Triple(1f, 2f, Color(0x0DF59E0B)),    // Mid zone
                            Triple(2f, 3f, Color(0x0DEF4444))     // High zone
                        )
                        zones.forEach { (low, high, zoneColor) ->
                            val yTop = padTop + chartH - (high / (maxLevel + 0.5f)) * chartH
                            val yBot = padTop + chartH - (low / (maxLevel + 0.5f)) * chartH
                            drawRect(color = zoneColor, topLeft = Offset(0f, yTop.coerceAtLeast(0f)), size = Size(w, (yBot - yTop).coerceAtLeast(0f)))
                        }

                        // Gridlines at each level
                        for (lvl in 0..2) {
                            val y = padTop + chartH - (lvl.toFloat() / (maxLevel + 0.5f)) * chartH
                            drawLine(
                                color = Color(0x18000000), start = Offset(0f, y), end = Offset(w, y),
                                strokeWidth = 1f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
                            )
                        }

                        // Draw bars
                        levels.forEachIndexed { i, lvl ->
                            if (lvl < 0) return@forEachIndexed // no data
                            val barVal = (lvl + 0.3f) // offset so bars are visible even at level 0
                            val barH = (barVal / (maxLevel + 0.5f)) * chartH * animProgress
                            val x = gapWidth + i * (barWidth + gapWidth)
                            val y = padTop + chartH - barH

                            val barColor = when (lvl.toInt()) {
                                0 -> Color(0xFF22C55E)
                                1 -> Color(0xFFF59E0B)
                                else -> Color(0xFFEF4444)
                            }

                            drawRoundRect(
                                color = barColor, topLeft = Offset(x, y),
                                size = Size(barWidth, barH.coerceAtLeast(4f)),
                                cornerRadius = CornerRadius(6f, 6f)
                            )

                            // Depth overlay
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.3f), Color.Transparent),
                                    startY = y, endY = y + barH
                                ),
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barH.coerceAtLeast(4f)),
                                cornerRadius = CornerRadius(6f, 6f)
                            )

                            // Level label on top
                            val labelText = when (lvl.toInt()) { 0 -> "Low"; 1 -> "Mid"; else -> "High" }
                            val paint = android.graphics.Paint().apply {
                                color = barColor.hashCode()
                                textSize = 20f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            drawContext.canvas.nativeCanvas.drawText(labelText, x + barWidth / 2, y - 6f, paint)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Day labels
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                data.forEach { day ->
                    Text(day.label, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun StressZoneLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}

// ──────────────────────────────────────────────
//              SUMMARY & CHIP
// ──────────────────────────────────────────────

@Composable
fun SummaryStat(value: String, label: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = valueColor)
            Text(label, color = Color(0xFF475569), fontSize = 11.sp)
        }
    }
}

@Composable
fun Chip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(BackgroundLight)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = Color(0xFF475569))
    }
}

/**
 * Format backend timestamp — handles both epoch millis (numeric string) and ISO date strings.
 */
private fun formatTimestamp(timestamp: String?): String {
    if (timestamp.isNullOrBlank()) return "Unknown"

    val outputFormat = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
    outputFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")

    // Try epoch millis first (the backend may return numeric timestamps)
    try {
        val millis = timestamp.toLongOrNull()
        if (millis != null) {
            return outputFormat.format(Date(millis))
        }
    } catch (_: Exception) {}

    // Try epoch seconds (if value is small for millis)
    try {
        val seconds = timestamp.toDoubleOrNull()
        if (seconds != null && seconds < 9999999999.0) {
            return outputFormat.format(Date((seconds * 1000).toLong()))
        }
    } catch (_: Exception) {}

    // Try date string formats
    return try {
        val inputFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        )

        var date: Date? = null
        for (fmt in inputFormats) {
            try {
                fmt.timeZone = TimeZone.getTimeZone("UTC")
                date = fmt.parse(timestamp)
                if (date != null) break
            } catch (_: Exception) {}
        }

        if (date != null) outputFormat.format(date) else timestamp

    } catch (e: Exception) {
        timestamp
    }
}