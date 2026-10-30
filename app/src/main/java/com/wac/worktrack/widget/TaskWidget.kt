package com.wac.worktrack.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.room.InvalidationTracker
import com.wac.worktrack.MainActivity
import com.wac.worktrack.R
import com.wac.worktrack.WorkTrackApp
import com.wac.worktrack.core.Day
import com.wac.worktrack.core.Money
import com.wac.worktrack.core.db
import com.wac.worktrack.core.durationLabel
import com.wac.worktrack.notify.HabitAlarms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Home-screen widget: the habit timer you started today (ticking while it runs) with pause / resume,
 * today's spend, and a button that opens the spend form.
 */
class TaskWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        (context.applicationContext as WorkTrackApp).scope.launch {
            try {
                refresh(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws the widget whenever timers, habits or spends change. */
        fun watch(app: WorkTrackApp) {
            app.db.invalidationTracker.addObserver(
                object : InvalidationTracker.Observer(arrayOf("habit_timers", "habit_logs", "habits", "expenses")) {
                    override fun onInvalidated(tables: Set<String>) {
                        app.scope.launch { refresh(app) }
                    }
                },
            )
        }

        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TaskWidget::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, views(context))
        }

        private suspend fun views(context: Context): RemoteViews {
            val habits = context.db.habits()
            val today = Day.today()
            val now = System.currentTimeMillis()
            // Started today and not finished yet, the running one first.
            val ongoing = habits.timers(today).first()
                .filter { (it.runningSince != null || it.accumulatedMillis > 0) && !habits.isLogged(it.habitId, today) }
                .sortedBy { it.runningSince == null }
                .firstNotNullOfOrNull { t -> habits.habit(t.habitId)?.takeIf { it.durationMinutes != null }?.let { it to t } }
            val spent = context.db.money().expenses(today, today).first().sumOf { it.amountPaise }

            return RemoteViews(context.packageName, R.layout.widget_task).apply {
                if (ongoing == null) {
                    setTextViewText(R.id.widget_task, "⚡ No task running")
                    setViewVisibility(R.id.widget_timer, View.GONE)
                    setViewVisibility(R.id.widget_toggle, View.GONE)
                } else {
                    val (habit, timer) = ongoing
                    val running = timer.runningSince != null
                    setTextViewText(R.id.widget_task, "${habit.emoji} ${habit.name}" + if (running) "" else " · paused")
                    setViewVisibility(R.id.widget_timer, View.VISIBLE)
                    setChronometer(R.id.widget_timer, SystemClock.elapsedRealtime() - timer.elapsed(now), null, running)
                    setViewVisibility(R.id.widget_toggle, View.VISIBLE)
                    setTextViewText(R.id.widget_toggle, if (running) "⏸" else "▶")
                    setOnClickPendingIntent(
                        R.id.widget_toggle,
                        HabitAlarms.actionIntent(context, if (running) HabitAlarms.PAUSE else HabitAlarms.START, habit.id),
                    )
                }
                setTextViewText(
                    R.id.widget_spent,
                    "Spent today ${Money.format(spent)}" + (ongoing?.first?.durationMinutes?.let { " · goal ${durationLabel(it)}" } ?: ""),
                )
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
                )
                setOnClickPendingIntent(
                    R.id.widget_add,
                    PendingIntent.getActivity(
                        context, 1,
                        Intent(context, QuickSpendActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
        }
    }
}
