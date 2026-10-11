package com.example.iptvapp.playlist

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.iptvapp.epg.EpgDao
import com.example.iptvapp.epg.EpgProgramme

// version 2: added ChannelEntity.headers (#EXTVLCOPT HTTP headers).
// version 3: added the EpgProgramme (programmes) table for XMLTV EPG.
// No manual migrations — both tables are re-fetchable network caches, so
// fallbackToDestructiveMigration rebuilds them from the next fetch.
@Database(
    entities = [ChannelEntity::class, EpgProgramme::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun channelDao(): ChannelDao

    abstract fun epgDao(): EpgDao

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
