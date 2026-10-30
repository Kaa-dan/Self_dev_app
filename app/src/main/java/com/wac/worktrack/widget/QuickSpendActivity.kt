package com.wac.worktrack.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.db
import com.wac.worktrack.data.Bank
import com.wac.worktrack.ui.LocalToday
import com.wac.worktrack.ui.WorkTrackTheme
import com.wac.worktrack.ui.rememberFlow
import com.wac.worktrack.ui.screens.ExpenseSheet

/** The app's spend form over the home screen, opened from the widget. Closes when saved or dismissed. */
class QuickSpendActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WorkTrackTheme {
                CompositionLocalProvider(LocalToday provides Day.today()) {
                    val banks = rememberFlow<List<Bank>?>(null) { db.money().banks() }
                    // Wait for the banks so "Paid from" starts on the first one, as in the app.
                    banks?.let { ExpenseSheet(it, onDismiss = ::finish) }
                }
            }
        }
    }
}
