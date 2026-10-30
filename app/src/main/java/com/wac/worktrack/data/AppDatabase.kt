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
        Saving::class, Habit::class, HabitLog::class, HabitTimer::class, Goal::class, Income::class, PendingTxn::class,
        AppBlock::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun money(): MoneyDao
    abstract fun habits(): HabitDao
    abstract fun goals(): GoalDao
    abstract fun blocks(): BlockDao

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

        /** v4: money received, and bank SMS waiting to be checked. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "CREATE TABLE IF NOT EXISTS `incomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountPaise` INTEGER NOT NULL, `source` TEXT NOT NULL, `date` INTEGER NOT NULL, `bankId` INTEGER)",
                    "CREATE INDEX IF NOT EXISTS `index_incomes_date` ON `incomes` (`date`)",
                    "CREATE TABLE IF NOT EXISTS `pending_txns` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `smsKey` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `credit` INTEGER NOT NULL, `party` TEXT NOT NULL, `bankId` INTEGER, `account` TEXT NOT NULL, `date` INTEGER NOT NULL, `body` TEXT NOT NULL, `status` INTEGER NOT NULL)",
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_txns_smsKey` ON `pending_txns` (`smsKey`)",
                ).forEach(db::execSQL)
            }
        }

        /** v5: app blocks. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `app_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `packages` TEXT NOT NULL, `daily` INTEGER NOT NULL, `startMinutes` INTEGER NOT NULL, `endMinutes` INTEGER NOT NULL, `untilMillis` INTEGER NOT NULL)")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
                .also { instance = it }
        }
    }
}
