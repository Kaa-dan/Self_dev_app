package com.wac.worktrack.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.wac.worktrack.WorkTrackApp
import com.wac.worktrack.core.db
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Turns bank SMS into pending transactions: new ones as they arrive, and the inbox when the app opens. */
object SmsImport {
    private const val PREFS = "sms_import"
    private const val LAST_SCAN = "last_scan"
    /** The first scan looks this far back. */
    private const val FIRST_SCAN_DAYS = 30L

    val permissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

    fun allowed(context: Context) = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Reads inbox SMS that arrived since the last scan. */
    fun scan(context: Context) {
        if (!allowed(context)) return
        val app = context.applicationContext as WorkTrackApp
        app.scope.launch {
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val since = prefs.getLong(LAST_SCAN, System.currentTimeMillis() - FIRST_SCAN_DAYS * 86_400_000L)
            val banks = app.db.money().banks().first()
            var latest = since
            app.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                "${Telephony.Sms.DATE} > ?", arrayOf(since.toString()), "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val millis = c.getLong(2)
                    latest = maxOf(latest, millis)
                    val txn = BankSms.parse(c.getString(0) ?: continue, c.getString(1) ?: continue, millis, banks) ?: continue
                    app.db.money().insertPending(txn)
                }
            }
            prefs.edit { putLong(LAST_SCAN, latest) }
        }
    }
}

/** A new SMS: added straight away if it is a bank transaction. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty()
        val first = parts.firstOrNull() ?: return
        val address = first.originatingAddress ?: return
        // A long SMS arrives in parts; the inbox stores them joined.
        val body = parts.joinToString("") { it.messageBody.orEmpty() }
        val pending = goAsync()
        val app = context.applicationContext as WorkTrackApp
        app.scope.launch {
            try {
                val banks = app.db.money().banks().first()
                BankSms.parse(address, body, System.currentTimeMillis(), banks)?.let { app.db.money().insertPending(it) }
            } finally {
                pending.finish()
            }
        }
    }
}
