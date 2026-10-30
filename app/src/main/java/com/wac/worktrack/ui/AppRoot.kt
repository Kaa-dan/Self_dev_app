package com.wac.worktrack.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.wac.worktrack.ui.screens.DashboardScreen
import com.wac.worktrack.ui.screens.FocusScreen
import com.wac.worktrack.ui.screens.GoalsScreen
import com.wac.worktrack.ui.screens.HabitsScreen
import com.wac.worktrack.ui.screens.MoneyScreen
import com.wac.worktrack.ui.screens.MoneyTab

private val tabs = listOf(
    NavItem("Home", Icons.Default.Home),
    NavItem("Money", Icons.Default.AccountBalanceWallet),
    NavItem("Habits", Icons.Default.LocalFireDepartment),
    NavItem("Goals", Icons.Default.Flag),
    NavItem("Focus", Icons.Default.Shield),
)

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var moneyTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { FloatingNavBar(tabs, tab) { tab = it } },
    ) { padding ->
        AnimatedContent(
            tab,
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            transitionSpec = { (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.98f)) togetherWith fadeOut(tween(120)) },
            label = "tab",
        ) { current ->
            val modifier = Modifier.fillMaxSize()
            when (current) {
                0 -> DashboardScreen(
                    modifier,
                    openMoney = { moneyTab = it.ordinal; tab = 1 },
                    openHabits = { tab = 2 },
                    openGoals = { tab = 3 },
                )
                1 -> MoneyScreen(modifier, MoneyTab.entries[moneyTab]) { moneyTab = it.ordinal }
                2 -> HabitsScreen(modifier)
                3 -> GoalsScreen(modifier)
                else -> FocusScreen(modifier)
            }
        }
    }
}
