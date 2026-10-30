package com.wac.worktrack

import android.app.Application
import com.wac.worktrack.notify.HabitAlarms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WorkTrackApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        HabitAlarms.createChannels(this)
    }
}
