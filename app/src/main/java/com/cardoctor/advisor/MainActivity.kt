package com.cardoctor.advisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cardoctor.advisor.ui.DiagnosisViewModel
import com.cardoctor.advisor.ui.advice.AdviceScreen
import com.cardoctor.advisor.ui.detail.DetailScreen
import com.cardoctor.advisor.ui.history.HistoryScreen
import com.cardoctor.advisor.ui.home.HomeScreen
import com.cardoctor.advisor.ui.scan.ScanScreen
import com.cardoctor.advisor.ui.theme.CarDoctorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CarDoctorTheme {
                CarDoctorApp()
            }
        }
    }
}

private enum class Screen(val route: String, val label: String) {
    HOME("home", "홈"),
    SCAN("scan", "자가진단"),
    ADVICE("advice", "정비 권고"),
    DETAIL("detail", "진단 상세"),
    HISTORY("history", "이력"),
}

/** 하단 탭에 노출할 화면. 상세는 부위 선택으로만 들어가므로 제외한다. */
private val TAB_SCREENS = listOf(Screen.HOME, Screen.SCAN, Screen.ADVICE, Screen.HISTORY)

@Composable
private fun CarDoctorApp(viewModel: DiagnosisViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateTab(screen: Screen) {
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TAB_SCREENS.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = { navigateTab(screen) },
                        label = { Text(screen.label, style = MaterialTheme.typography.labelLarge) },
                        icon = {},
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.HOME.route,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Screen.HOME.route) {
                HomeScreen(
                    state = state,
                    onPartClick = { part ->
                        viewModel.selectPart(part)
                        if (state.findingsFor(part).isNotEmpty()) {
                            navController.navigate(Screen.DETAIL.route)
                        }
                    },
                    onFindingClick = { finding ->
                        viewModel.selectFinding(finding)
                        navController.navigate(Screen.DETAIL.route)
                    },
                    onRunScan = { viewModel.runScan() },
                    onOpenAdvice = { navigateTab(Screen.ADVICE) },
                    onOverrideDaylight = viewModel::overrideDaylight,
                    onOverrideWeather = viewModel::overrideWeather,
                    onOverrideRoad = viewModel::overrideRoadType,
                    onClearOverrides = viewModel::clearOverrides,
                )
            }
            composable(Screen.SCAN.route) {
                ScanScreen(
                    state = state,
                    onRunScan = { viewModel.runScan() },
                    onBack = { navigateTab(Screen.HOME) },
                )
            }
            composable(Screen.ADVICE.route) {
                AdviceScreen(state = state, onBack = { navigateTab(Screen.HOME) })
            }
            composable(Screen.DETAIL.route) {
                val finding = state.selectedFinding
                DetailScreen(
                    finding = finding,
                    explanation = finding?.let { state.explanationFor(it) },
                    driving = state.driving,
                    onBack = {
                        viewModel.clearSelection()
                        navigateTab(Screen.HOME)
                    },
                )
            }
            composable(Screen.HISTORY.route) {
                HistoryScreen(
                    records = history,
                    onClear = viewModel::clearHistory,
                    onBack = { navigateTab(Screen.HOME) },
                )
            }
        }
    }
}
