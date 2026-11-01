package com.example.iptvapp.playlist

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.iptvapp.epg.EpgDao
import com.example.iptvapp.epg.EpgProgramme

// version 2: added ChannelEntity.headers (#EXTVLCOPT HTTP headers).
// version 3: added the EpgProgramme (programmes) table for XMLTV EPG.
// version 4: programmes gained a `source` column (which guide a row came
//            from) and it joined the primary key. See MIGRATION_3_4 below.
//
// fallbackToDestructiveMigration() stays registered for FUTURE version bumps
// only: both tables are re-fetchable network caches, and a later schema change
// with no migration should rebuild rather than crash on upgrade. The 3 -> 4
// upgrade is explicitly handled and never takes that path.
@Database(
    entities = [ChannelEntity::class, EpgProgramme::class],
    version = 4,
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
                    // Only reached by FUTURE version bumps that ship without a
                    // migration. Safe here: both tables are re-fetchable network
                    // caches, not source-of-truth data (SPEC §4, risk #6).
                    .fallbackToDestructiveMigration()
                    .addMigrations(MIGRATION_3_4)
                    .build().also { instance = it }
            }
    }
}

/** Drops the old programmes table, whose rows carry no source attribution. */
internal const val DROP_PROGRAMMES_V3 = "DROP TABLE IF EXISTS `programmes`"

/**
 * The v4 programmes table, written to match what Room generates for
 * [EpgProgramme]: column order, NOT NULL flags and primary-key order must all
 * agree, or Room's post-migration schema validation rejects the upgrade.
 */
internal const val CREATE_PROGRAMMES_V4 =
    "CREATE TABLE IF NOT EXISTS `programmes` (" +
        "`source` TEXT NOT NULL, " +
        "`channelId` TEXT NOT NULL, " +
        "`startMs` INTEGER NOT NULL, " +
        "`stopMs` INTEGER NOT NULL, " +
        "`title` TEXT NOT NULL, " +
        "`description` TEXT, " +
        "PRIMARY KEY(`source`, `channelId`, `startMs`))"

/** The statements MIGRATION_3_4 runs, exposed so a test can assert on them. */
internal val MIGRATION_3_4_STATEMENTS: List<String> =
    listOf(DROP_PROGRAMMES_V3, CREATE_PROGRAMMES_V4)

/**
 * Targeted 3 -> 4 upgrade: ONLY the cached EPG table is dropped and recreated,
 * because its existing rows have no `source` to attribute them to. The
 * `channels` cache and every SharedPreferences store (playlists, favourites,
 * recents, settings) are untouched.
 *
 * Consequence, stated plainly: existing EPG schedules are discarded and are
 * re-downloaded on the next guide refresh. Nothing else is lost.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (statement in MIGRATION_3_4_STATEMENTS) {
            db.execSQL(statement)
        }
    }
}
