package com.alarmapp.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.alarmapp.monitor.SleepMonitorService
import com.alarmapp.ui.alarms.AlarmSetupScreen
import com.alarmapp.ui.home.HomeScreen
import com.alarmapp.ui.night.NightScreen
import com.alarmapp.ui.onboarding.OnboardingScreen
import com.alarmapp.ui.report.ReportScreen
import com.alarmapp.ui.settings.SettingsScreen
import com.alarmapp.ui.theme.AlarmappTheme

class MainActivity : ComponentActivity() {

    private var pendingReport by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingReport = reportFrom(intent)

        val prefs = getSharedPreferences("ui", Context.MODE_PRIVATE)
        val activeNight = SleepMonitorService.running.value
        val start = when {
            !prefs.getBoolean("onboarding_done", false) -> "onboarding"
            activeNight != null -> "night/$activeNight"
            else -> "home"
        }

        setContent {
            AlarmappTheme {
                val nav = rememberNavController()

                LaunchedEffect(pendingReport) {
                    pendingReport?.let { id ->
                        nav.navigate("report/$id") { popUpTo("home") { inclusive = false }; launchSingleTop = true }
                        pendingReport = null
                    }
                }

                NavHost(navController = nav, startDestination = start) {
                    composable("onboarding") {
                        OnboardingScreen(onDone = {
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                        })
                    }
                    composable("home") {
                        HomeScreen(
                            onOpenAlarms = { nav.navigate("alarms") },
                            onOpenSettings = { nav.navigate("settings") },
                            onOpenReport = { nav.navigate("report/$it") },
                            onNightStarted = { nav.navigate("night/$it") { popUpTo("home") } },
                        )
                    }
                    composable("alarms") { AlarmSetupScreen(onBack = { nav.popBackStack() }) }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable("night/{sessionId}", arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("sessionId") ?: -1L
                        NightScreen(id, onFinished = {
                            nav.navigate("report/$it") { popUpTo("home") }
                        })
                    }
                    composable("report/{sessionId}", arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) {
                        ReportScreen(onBack = {
                            if (!nav.popBackStack("home", inclusive = false)) nav.navigate("home") { popUpTo(nav.graph.id) { inclusive = true } }
                        })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        reportFrom(intent)?.let { pendingReport = it }
    }

    private fun reportFrom(intent: Intent?): Long? =
        intent?.getLongExtra(EXTRA_REPORT, -1L)?.takeIf { it >= 0 }

    companion object {
        const val EXTRA_REPORT = "report_session"
    }
}
