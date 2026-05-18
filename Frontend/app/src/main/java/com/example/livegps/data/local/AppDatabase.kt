package com.example.livegps.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** Room database holding the offline location buffer. */
@Database(entities = [LocationEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun locationDao(): LocationDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "livegps.db",
            )
                // The buffer holds only transient unsent fixes — a destructive
                // upgrade is acceptable and avoids hand-written migrations.
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}
