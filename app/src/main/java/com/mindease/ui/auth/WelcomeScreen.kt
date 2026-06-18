package com.mindease.ui.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mindease.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlin.math.*

// ─── Orb Data ─────────────────────────────────────────────
private data class FloatingOrb(
    val xFraction: Float,   // 0..1 base X on screen
    val yFraction: Float,   // 0..1 base Y on screen
    val radius: Float,
    val color: Color,
    val speedX: Float,      // amplitude of horizontal float
    val speedY: Float,      // amplitude of vertical float
    val phase: Float        // offset so orbs don't move in sync
)

private val orbs = listOf(
    FloatingOrb(0.15f, 0.12f, 90f,  Color(0x335B8DEF), 30f, 25f, 0f),
    FloatingOrb(0.82f, 0.08f, 60f,  Color(0x334ECDC4), 20f, 35f, 1.2f),
    FloatingOrb(0.70f, 0.30f, 110f, Color(0x223E8B7A), 25f, 20f, 2.5f),
    FloatingOrb(0.10f, 0.40f, 70f,  Color(0x335B8DEF), 35f, 15f, 0.8f),
    FloatingOrb(0.88f, 0.55f, 80f,  Color(0x224ECDC4), 18f, 30f, 3.7f),
    FloatingOrb(0.30f, 0.70f, 100f, Color(0x223E8B7A), 22f, 28f, 1.9f),
    FloatingOrb(0.60f, 0.80f, 65f,  Color(0x335B8DEF), 28f, 22f, 4.2f),
    FloatingOrb(0.20f, 0.90f, 50f,  Color(0x334ECDC4), 15f, 32f, 2.8f),
)

