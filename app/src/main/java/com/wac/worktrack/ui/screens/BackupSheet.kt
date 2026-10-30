@file:OptIn(ExperimentalMaterial3Api::class)

package com.wac.worktrack.ui.screens

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.wac.worktrack.cloud.Auth
import com.wac.worktrack.cloud.BackupPrefs
import com.wac.worktrack.cloud.CloudBackup
import com.wac.worktrack.core.Day
import com.wac.worktrack.ui.AppCard
import com.wac.worktrack.ui.ConfirmDialog
import com.wac.worktrack.ui.Good
import com.wac.worktrack.ui.PrimaryButton
import com.wac.worktrack.ui.Warn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Icon for the Today screen's backup button: off, signed out, or backed up. */
fun backupIcon(context: android.content.Context) = when {
    !Auth.firebaseReady(context) -> Icons.Rounded.CloudOff
    Auth.user(context) == null -> Icons.Rounded.CloudUpload
    else -> Icons.Rounded.CloudDone
}

/** Google sign-in and the cloud backup: back up now, restore, daily auto backup, sign out. */
@Composable
fun BackupSheet(initialFound: Long? = null, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val prefs = remember { BackupPrefs(context) }
    var user by remember { mutableStateOf(Auth.user(context)) }
    var last by remember { mutableStateOf(prefs.lastBackupMillis) }
    var auto by remember { mutableStateOf(prefs.autoBackup) }
    var busy by remember { mutableStateOf<String?>(null) }
    // Set after signing in when the account already has a backup: restore it or replace it.
    var found by remember { mutableStateOf(initialFound) }
    var confirmRestore by remember { mutableStateOf(false) }
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    fun backUp() {
        busy = "backup"
        scope.launch {
            runCatching { CloudBackup.backup(context) }
                .onSuccess { toast("Backed up $it items."); last = prefs.lastBackupMillis; found = null }
                .onFailure { toast("Backup failed: ${it.message}") }
            busy = null
        }
    }

    // Keeps the daily backup in step with the signed-in state and the toggle.
    LaunchedEffect(user, auto) { CloudBackup.schedule(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Cloud backup", style = MaterialTheme.typography.headlineSmall)
            val signedIn = user
            when {
                !Auth.firebaseReady(context) -> Note(
                    "Cloud backup isn't set up in this build yet. Everything is saved on this phone only.",
                )
                signedIn == null -> {
                    Note("Sign in with Google to keep a copy of your money, habits and goals in your Firebase account. It backs up right after you sign in, whenever you leave the app, and every day.")
                    PrimaryButton(
                        if (busy == "signin") "Signing in…" else "Sign in with Google",
                        {
                            val a = activity ?: return@PrimaryButton
                            busy = "signin"
                            scope.launch {
                                Auth.signIn(a)
                                    .onSuccess { u ->
                                        user = u
                                        runCatching { CloudBackup.afterSignIn(context) }
                                            .onSuccess { start ->
                                                last = prefs.lastBackupMillis
                                                when (start) {
                                                    is CloudBackup.Start.Uploaded -> toast("Backed up ${start.rows} items.")
                                                    is CloudBackup.Start.Restored -> toast("Restored ${start.rows} items from your backup.")
                                                    is CloudBackup.Start.Conflict -> found = start.cloudMillis
                                                }
                                            }
                                            .onFailure { toast("Signed in, but couldn't reach the cloud: ${it.message}") }
                                    }
                                    .onFailure { toast(it.message ?: "Sign-in failed") }
                                busy = null
                            }
                        },
                        Modifier.fillMaxWidth(), enabled = busy == null, icon = Icons.AutoMirrored.Rounded.Login,
                    )
                }
                else -> {
                    AppCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                        Text("Signed in as", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(signedIn.email ?: signedIn.displayName ?: "Google account", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (last == 0L) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDone, null,
                                tint = if (last == 0L) Warn else Good, modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (last == 0L) "Not backed up from this phone yet" else "Last backup ${whenLabel(last)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    found?.let {
                        Note("This account already has a backup from ${whenLabel(it)}. Restore it to this phone, or replace it with what's on this phone now.")
                    }
                    PrimaryButton(
                        when {
                            busy == "backup" -> "Backing up…"
                            found != null -> "Replace with this phone's data"
                            else -> "Back up now"
                        },
                        ::backUp, Modifier.fillMaxWidth(), enabled = busy == null, icon = Icons.Rounded.CloudUpload,
                    )
                    PrimaryButton(
                        if (busy == "restore") "Restoring…" else "Restore from cloud",
                        { confirmRestore = true }, Modifier.fillMaxWidth(), enabled = busy == null, outlined = true, icon = Icons.Rounded.Restore,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Back up automatically", style = MaterialTheme.typography.titleSmall)
                            Text("When you leave the app and once a day, whenever you're online", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(auto, { auto = it; prefs.autoBackup = it })
                    }
                    TextButton(
                        onClick = { scope.launch { Auth.signOut(context); user = null; found = null } },
                        enabled = busy == null, modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sign out", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            "Restore from cloud?",
            "This replaces everything on this phone with your last cloud backup.",
            "Restore",
            onDismiss = { confirmRestore = false },
        ) {
            busy = "restore"
            scope.launch {
                runCatching { CloudBackup.restore(context) }
                    .onSuccess {
                        toast(if (it < 0) "No backup found for this account." else "Restored $it items.")
                        if (it >= 0) { found = null; last = prefs.lastBackupMillis }
                    }
                    .onFailure { toast("Restore failed: ${it.message}") }
                busy = null
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "Today 9:41 PM" / "28 Oct 9:41 PM". */
private fun whenLabel(millis: Long): String =
    Day.label(Day.ofMillis(millis)) + " " +
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))

/**
 * Shown when the app opens until the user signs in or chooses to stay offline, like the full
 * app. [onDone] gets the cloud backup's time when both phone and cloud already have data.
 */
@Composable
fun SignInScreen(onDone: (conflict: Long?) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("WorkTrack", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(
            "Money, habits and goals, kept in one place. Sign in so your records are backed up to your Google account.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(36.dp))
        PrimaryButton(
            if (busy) "Signing in…" else "Sign in with Google",
            {
                val a = activity ?: return@PrimaryButton
                busy = true
                error = null
                scope.launch {
                    Auth.signIn(a)
                        .onSuccess {
                            val start = runCatching { CloudBackup.afterSignIn(context) }.getOrNull()
                            when (start) {
                                is CloudBackup.Start.Restored -> Toast.makeText(context, "Restored ${start.rows} items from your backup.", Toast.LENGTH_LONG).show()
                                null -> Toast.makeText(context, "Signed in. Couldn't reach the cloud yet; open the cloud button to back up.", Toast.LENGTH_LONG).show()
                                else -> {}
                            }
                            onDone((start as? CloudBackup.Start.Conflict)?.cloudMillis)
                        }
                        .onFailure { error = it.message ?: "Sign-in failed" }
                    busy = false
                }
            },
            Modifier.fillMaxWidth(), enabled = !busy, icon = Icons.AutoMirrored.Rounded.Login,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { BackupPrefs(context).offline = true; onDone(null) },
            enabled = !busy, modifier = Modifier.fillMaxWidth(),
        ) { Text("Continue without signing in", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
