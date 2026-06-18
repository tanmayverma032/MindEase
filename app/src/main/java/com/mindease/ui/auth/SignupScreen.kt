package com.mindease.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mindease.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun SignupScreen(
    navController: NavController,
    viewModel: AuthViewModel
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is AuthState.Success) {
            viewModel.resetState()
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Signup.route) { inclusive = true }
            }
        } else if (uiState is AuthState.OtpSent) {
            viewModel.resetState()
            // Navigate to Verification screen, passing the email
            navController.navigate("verify_email/${email}")
        }
    }

    // ── Animations ──
    val infiniteTransition = rememberInfiniteTransition(label = "signup")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // Entrance animations
    var showLogo by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var showFooter by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        showLogo = true
        delay(300)
        showForm = true
        delay(200)
        showFooter = true
    }

    val logoAlpha by animateFloatAsState(
        targetValue = if (showLogo) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing), label = "logoAlpha"
    )
    val logoOffset by animateFloatAsState(
        targetValue = if (showLogo) 0f else 30f,
        animationSpec = tween(700, easing = FastOutSlowInEasing), label = "logoOffset"
    )
    val formAlpha by animateFloatAsState(
        targetValue = if (showForm) 1f else 0f,
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "formAlpha"
    )
    val formOffset by animateFloatAsState(
        targetValue = if (showForm) 0f else 40f,
        animationSpec = tween(600, easing = FastOutSlowInEasing), label = "formOffset"
    )
    val footerAlpha by animateFloatAsState(
        targetValue = if (showFooter) 1f else 0f,
        animationSpec = tween(500), label = "footerAlpha"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
    )

    // Password strength
    val passwordStrength = remember(password) {
        when {
            password.length < 4 -> 0
            password.length < 6 -> 1
            password.length < 8 -> 2
            password.any { it.isUpperCase() } && password.any { it.isDigit() } -> 4
            else -> 3
        }
    }

    val strengthColor = when (passwordStrength) {
        0 -> Color.Transparent
        1 -> Color(0xFFEF4444)
        2 -> Color(0xFFF59E0B)
        3 -> Color(0xFF4ECDC4)
        4 -> Color(0xFF22C55E)
        else -> Color.Transparent
    }

    val strengthLabel = when (passwordStrength) {
        1 -> "Weak"
        2 -> "Fair"
        3 -> "Good"
        4 -> "Strong"
        else -> ""
    }

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
        // ── Floating Orbs ──
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(50.dp)
        ) {
            val orbs = listOf(
                Triple(0.8f, 0.08f, Color(0x335B8DEF)),
                Triple(0.2f, 0.15f, Color(0x334ECDC4)),
                Triple(0.85f, 0.45f, Color(0x223E8B7A)),
                Triple(0.1f, 0.55f, Color(0x225B8DEF)),
                Triple(0.6f, 0.85f, Color(0x224ECDC4)),
            )
            orbs.forEachIndexed { i, (xf, yf, color) ->
                val cx = size.width * xf + sin(time + i * 1.5f) * 20f
                val cy = size.height * yf + cos(time * 0.8f + i * 1.1f) * 18f
                drawCircle(color = color, radius = 75f + i * 12f, center = Offset(cx, cy))
            }
        }

        // ── Sparkles ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0..9) {
                val a = (0.12f + 0.35f * sin(time * 1.4f + i * 0.7f)).coerceIn(0f, 0.5f)
                val x = size.width * (0.08f + i * 0.09f) + sin(time * 0.7f + i) * 5f
                val y = size.height * (0.08f + i * 0.09f) + cos(time * 0.5f + i) * 4f
                drawCircle(Color.White.copy(alpha = a), radius = 1.3f, center = Offset(x, y))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // ── Logo ──
            Box(
                modifier = Modifier
                    .alpha(logoAlpha)
                    .offset(y = logoOffset.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .alpha(glowAlpha * 0.5f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0x555B8DEF), Color.Transparent)
                            )
                        )
                )
                Canvas(modifier = Modifier.size(80.dp).alpha(0.35f)) {
                    drawArc(
                        color = Color(0xFF5B8DEF),
                        startAngle = -time * 57.3f + 45f,
                        sweepAngle = 200f,
                        useCenter = false,
                        style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF5B8DEF), Color(0xFF3E6FD4), Color(0xFF2D5AC0)),
                                center = Offset(70f, 50f)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                "Create Account",
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp,
                color = Color.White,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .alpha(logoAlpha)
                    .offset(y = logoOffset.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                "Join MindEase and start your\njourney towards better mental health",
                color = Color(0xAAFFFFFF),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                modifier = Modifier
                    .alpha(logoAlpha)
                    .offset(y = logoOffset.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // ── Glassmorphic Form Card ──
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(formAlpha)
                    .offset(y = formOffset.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.08f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Full Name
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Full Name", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xCCFFFFFF))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("John Doe", color = Color(0x66FFFFFF)) },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = Color(0xFF5B8DEF)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF5B8DEF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF5B8DEF)
                            )
                        )
                    }

                    // Email
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Email Address", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xCCFFFFFF))
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("your.email@example.com", color = Color(0x66FFFFFF)) },
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = Color(0xFF5B8DEF)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF5B8DEF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF5B8DEF)
                            )
                        )
                    }

                    // Password
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Password", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xCCFFFFFF))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Create a password", color = Color(0x66FFFFFF)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF5B8DEF)) },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        null, tint = Color(0x99FFFFFF)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF5B8DEF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF5B8DEF)
                            )
                        )
                        // Password strength indicator
                        if (password.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                repeat(4) { i ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(
                                                if (i < passwordStrength) strengthColor
                                                else Color.White.copy(alpha = 0.1f)
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(strengthLabel, fontSize = 11.sp, color = strengthColor, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Confirm Password
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Confirm Password", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color(0xCCFFFFFF))
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Repeat your password", color = Color(0x66FFFFFF)) },
                            leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF5B8DEF)) },
                            trailingIcon = {
                                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                    Icon(
                                        if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        null, tint = Color(0x99FFFFFF)
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF5B8DEF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                errorBorderColor = Color(0xFFEF4444),
                                errorTextColor = Color.White,
                                cursorColor = Color(0xFF5B8DEF)
                            )
                        )
                        if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                            Text("Passwords do not match", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // Continue button — gradient
                    Button(
                        onClick = { viewModel.signup(name, email, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = uiState !is AuthState.Loading &&
                                name.isNotBlank() &&
                                email.isNotBlank() &&
                                password.isNotBlank() &&
                                password == confirmPassword,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (uiState is AuthState.Loading || name.isBlank() || email.isBlank() || password.isBlank() || password != confirmPassword)
                                        Brush.linearGradient(listOf(Color(0xFF5B8DEF).copy(0.4f), Color(0xFF3E6FD4).copy(0.4f)))
                                    else
                                        Brush.linearGradient(listOf(Color(0xFF5B8DEF), Color(0xFF3E6FD4))),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState is AuthState.Loading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            } else {
                                Text("Continue", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White, letterSpacing = 0.5.sp)
                            }
                        }
                    }
                }
            }

            if (uiState is AuthState.Error) {
                Spacer(modifier = Modifier.height(14.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Email, contentDescription = "Error", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            (uiState as AuthState.Error).message,
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.alpha(footerAlpha)
            ) {
                Text("Already have an account? ", color = Color(0x99FFFFFF), fontSize = 15.sp)
                Text(
                    "Sign In",
                    color = Color(0xFF4ECDC4),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clickable { navController.popBackStack() }
                        .padding(4.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
