package com.wac.worktrack.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Settings::class, Bank::class, Expense::class, Loan::class, PersonDebt::class, Payable::class,
        Saving::class, Habit::class, HabitLog::class, Goal::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun money(): MoneyDao
    abstract fun habits(): HabitDao
    abstract fun goals(): GoalDao

    companion object {
        const val NAME = "worktrack-lite.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .build()
                .also { instance = it }
        }
    }
}
