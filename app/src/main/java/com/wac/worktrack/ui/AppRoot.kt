package com.wac.worktrack.ui

import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.wac.worktrack.ui.screens.DashboardScreen
import com.wac.worktrack.ui.screens.GoalsScreen
import com.wac.worktrack.ui.screens.HabitsScreen
import com.wac.worktrack.ui.screens.MoneyScreen
import com.wac.worktrack.ui.screens.MoneyTab

private enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Dashboard", Icons.Default.Dashboard),
    Money("Money", Icons.Default.AccountBalanceWallet),
    Habits("Habits", Icons.Default.TaskAlt),
    Goals("Goals", Icons.Default.Flag),
}

@Composable
fun AppRoot() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var moneyTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it.ordinal,
                        onClick = { tab = it.ordinal },
                        icon = { Icon(it.icon, null) },
                        label = { Text(it.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
        when (Tab.entries[tab]) {
            Tab.Dashboard -> DashboardScreen(
                modifier,
                openMoney = { moneyTab = it.ordinal; tab = Tab.Money.ordinal },
                openHabits = { tab = Tab.Habits.ordinal },
                openGoals = { tab = Tab.Goals.ordinal },
            )
            Tab.Money -> MoneyScreen(modifier, MoneyTab.entries[moneyTab]) { moneyTab = it.ordinal }
            Tab.Habits -> HabitsScreen(modifier)
            Tab.Goals -> GoalsScreen(modifier)
        }
    }
}
