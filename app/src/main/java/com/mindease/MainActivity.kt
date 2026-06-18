package com.mindease

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mindease.ui.theme.MindEaseTheme
import com.mindease.ui.navigation.AppNavigation
import com.mindease.ui.navigation.Screen
import com.mindease.ui.theme.BackgroundLight
import com.mindease.ui.theme.PrimaryBlue
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as MindEaseApp

        setContent {
            MindEaseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundLight
                ) {
                    var startRoute by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(Unit) {
                        val token = app.userPreferences.authTokenFlow.first()
                        val userId = app.userPreferences.userIdFlow.first()
                        startRoute = if (!token.isNullOrBlank() && !userId.isNullOrBlank()) {
                            Screen.Dashboard.route
                        } else {
                            Screen.Welcome.route
                        }
                    }

                    if (startRoute != null) {
                        AppNavigation(startDestination = startRoute!!)
                    } else {
                        // Brief loading while checking token
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = PrimaryBlue)
                        }
                    }
                }
            }
        }
    }
}
