package com.alarmapp.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
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
import com.alarmapp.ui.alarms.AlarmListScreen
import com.alarmapp.ui.alarms.AlarmSetupScreen
import com.alarmapp.ui.home.HomeScreen
import com.alarmapp.ui.insights.InsightsScreen
import com.alarmapp.ui.night.NightScreen
import com.alarmapp.ui.onboarding.OnboardingScreen
import com.alarmapp.ui.profile.ProfileScreen
import com.alarmapp.ui.profile.QuestionsScreen
import com.alarmapp.ui.report.ReportScreen
import com.alarmapp.ui.settings.SettingsScreen
import com.alarmapp.ui.theme.AlarmappTheme

class MainActivity : ComponentActivity() {

    private var pendingReport by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barras del sistema con íconos oscuros sobre el fondo claro de día.
        enableEdgeToEdge(
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        pendingReport = reportFrom(intent)

        val prefs = getSharedPreferences("ui", Context.MODE_PRIVATE)
        val activeNight = SleepMonitorService.running.value
        val start = when {
            // Primero las preguntas (también a quienes ya usaban la app antes de esta versión).
            !prefs.getBoolean("questions_done", false) && activeNight == null -> "questions"
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
                    composable("questions") {
                        QuestionsScreen(onFinish = {
                            prefs.edit().putBoolean("questions_done", true).apply()
                            val next = if (prefs.getBoolean("onboarding_done", false)) "home" else "onboarding"
                            nav.navigate(next) { popUpTo("questions") { inclusive = true } }
                        })
                    }
                    composable("profile") { ProfileScreen(onBack = { nav.popBackStack() }) }
                    composable("onboarding") {
                        OnboardingScreen(onDone = {
                            prefs.edit().putBoolean("onboarding_done", true).apply()
                            nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                        })
                    }
                    composable("home") {
                        HomeScreen(
                            onOpenAlarms = { nav.navigate("alarms") },
                            onNewAlarm = { nav.navigate("alarm/-1") },
                            onOpenInsights = { nav.navigate("insights") },
                            onOpenSettings = { nav.navigate("settings") },
                            onOpenReport = { nav.navigate("report/$it") },
                            onNightStarted = { nav.navigate("night/$it") { popUpTo("home") } },
                        )
                    }
                    composable("alarms") {
                        AlarmListScreen(
                            onBack = { nav.popBackStack() },
                            onEdit = { id -> nav.navigate("alarm/${id ?: -1}") },
                        )
                    }
                    composable("alarm/{alarmId}", arguments = listOf(navArgument("alarmId") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("alarmId")?.takeIf { it >= 0 }
                        AlarmSetupScreen(alarmId = id, onBack = { nav.popBackStack() })
                    }
                    composable("insights") {
                        InsightsScreen(
                            onBack = { nav.popBackStack() },
                            onOpenNight = { nav.navigate("report/$it") },
                            onOpenProfile = { nav.navigate("profile") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { nav.popBackStack() }, onOpenProfile = { nav.navigate("profile") })
                    }
                    composable("night/{sessionId}", arguments = listOf(navArgument("sessionId") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments?.getLong("sessionId") ?: -1L
                        AlarmappTheme(night = true) {
                            NightScreen(id, onFinished = {
                                nav.navigate("report/$it") { popUpTo("home") }
                            })
                        }
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
