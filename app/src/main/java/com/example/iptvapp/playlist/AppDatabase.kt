package com.example.iptvapp.playlist

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// version 2: added ChannelEntity.headers (#EXTVLCOPT HTTP headers). No manual
// migration needed — the table is a re-fetchable network cache, so
// fallbackToDestructiveMigration rebuilds it from the next playlist fetch.
@Database(entities = [ChannelEntity::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
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