@Composable
fun WelcomeScreen(navController: NavController) {

    // ── Infinite animation driver ──
    val infiniteTransition = rememberInfiniteTransition(label = "welcome")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // ── Glow pulse for the brain icon ──
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // ── Entrance animations ──
    var showContent by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
        delay(500)
        showButtons = true
    }

    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "contentAlpha"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (showContent) 0f else 40f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "contentOffset"
    )
    val buttonAlpha by animateFloatAsState(
        targetValue = if (showButtons) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "buttonAlpha"
    )
    val buttonOffset by animateFloatAsState(
        targetValue = if (showButtons) 0f else 30f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "buttonOffset"
    )

    // ── Ring rotation ──
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B1428),
                        Color(0xFF0F1E3D),
                        Color(0xFF132B50),
                        Color(0xFF0B1428)
                    )
                )
            )
    ) {
        // ── Floating Orbs (background) ──
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(40.dp)
        ) {
            orbs.forEach { orb ->
                val cx = size.width * orb.xFraction + sin(time + orb.phase) * orb.speedX
                val cy = size.height * orb.yFraction + cos(time * 0.7f + orb.phase) * orb.speedY
                drawCircle(
                    color = orb.color,
                    radius = orb.radius,
                    center = Offset(cx, cy)
                )
            }
        }

        // ── Particle sparkles ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawParticles(time)
        }

        // ── Main content ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.15f))

            // ── 3D Brain / Mind Icon ──
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .alpha(contentAlpha),
                contentAlignment = Alignment.Center
            ) {
                // Outer glow ring
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(glowScale)
                        .alpha(glowAlpha * 0.5f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x554ECDC4),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Rotating orbit rings
                Canvas(
                    modifier = Modifier
                        .size(160.dp)
                        .alpha(0.4f)
                ) {
                    val centerX = size.width / 2
                    val centerY = size.height / 2
                    val ringRadius = size.width / 2 - 8f

                    // Ring 1 (tilted ellipse)
                    drawArc(
                        color = Color(0xFF4ECDC4),
                        startAngle = ringRotation,
                        sweepAngle = 200f,
                        useCenter = false,
                        topLeft = Offset(centerX - ringRadius, centerY - ringRadius * 0.4f),
                        size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 0.8f),
                        style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                    )

                    // Ring 2 (opposite tilt)
                    drawArc(
                        color = Color(0xFF5B8DEF),
                        startAngle = -ringRotation + 90f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(centerX - ringRadius * 0.4f, centerY - ringRadius),
                        size = androidx.compose.ui.geometry.Size(ringRadius * 0.8f, ringRadius * 2),
                        style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                    )

                    // Ring 3 (wider)
                    drawArc(
                        color = Color(0xFF3E8B7A),
                        startAngle = ringRotation * 0.5f + 45f,
                        sweepAngle = 160f,
                        useCenter = false,
                        topLeft = Offset(centerX - ringRadius * 0.7f, centerY - ringRadius * 0.7f),
                        size = androidx.compose.ui.geometry.Size(ringRadius * 1.4f, ringRadius * 1.4f),
                        style = Stroke(width = 1f, cap = StrokeCap.Round)
                    )
                }

                // Inner glow
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(glowScale * 0.95f)
                        .alpha(glowAlpha)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x664ECDC4),
                                    Color(0x225B8DEF),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Core sphere
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF4ECDC4),
                                    Color(0xFF3E8B7A),
                                    Color(0xFF2D6B5E)
                                ),
                                center = Offset(80f, 60f)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Brain-like neural pattern drawn on canvas
                    Canvas(modifier = Modifier.size(52.dp)) {
                        drawBrainPattern(this)
                    }
                }

                // Floating dots (orbiting particles)
                Canvas(modifier = Modifier.size(160.dp)) {
                    val cx = size.width / 2
                    val cy = size.height / 2

                    for (i in 0..5) {
                        val angle = ringRotation * (if (i % 2 == 0) 1f else -0.7f) + i * 60f
                        val rad = (size.width / 2 - 6f) * (0.7f + 0.3f * sin(i * 1.3f))
                        val dx = cx + rad * cos(Math.toRadians(angle.toDouble())).toFloat()
                        val dy = cy + rad * sin(Math.toRadians(angle.toDouble())).toFloat() * 0.5f

                        drawCircle(
                            color = if (i % 2 == 0) Color(0xBB4ECDC4) else Color(0xBB5B8DEF),
                            radius = 3f + sin(time + i) * 1.5f,
                            center = Offset(dx, dy)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            // ── Title ──
            Text(
                text = "MindEase",
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp,
                color = Color.White,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .alpha(contentAlpha)
                    .offset(y = contentOffset.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Your AI-powered companion for\nstress measurement & mental wellbeing",
                color = Color(0xAAFFFFFF),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                modifier = Modifier
                    .alpha(contentAlpha)
                    .offset(y = contentOffset.dp)
            )

            Spacer(modifier = Modifier.weight(0.12f))

            // ── Feature pills ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(contentAlpha)
                    .offset(y = contentOffset.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FeaturePill("🧠", "AI Analysis")
                FeaturePill("💓", "Heart Rate")
                FeaturePill("😌", "Wellness")
            }

            Spacer(modifier = Modifier.weight(0.08f))

            // ── Buttons ──
            // Sign In — gradient filled button
            Button(
                onClick = { navController.navigate(Screen.Login.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .alpha(buttonAlpha)
                    .offset(y = buttonOffset.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF4ECDC4), Color(0xFF3E8B7A))
                            ),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Sign In",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Create Account — frosted outlined button
            OutlinedButton(
                onClick = { navController.navigate(Screen.Signup.route) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .alpha(buttonAlpha)
                    .offset(y = buttonOffset.dp),
                shape = RoundedCornerShape(16.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4ECDC4).copy(alpha = 0.6f),
                            Color(0xFF5B8DEF).copy(alpha = 0.6f)
                        )
                    )
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White.copy(alpha = 0.06f),
                    contentColor = Color.White
                )
            ) {
                Text(
                    "Create Account",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.weight(0.1f))
        }
    }
}

// ── Feature Pill ──────────────────────────────────────────
@Composable
private fun FeaturePill(emoji: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            color = Color(0xCCFFFFFF),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ── Draw brain-like neural pattern ───────────────────────
private fun drawBrainPattern(scope: DrawScope) {
    with(scope) {
        val cx = size.width / 2
        val cy = size.height / 2
        val white = Color.White.copy(alpha = 0.9f)

        // Central node
        drawCircle(white, radius = 4f, center = Offset(cx, cy))

        // Neural branches
        val nodes = listOf(
            Offset(cx - 16f, cy - 14f),
            Offset(cx + 14f, cy - 12f),
            Offset(cx - 10f, cy + 16f),
            Offset(cx + 16f, cy + 10f),
            Offset(cx - 18f, cy + 2f),
            Offset(cx + 6f, cy - 20f),
        )

        nodes.forEach { node ->
            // Line from center
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(cx, cy),
                end = node,
                strokeWidth = 1.2f,
                cap = StrokeCap.Round
            )
            // Endpoint dot
            drawCircle(white, radius = 2.5f, center = node)
        }

        // Outer connections
        val outerNodes = listOf(
            Offset(cx - 22f, cy - 22f),
            Offset(cx + 22f, cy - 20f),
            Offset(cx - 24f, cy + 18f),
            Offset(cx + 24f, cy + 18f),
            Offset(cx, cy - 24f),
            Offset(cx, cy + 24f),
        )

        for (i in outerNodes.indices) {
            val from = nodes[i % nodes.size]
            val to = outerNodes[i]
            drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = from,
                end = to,
                strokeWidth = 0.8f,
                cap = StrokeCap.Round
            )
            drawCircle(Color.White.copy(alpha = 0.7f), radius = 1.8f, center = to)
        }
    }
}

// ── Draw particle sparkles ───────────────────────────────
private fun DrawScope.drawParticles(time: Float) {
    val particles = listOf(
        Triple(0.2f, 0.15f, 0.3f),
        Triple(0.8f, 0.12f, 0.7f),
        Triple(0.5f, 0.25f, 1.5f),
        Triple(0.15f, 0.55f, 2.1f),
        Triple(0.85f, 0.45f, 0.9f),
        Triple(0.4f, 0.65f, 3.0f),
        Triple(0.7f, 0.75f, 1.8f),
        Triple(0.3f, 0.85f, 2.5f),
        Triple(0.9f, 0.90f, 0.4f),
        Triple(0.55f, 0.50f, 1.2f),
        Triple(0.65f, 0.15f, 3.5f),
        Triple(0.25f, 0.35f, 2.8f),
    )

    particles.forEach { (xf, yf, phase) ->
        val alpha = (0.2f + 0.5f * sin(time * 1.5f + phase)).coerceIn(0f, 0.7f)
        val x = size.width * xf + sin(time * 0.8f + phase) * 8f
        val y = size.height * yf + cos(time * 0.6f + phase) * 6f

        drawCircle(
            color = Color.White.copy(alpha = alpha),
            radius = 1.5f + sin(time + phase) * 0.8f,
            center = Offset(x, y)
        )
    }
}
