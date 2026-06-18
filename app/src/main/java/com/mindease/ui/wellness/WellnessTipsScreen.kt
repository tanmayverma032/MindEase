package com.mindease.ui.wellness

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.mindease.ui.navigation.Screen

data class TipItem(val title: String, val description: String, val time: String)
data class TipCategory(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val gradientEnd: Color,
    val tips: List<TipItem>
)

val wellnessCategories = listOf(
    TipCategory(
        "Breathing Exercises", Icons.Default.Air,
        Color(0xFF5B8DEF), Color(0xFF3D6FD9),
        listOf(
            TipItem("4-7-8 Breathing", "Inhale for 4 seconds, hold for 7, exhale slowly for 8 seconds. Repeat 4 cycles.", "5 min"),
            TipItem("Box Breathing", "Inhale → Hold → Exhale → Hold, each for 4 counts. Used by Navy SEALs.", "5 min"),
            TipItem("Diaphragmatic Breathing", "Breathe deeply into your belly, not chest. Place hand on stomach to feel it rise.", "3 min")
        )
    ),
    TipCategory(
        "Meditation", Icons.Default.SelfImprovement,
        Color(0xFF9B59B6), Color(0xFF7C3FA0),
        listOf(
            TipItem("Mindful Meditation", "Sit quietly, focus on your breath. When thoughts arise, gently return to breathing.", "10 min"),
            TipItem("Body Scan", "Progressively relax each body part from toes to head. Notice tension and release it.", "15 min"),
            TipItem("Gratitude Practice", "List 3 things you're grateful for. Feel the emotion of each one deeply.", "5 min")
        )
    ),
    TipCategory(
        "Sleep Hygiene", Icons.Default.Bedtime,
        Color(0xFF3F6FD4), Color(0xFF2D55B0),
        listOf(
            TipItem("Consistent Schedule", "Sleep and wake at the same time daily — even on weekends.", "Daily"),
            TipItem("Screen Detox", "Avoid screens 1 hour before bed. Use night mode if you must.", "Daily"),
            TipItem("Cool & Dark Room", "Keep bedroom at 18-20°C and block all light sources.", "Daily")
        )
    ),
    TipCategory(
        "Relaxation", Icons.Default.Spa,
        Color(0xFFE91E8C), Color(0xFFC0186F),
        listOf(
            TipItem("Calming Sounds", "Listen to nature sounds, rain, or lo-fi music to reduce anxiety.", "15 min"),
            TipItem("Progressive Muscle Relaxation", "Tense each muscle group for 5s, then release. Start from feet up.", "15 min"),
            TipItem("Warm Bath", "A warm bath 1-2 hours before sleep drops core temperature and promotes drowsiness.", "20 min")
        )
    ),
    TipCategory(
        "Physical Wellness", Icons.Default.FitnessCenter,
        Color(0xFF22C55E), Color(0xFF16A34A),
        listOf(
            TipItem("Morning Walk", "A 20-minute morning walk boosts serotonin and regulates your circadian rhythm.", "20 min"),
            TipItem("Stretching", "Gentle stretching reduces muscle tension and improves blood flow.", "10 min"),
            TipItem("Hydration", "Drink at least 8 glasses of water daily. Dehydration worsens anxiety.", "Daily")
        )
    )
)

@Composable
fun WellnessTipsScreen(navController: NavController) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Header
            item {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF334155), Color(0xFFF8FAFC))
                            )
                        )
                        .padding(24.dp)
                        .padding(top = 8.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF4ECDC4).copy(0.2f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Lightbulb, null, tint = Color(0xFF4ECDC4), modifier = Modifier.size(22.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Wellness Tips", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
                                Text("Improve your mental & physical wellness", fontSize = 13.sp, color = Color.White.copy(0.7f))
                            }
                        }
                    }
                }
            }

            // Featured tip card
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(Color(0xFF4ECDC4), Color(0xFF3E8B7A))))
                            .padding(22.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("💡 Today's Tip", color = Color.White.copy(0.85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(4.dp))
                                Text("Take a Breathing Break", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Practice deep breathing for 5 minutes to reset your nervous system.", color = Color.White.copy(0.9f), fontSize = 14.sp, lineHeight = 20.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier.size(56.dp).clip(CircleShape)
                                    .background(Color.White.copy(0.15f)),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Default.Air, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            // Categories
            itemsIndexed(wellnessCategories) { index, category ->
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    // Category header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                                .background(category.color.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(category.icon, null, tint = category.color, modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(10.dp))
                        Text(category.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF1E293B))
                        Spacer(Modifier.weight(1f))
                        Text("${category.tips.size} tips", fontSize = 11.sp, color = Color(0xFF94A3B8))
                    }

                    category.tips.forEachIndexed { tipIndex, tip ->
                        PremiumTipCard(tip = tip, accentColor = category.color, gradientEnd = category.gradientEnd, number = tipIndex + 1)
                        Spacer(Modifier.height(10.dp))
                    }

                    if (index < wellnessCategories.lastIndex) {
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            // Doctor Button
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { navController.navigate(Screen.DoctorsMap.route) },
                    modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF5B8DEF), Color(0xFF3D6FD9))), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalHospital, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Find Nearby Doctors", fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumTipCard(
    tip: TipItem,
    accentColor: Color,
    gradientEnd: Color,
    number: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Number badge
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accentColor, gradientEnd))),
                contentAlignment = Alignment.Center
            ) {
                Text("$number", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(tip.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF1E293B))
                Spacer(Modifier.height(4.dp))
                Text(tip.description, fontSize = 13.sp, color = Color(0xFF64748B), lineHeight = 18.sp)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, tint = accentColor, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tip.time, fontSize = 12.sp, color = accentColor, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}