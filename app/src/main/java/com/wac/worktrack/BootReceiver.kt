package com.wac.worktrack

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the daily schedule alarms after a reboot (alarms are cleared on power-off). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ScheduleAlarm.rescheduleAll(context)
        }
    }
}
