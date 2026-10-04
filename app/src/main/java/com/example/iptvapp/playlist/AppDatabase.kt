package com.example.iptvapp.playlist

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ChannelEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun channelDao(): ChannelDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "iptv.db"
                )
                    // Safe here: this table is just a re-fetchable network cache, not
                    // source-of-truth data. A future schema bump re-fetches instead of
                    // crashing on upgrade with no migration defined (SPEC §4, risk #6).
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
