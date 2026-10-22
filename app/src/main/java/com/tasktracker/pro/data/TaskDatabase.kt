package com.tasktracker.pro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Task::class], version = 2, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile private var instance: TaskDatabase? = null

        /** v1 → v2: adds the local-only exact-time reminder column. No data is touched. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderAt TEXT NOT NULL DEFAULT ''")
            }
        }

        fun get(context: Context): TaskDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "tasktracker.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }

        /** Test helper: in-memory database. */
        fun inMemory(context: Context): TaskDatabase =
            Room.inMemoryDatabaseBuilder(context, TaskDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }
}
