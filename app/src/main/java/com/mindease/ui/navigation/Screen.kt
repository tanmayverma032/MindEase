package com.mindease.ui.navigation

sealed class Screen(val route: String) {

    // Auth
    object Welcome : Screen("welcome")
    object Login : Screen("login")
    object Signup : Screen("signup")

    // Main Screens
    object Dashboard : Screen("dashboard")
    object History : Screen("history")
    object Profile : Screen("profile")
    object WellnessTips : Screen("wellness_tips")

    // Scan / Assessment
    object CameraScan : Screen("camera_scan/{scanType}") {
        fun createRoute(scanType: String): String {
            return "camera_scan/$scanType"
        }
    }
    object Assessment : Screen("assessment")



    // Doctors / Map
    object DoctorsMap : Screen("doctors_map")

    // ✅ ADD THESE (FIXES YOUR ERROR)
    object Chat : Screen("chat")
    object Chatbot : Screen("chatbot")
}