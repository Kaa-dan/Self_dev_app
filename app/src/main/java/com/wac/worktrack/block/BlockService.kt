package com.wac.worktrack.block

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.wac.worktrack.core.db
import com.wac.worktrack.data.AppBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Watches which app comes to the front and sends you home from a blocked one. */
class BlockService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var blocks: List<AppBlock> = emptyList()
    private var lastBlocked = 0L

    override fun onServiceConnected() {
        scope.launch { db.blocks().blocks().collect { blocks = it } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        // Content changes also catch an app left open when its block starts; they come in bursts.
        if (now - lastBlocked < 1500) return
        val until = Blocks.blockedUntil(blocks, pkg, now) ?: return
        lastBlocked = now
        // Cover the app rather than pressing Home first: Android drops activity starts for a few seconds after Home.
        startActivity(
            Intent(this, BlockedActivity::class.java)
                .putExtra(BlockedActivity.EXTRA_PACKAGE, pkg)
                .putExtra(BlockedActivity.EXTRA_UNTIL, until)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
