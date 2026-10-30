package com.wac.worktrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Settings::class, Bank::class, Expense::class, Loan::class, PersonDebt::class, Payable::class,
        Saving::class, Habit::class, HabitLog::class, HabitTimer::class, Goal::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun money(): MoneyDao
    abstract fun habits(): HabitDao
    abstract fun goals(): GoalDao

    companion object {
        const val NAME = "worktrack-lite.db"

        /** v2: an emoji per habit and goal, and the day each goal was set. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "ALTER TABLE habits ADD COLUMN emoji TEXT NOT NULL DEFAULT '✅'",
                    "ALTER TABLE goals ADD COLUMN emoji TEXT NOT NULL DEFAULT '🎯'",
                    "ALTER TABLE goals ADD COLUMN startDay INTEGER NOT NULL DEFAULT 0",
                    // Existing goals count from today (epoch day in local time).
                    "UPDATE goals SET startDay = CAST(julianday('now', 'localtime') - 2440587.5 AS INTEGER)",
                ).forEach(db::execSQL)
            }
        }

        /** v3: alarm vs quiet reminder per habit, and start/stop timers for timed habits. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "ALTER TABLE habits ADD COLUMN ringAlarm INTEGER NOT NULL DEFAULT 1",
                    "CREATE TABLE IF NOT EXISTS `habit_timers` (`habitId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `accumulatedMillis` INTEGER NOT NULL, `runningSince` INTEGER, PRIMARY KEY(`habitId`, `date`))",
                ).forEach(db::execSQL)
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
