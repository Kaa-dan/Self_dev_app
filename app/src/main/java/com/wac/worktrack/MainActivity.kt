package com.wac.worktrack

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.wac.worktrack.core.Day
import com.wac.worktrack.sms.SmsImport
import com.wac.worktrack.ui.AppRoot
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.WorkTrackTheme

class MainActivity : ComponentActivity() {

    private var today by mutableLongStateOf(Day.today())

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            WorkTrackTheme {
                CompositionLocalProvider(LocalToday provides today) { AppRoot() }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Roll over to the new day if the app was left open past midnight.
        today = Day.today()
        SmsImport.scan(this)
    }
}
