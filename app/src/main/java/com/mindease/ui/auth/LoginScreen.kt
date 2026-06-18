package com.mindease.ui.auth

import android.util.Patterns
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
fun LoginScreen(
    navController: NavController,
    viewModel: AuthViewModel
) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState is AuthState.Success) {
            viewModel.resetState()
            navController.navigate(Screen.Dashboard.route) {
                popUpTo(Screen.Login.route) { inclusive = true }
            }
        }
    }

    fun validateEmail(): Boolean {
        return if (email.isBlank()) {
            emailError = "Email is required"
            false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailError = "Enter a valid email address"
            false
        } else {
            emailError = ""
            true
        }
    }

    // ── Animations ──
    val infiniteTransition = rememberInfiniteTransition(label = "login")
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

    // Glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "glow"
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
        // ── Floating Orbs ──
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(50.dp)
        ) {
            val orbs = listOf(
                Triple(0.2f, 0.1f, Color(0x335B8DEF)),
                Triple(0.8f, 0.15f, Color(0x334ECDC4)),
                Triple(0.15f, 0.5f, Color(0x223E8B7A)),
                Triple(0.85f, 0.6f, Color(0x225B8DEF)),
                Triple(0.5f, 0.85f, Color(0x224ECDC4)),
            )
            orbs.forEachIndexed { i, (xf, yf, color) ->
                val cx = size.width * xf + sin(time + i * 1.3f) * 25f
                val cy = size.height * yf + cos(time * 0.7f + i * 0.9f) * 20f
                drawCircle(color = color, radius = 80f + i * 15f, center = Offset(cx, cy))
            }
        }

        // ── Sparkles ──
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (i in 0..7) {
                val a = (0.15f + 0.4f * sin(time * 1.5f + i * 0.8f)).coerceIn(0f, 0.6f)
                val x = size.width * (0.1f + i * 0.11f) + sin(time * 0.8f + i) * 6f
                val y = size.height * (0.1f + i * 0.1f) + cos(time * 0.6f + i) * 5f
                drawCircle(Color.White.copy(alpha = a), radius = 1.5f, center = Offset(x, y))
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
            Spacer(modifier = Modifier.height(40.dp))

            // ── Logo ──
            Box(
                modifier = Modifier
                    .alpha(logoAlpha)
                    .offset(y = logoOffset.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .alpha(glowAlpha * 0.5f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0x554ECDC4), Color.Transparent)
                            )
                        )
                )
                // Orbit ring
                Canvas(modifier = Modifier.size(90.dp).alpha(0.4f)) {
                    drawArc(
                        color = Color(0xFF4ECDC4),
                        startAngle = time * 57.3f,
                        sweepAngle = 180f,
                        useCenter = false,
                        style = Stroke(width = 1.5f, cap = StrokeCap.Round)
                    )
                }
                // Core
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF4ECDC4), Color(0xFF3E8B7A), Color(0xFF2D6B5E)),
                                center = Offset(80f, 60f)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Welcome Back",
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
                text = "Sign in to continue your wellness journey",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xAAFFFFFF),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .alpha(logoAlpha)
                    .offset(y = logoOffset.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Email field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Email Address",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xCCFFFFFF)
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                if (emailError.isNotEmpty()) emailError = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("your.email@example.com", color = Color(0x66FFFFFF))
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF4ECDC4))
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) }
                            ),
                            singleLine = true,
                            isError = emailError.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4ECDC4),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                errorBorderColor = Color(0xFFEF4444),
                                errorTextColor = Color.White,
                                cursorColor = Color(0xFF4ECDC4)
                            )
                        )
                        if (emailError.isNotEmpty()) {
                            Text(emailError, color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }

                    // Password field
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Password",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color(0xCCFFFFFF)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Enter your password", color = Color(0x66FFFFFF))
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF4ECDC4))
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password",
                                        tint = Color(0x99FFFFFF)
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
                                    if (validateEmail() && password.isNotBlank()) {
                                        viewModel.login(email, password)
                                    }
                                }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF4ECDC4),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.White.copy(alpha = 0.06f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF4ECDC4)
                            )
                        )
                    }

                    // Forgot password
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            "Forgot Password?",
                            color = Color(0xFF4ECDC4),
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }

                    // Sign In button — gradient
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (validateEmail() && password.isNotBlank()) {
                                viewModel.login(email, password)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        enabled = uiState !is AuthState.Loading && email.isNotBlank() && password.isNotBlank(),
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
                                    if (uiState is AuthState.Loading || email.isBlank() || password.isBlank())
                                        Brush.linearGradient(listOf(Color(0xFF4ECDC4).copy(0.4f), Color(0xFF3E8B7A).copy(0.4f)))
                                    else
                                        Brush.linearGradient(listOf(Color(0xFF4ECDC4), Color(0xFF3E8B7A))),
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState is AuthState.Loading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    "Sign In",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // Error banner
            if (uiState is AuthState.Error) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (uiState as AuthState.Error).message,
                            color = Color(0xFFEF4444),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(footerAlpha),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Don't have an account? ", color = Color(0x99FFFFFF))
                Text(
                    "Sign Up",
                    color = Color(0xFF4ECDC4),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        navController.navigate(Screen.Signup.route)
                    }
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}
