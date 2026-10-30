package com.wac.worktrack.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.wac.worktrack.cloud.Auth
import com.wac.worktrack.cloud.BackupPrefs
import com.wac.worktrack.ui.screens.BackupSheet
import com.wac.worktrack.ui.screens.SignInScreen
import com.wac.worktrack.ui.screens.DashboardScreen
import com.wac.worktrack.ui.screens.FocusScreen
import com.wac.worktrack.ui.screens.GoalsScreen
import com.wac.worktrack.ui.screens.HabitsScreen
import com.wac.worktrack.ui.screens.MoneyScreen
import com.wac.worktrack.ui.screens.MoneyTab

private val tabs = listOf(
    NavItem("Today", Icons.Outlined.Home, Icons.Rounded.Home),
    NavItem("Money", Icons.Outlined.AccountBalanceWallet, Icons.Rounded.AccountBalanceWallet),
    NavItem("Habits", Icons.Outlined.CheckCircle, Icons.Rounded.CheckCircle),
    NavItem("Goals", Icons.Outlined.Flag, Icons.Rounded.Flag),
    NavItem("Focus", Icons.Outlined.Shield, Icons.Rounded.Shield),
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    // Signed in, chose to stay offline, or Firebase isn't set up in this build.
    var gatePassed by remember {
        mutableStateOf(!Auth.firebaseReady(context) || Auth.user(context) != null || BackupPrefs(context).offline)
    }
    // Set when signing in finds data both here and in the cloud; the backup sheet asks which wins.
    var conflict by remember { mutableStateOf<Long?>(null) }
    if (!gatePassed) {
        SignInScreen { conflict = it; gatePassed = true }
        return
    }
    conflict?.let { BackupSheet(initialFound = it) { conflict = null } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var moneyTab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { BottomNav(tabs, tab) { tab = it } },
    ) { padding ->
        AnimatedContent(
            tab,
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "tab",
        ) { current ->
            val modifier = Modifier.fillMaxSize()
            when (current) {
                0 -> DashboardScreen(
                    modifier,
                    openMoney = { moneyTab = it.ordinal; tab = 1 },
                    openHabits = { tab = 2 },
                )
                1 -> MoneyScreen(modifier, MoneyTab.entries[moneyTab]) { moneyTab = it.ordinal }
                2 -> HabitsScreen(modifier)
                3 -> GoalsScreen(modifier)
                else -> FocusScreen(modifier)
            }
        }
    }
}
