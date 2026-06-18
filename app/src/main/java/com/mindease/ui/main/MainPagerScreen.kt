package com.mindease.ui.main

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mindease.ui.common.PagerBottomNavigationBar
import com.mindease.ui.dashboard.DashboardScreen
import com.mindease.ui.dashboard.DashboardViewModel
import com.mindease.ui.history.HistoryScreen
import com.mindease.ui.history.HistoryViewModel
import com.mindease.ui.profile.ProfileScreen
import com.mindease.ui.profile.ProfileViewModel
import com.mindease.ui.wellness.WellnessTipsScreen
import kotlinx.coroutines.launch

/**
 * Main pager screen wrapping the 4 bottom-nav tabs in a HorizontalPager
 * for smooth swipe navigation.
 *
 * Pages: 0=Home, 1=History, 2=Profile, 3=Tips
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainPagerScreen(
    navController: NavController,
    dashboardViewModel: DashboardViewModel,
    historyViewModel: HistoryViewModel,
    profileViewModel: ProfileViewModel,
    initialPage: Int = 0
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { 4 }
    )
    val scope = rememberCoroutineScope()

    // Refresh history data when swiping to History tab
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) {
            historyViewModel.loadHistory()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Swipeable pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            beyondBoundsPageCount = 1
        ) { page ->
            when (page) {
                0 -> DashboardScreen(navController, dashboardViewModel)
                1 -> HistoryScreen(navController, historyViewModel)
                2 -> ProfileScreen(navController, profileViewModel)
                3 -> WellnessTipsScreen(navController)
            }
        }

        // Bottom nav synced with pager
        PagerBottomNavigationBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedIndex = pagerState.currentPage,
            onIndexChanged = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(
                        page = index,
                        animationSpec = tween(350)
                    )
                }
            }
        )
    }
}
