package com.cardoctor.advisor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.cardoctor.advisor.ui.common.color
import com.cardoctor.advisor.ui.detail.DetailScreen
import com.cardoctor.advisor.ui.history.HistoryScreen
import com.cardoctor.advisor.ui.home.HomeScreen
import com.cardoctor.advisor.ui.scan.ScanScreen
import com.cardoctor.advisor.ui.theme.CarDoctorTheme

class MainActivity : ComponentActivity() {

    /**
     * 단계적 알림(확장 아이디어 A)에 필요한 권한.
     * API 33+ 는 런타임 권한이며, 거부돼도 앱은 그대로 동작한다 — 알림만 안 뜬다.
     */
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 결과와 무관하게 진행 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            CarDoctorTheme {
                CarDoctorApp()
            }
        }
    }
}

private enum class Screen(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "홈", Icons.Rounded.Home),
    SCAN("scan", "자가진단", Icons.Rounded.TaskAlt),
    ADVICE("advice", "정비 권고", Icons.Rounded.Build),
    DETAIL("detail", "진단 상세", Icons.Rounded.Home),
    HISTORY("history", "이력", Icons.Rounded.History),
}

/** 좌측 레일에 노출할 화면. 상세는 부위 선택으로만 들어가므로 제외한다. */
private val TAB_SCREENS = listOf(Screen.HOME, Screen.SCAN, Screen.ADVICE, Screen.HISTORY)

@Composable
private fun CarDoctorApp(viewModel: DiagnosisViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    fun navigateTab(screen: Screen) {
        // 상세는 홈 위에 쌓인다. 홈으로 갈 때는 저장된 탭 상태를 복원하지 말고 상세를 걷어내야
        // 한다 — restoreState 가 상세까지 되살려 홈으로 돌아가지 못하는 문제가 있었다.
        if (screen == Screen.HOME && navController.popBackStack(Screen.HOME.route, inclusive = false)) {
            return
        }
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            // ── 좌측 내비게이션 레일 — 가로 IVI 화면이라 하단 탭보다 운전석에서 손이 덜 간다 ──
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxHeight().width(124.dp),
                header = { RailHeader() },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    TAB_SCREENS.forEach { screen ->
                        val route = currentDestination?.route
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true ||
                            (screen == Screen.HOME && route == Screen.DETAIL.route)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTab(screen) },
                            label = { Text(screen.label, style = MaterialTheme.typography.labelMedium) },
                            icon = {
                                val count = if (screen == Screen.ADVICE) state.findings.size else 0
                                BadgedBox(badge = {
                                    if (count > 0) {
                                        Badge(containerColor = state.worstLevel.color()) { Text("$count") }
                                    }
                                }) {
                                    Icon(screen.icon, contentDescription = null, modifier = Modifier.size(28.dp))
                                }
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            NavHost(
                navController = navController,
                startDestination = Screen.HOME.route,
                modifier = Modifier.weight(1f).fillMaxHeight(),
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
                    AdviceScreen(
                        state = state,
                        onExportReport = viewModel::exportReport,
                        onBack = { navigateTab(Screen.HOME) },
                    )
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
}

@Composable
private fun RailHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 24.dp, bottom = 32.dp),
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MedicalServices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("카닥터 AI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
