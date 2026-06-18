package com.mindease.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mindease.ui.navigation.Screen

// ─── Pager-synced Bottom Navigation (for swipe screens) ──────

@Composable
fun PagerBottomNavigationBar(
    modifier: Modifier = Modifier,
    selectedIndex: Int,
    onIndexChanged: (Int) -> Unit
) {
    val tabs = listOf(
        TabItem("Home", Icons.Default.Home),
        TabItem("History", Icons.Default.History),
        TabItem("Profile", Icons.Default.Person),
        TabItem("Tips", Icons.Default.Lightbulb)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSelected = selectedIndex == index

                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF5B8DEF).copy(alpha = 0.12f) else Color.Transparent,
                        animationSpec = tween(250),
                        label = "tabBg"
                    )
                    val iconColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF3B6CE7) else Color(0xFF1E293B),
                        animationSpec = tween(250),
                        label = "tabIcon"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(color = bgColor)
                            .clickable { onIndexChanged(index) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                tab.icon, contentDescription = tab.label,
                                tint = iconColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                tab.label,
                                fontSize = 11.sp,
                                color = iconColor,
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.Bold
                                    else androidx.compose.ui.text.font.FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class TabItem(val label: String, val icon: ImageVector)

// ─── Legacy Bottom Navigation (for non-pager screens) ──────

@Composable
fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    currentRoute: String,
    navController: NavController
) {
    NavigationBar(
        modifier = modifier,
        containerColor = Color.White,
        tonalElevation = 6.dp
    ) {

        fun navigate(route: String) {
            navController.navigate(route) {
                popUpTo(Screen.Dashboard.route)
                launchSingleTop = true
            }
        }

        val itemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color(0xFF4A6CF7),
            selectedTextColor = Color(0xFF4A6CF7),
            unselectedIconColor = Color(0xFF6B7280),
            unselectedTextColor = Color(0xFF6B7280),
            indicatorColor = Color(0xFFE8EDFF)
        )

        // 🔹 HOME
        NavigationBarItem(
            selected = currentRoute == Screen.Dashboard.route,
            onClick = { navigate(Screen.Dashboard.route) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") },
            colors = itemColors
        )

        // 🔹 HISTORY
        NavigationBarItem(
            selected = currentRoute == Screen.History.route,
            onClick = { navigate(Screen.History.route) },
            icon = { Icon(Icons.Default.History, contentDescription = "History") },
            label = { Text("History") },
            colors = itemColors
        )

        // 🔹 PROFILE
        NavigationBarItem(
            selected = currentRoute == Screen.Profile.route,
            onClick = { navigate(Screen.Profile.route) },
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile") },
            colors = itemColors
        )

        // 🔹 WELLNESS TIPS ✅ FIXED
        NavigationBarItem(
            selected = currentRoute == Screen.WellnessTips.route,
            onClick = { navigate(Screen.WellnessTips.route) },
            icon = {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = "Wellness Tips"
                )
            },
            label = { Text("Tips") },
            colors = itemColors
        )
    }
}

// ✅ CHATBOT OVERLAY — Premium with Voice
@Composable
fun ChatbotOverlay(
    onDismiss: () -> Unit,
    navController: NavController
) {
    // Background dim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
    )

    // Bottom sheet
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
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
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF4ECDC4), Color(0xFF3E8B7A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Psychology, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("MindEase AI", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                            Text("Health & Wellness Assistant", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White.copy(0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "How can I help you today? Ask me about stress, sleep, heart health, or any wellness topic.",
                    color = Color.White.copy(0.8f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Open Chat button
                Button(
                    onClick = {
                        onDismiss()
                        navController.navigate(Screen.Chatbot.route)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF5B8DEF), Color(0xFF3E6FD4))),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ChatBubble, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Chat", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Talk with AI button
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        navController.navigate("voice_assistant")
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF4ECDC4))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, null, tint = Color(0xFF4ECDC4), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Talk with AI", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = Color(0xFF4ECDC4))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}