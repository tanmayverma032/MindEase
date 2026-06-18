package com.mindease.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument

import com.mindease.MindEaseApp

import com.mindease.data.HealthConnectManager
import com.mindease.data.StepManager
import com.mindease.data.SleepPatternRepository

// Auth
import com.mindease.ui.auth.*

// Dashboard
import com.mindease.ui.dashboard.*

// Chatbot
import com.mindease.ui.chatbot.*

// Other screens
import com.mindease.ui.doctors.DoctorsMapScreen
import com.mindease.ui.history.HistoryViewModel
import com.mindease.ui.history.HistoryViewModelFactory
import com.mindease.ui.main.MainPagerScreen
import com.mindease.ui.profile.*
import com.mindease.ui.scan.*

// Smooth transition specs
private val enterTransition: EnterTransition =
    fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
    slideInHorizontally(
        initialOffsetX = { fullWidth -> (fullWidth * 0.08f).toInt() },
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )

private val exitTransition: ExitTransition =
    fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() },
        animationSpec = tween(250, easing = FastOutSlowInEasing)
    )

private val popEnterTransition: EnterTransition =
    fadeIn(animationSpec = tween(300, easing = FastOutSlowInEasing)) +
    slideInHorizontally(
        initialOffsetX = { fullWidth -> -(fullWidth * 0.08f).toInt() },
        animationSpec = tween(300, easing = FastOutSlowInEasing)
    )

private val popExitTransition: ExitTransition =
    fadeOut(animationSpec = tween(250, easing = FastOutSlowInEasing)) +
    slideOutHorizontally(
        targetOffsetX = { fullWidth -> (fullWidth * 0.08f).toInt() },
        animationSpec = tween(250, easing = FastOutSlowInEasing)
    )

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Welcome.route
) {
    val context = LocalContext.current
    val app = context.applicationContext as MindEaseApp

    val healthManager = remember { HealthConnectManager(context) }
    val stepManager = remember { StepManager(context) }
    val sleepPatternRepo = remember { app.sleepPatternRepository }

    // ViewModels
    val authViewModel: AuthViewModel = viewModel(
        factory = AuthViewModelFactory(app.repository)
    )

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(
            app.repository,
            app.userPreferences,
            healthManager,
            stepManager,
            sleepPatternRepo
        )
    )

    val profileViewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModelFactory(app.repository, app.userPreferences)
    )

    val chatbotViewModel: ChatbotViewModel = viewModel(
        factory = ChatbotViewModelFactory(app.repository)
    )

    val cameraScanViewModel: CameraScanViewModel = viewModel(
        factory = CameraScanViewModelFactory(app.repository)
    )

    val assessmentViewModel: AssessmentViewModel = viewModel(
        factory = AssessmentViewModelFactory(app.repository, app.userPreferences, healthManager, stepManager, sleepPatternRepo)
    )

    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(app.repository, app.userPreferences)
    )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { enterTransition },
        exitTransition = { exitTransition },
        popEnterTransition = { popEnterTransition },
        popExitTransition = { popExitTransition }
    ) {

        // 🔹 Auth
        composable(Screen.Welcome.route) {
            WelcomeScreen(navController)
        }

        composable(Screen.Login.route) {
            LoginScreen(navController, authViewModel)
        }

        composable(Screen.Signup.route) {
            SignupScreen(navController, authViewModel)
        }

        composable(
            route = "verify_email/{email}",
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email") ?: ""
            VerifyEmailScreen(email, navController, authViewModel)
        }

        // 🔹 Main — Swipeable Pager containing all 4 tabs
        composable(
            route = Screen.Dashboard.route,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            MainPagerScreen(
                navController = navController,
                dashboardViewModel = dashboardViewModel,
                historyViewModel = historyViewModel,
                profileViewModel = profileViewModel,
                initialPage = 0
            )
        }

        // Keep individual routes for deep-link / direct navigation
        composable(
            route = Screen.History.route,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            MainPagerScreen(
                navController = navController,
                dashboardViewModel = dashboardViewModel,
                historyViewModel = historyViewModel,
                profileViewModel = profileViewModel,
                initialPage = 1
            )
        }

        composable(
            route = Screen.Profile.route,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            MainPagerScreen(
                navController = navController,
                dashboardViewModel = dashboardViewModel,
                historyViewModel = historyViewModel,
                profileViewModel = profileViewModel,
                initialPage = 2
            )
        }

        composable(
            route = Screen.WellnessTips.route,
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            MainPagerScreen(
                navController = navController,
                dashboardViewModel = dashboardViewModel,
                historyViewModel = historyViewModel,
                profileViewModel = profileViewModel,
                initialPage = 3
            )
        }

        // 🔹 Scan
        composable(
            route = Screen.CameraScan.route,
            arguments = listOf(navArgument("scanType") { type = NavType.StringType })
        ) { backStackEntry ->
            val scanType = backStackEntry.arguments?.getString("scanType") ?: "blink"
            CameraScanScreen(navController, cameraScanViewModel, scanType)
        }

        composable(Screen.Assessment.route) {
            AssessmentScreen(navController, assessmentViewModel)
        }

        // 🔹 Chatbot
        composable(Screen.Chat.route) {
            ChatbotScreen(navController, chatbotViewModel)
        }

        composable(Screen.Chatbot.route) {
            ChatbotScreen(navController, chatbotViewModel)
        }

        // Voice-only AI assistant
        composable("voice_assistant") {
            VoiceAssistantScreen(navController, chatbotViewModel)
        }

        // 🔹 Doctors Map
        composable(Screen.DoctorsMap.route) {
            DoctorsMapScreen(navController)
        }
    }
}