package com.dreamct.tingfeng.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [User::class, NotificationLog::class],
    version = 1,
    exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun notifyDao(): NotifyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 如果下方的的函数会被Java调用，需要在此处添加@JvmStatic关键字，
         * 或者在Java，AppDatabase之后添加Companion，例如：
         * AppDatabase.Companion.getInstance(context)*/
        @JvmStatic
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(context, AppDatabase::class.java, "app_database")
                .build()
    }
}