package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ApiKeyConfig::class,
        ProjectState::class,
        SrtLineState::class,
        GeneralSetting::class,
        TapSession::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apiKeyConfigDao(): ApiKeyConfigDao
    abstract fun projectStateDao(): ProjectStateDao
    abstract fun srtLineStateDao(): SrtLineStateDao
    abstract fun generalSettingDao(): GeneralSettingDao
    abstract fun tapSessionDao(): TapSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "subtitle_studio_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
