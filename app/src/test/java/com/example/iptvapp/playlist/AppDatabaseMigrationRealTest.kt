package com.example.iptvapp.playlist

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.iptvapp.epg.EpgProgramme
import com.example.iptvapp.epg.epgSourceKey
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * A REAL Room 3 -> 4 migration, executed by Robolectric.
 *
 * A database is first built with the exact schema Room 2.6.1 generated for
 * these entities at version 3, populated with a cached channel and an old EPG
 * row, and then opened through Room at version 4 — which runs the registered
 * MIGRATION_3_4 and validates the resulting schema against the entities.
 *
 * This proves the three things the SQL-level structural test cannot:
 *  1. cached channel records SURVIVE the upgrade with their data intact,
 *  2. the old EPG rows (which have no source attribution) are discarded,
 *  3. Room accepts the recreated `programmes` table, and the new schema works
 *     for both reads and writes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationRealTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbFile: File get() = context.getDatabasePath(DB_NAME)

    private lateinit var db: AppDatabase

    companion object {
        private const val DB_NAME = "migration-real-test.db"

        /** Exactly the schema Room 2.6.1 generates for ChannelEntity (unchanged since v2). */
        private const val CHANNELS_V3_DDL =
            "CREATE TABLE IF NOT EXISTS `channels` (" +
                "`streamUrl` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`logoUrl` TEXT, " +
                "`groupTitle` TEXT, " +
                "`tvgId` TEXT, " +
                "`headers` TEXT NOT NULL, " +
                "PRIMARY KEY(`streamUrl`))"

        /** Exactly the schema Room 2.6.1 generated for the pre-source EpgProgramme. */
        private const val PROGRAMMES_V3_DDL =
            "CREATE TABLE IF NOT EXISTS `programmes` (" +
                "`channelId` TEXT NOT NULL, " +
                "`startMs` INTEGER NOT NULL, " +
                "`stopMs` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`description` TEXT, " +
                "PRIMARY KEY(`channelId`, `startMs`))"
    }

    @Before
    fun startClean() {
        deleteDbFiles()
        dbFile.parentFile?.mkdirs()
    }

    @After
    fun closeAndCleanUp() {
        if (::db.isInitialized) db.close()
        deleteDbFiles()
    }

    private fun deleteDbFiles() {
        listOf("", "-wal", "-shm").forEach { suffix -> File(dbFile.path + suffix).delete() }
    }

    /** Builds the pre-upgrade database exactly as Room wrote it at version 3. */
    private fun createV3Database() {
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { raw ->
            raw.execSQL(CHANNELS_V3_DDL)
            raw.execSQL(PROGRAMMES_V3_DDL)
            raw.execSQL(
                "INSERT INTO `channels` (`streamUrl`,`name`,`logoUrl`,`groupTitle`,`tvgId`,`headers`) " +
                    "VALUES ('http://stream.example/sabc1','SABC 1',NULL,'News','sabc.1','')"
            )
            raw.execSQL(
                "INSERT INTO `programmes` (`channelId`,`startMs`,`stopMs`,`title`,`description`) " +
                    "VALUES ('sabc.1', 1000, 2000, 'Old schedule row', NULL)"
            )
            raw.version = 3
        }
    }

    private fun openAtV4(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `channels survive the upgrade with their data intact`() = runBlocking {
        createV3Database()
        db = openAtV4()

        val channels = db.channelDao().getAll()
        assertEquals(1, channels.size)
        assertEquals("SABC 1", channels[0].name)
        assertEquals("sabc.1", channels[0].tvgId)
        assertEquals("http://stream.example/sabc1", channels[0].streamUrl)
        assertTrue(channels[0].headers.isEmpty())
    }

    @Test
    fun `old epg rows are discarded because they carry no source attribution`() = runBlocking {
        createV3Database()
        db = openAtV4()

        assertEquals(0, db.epgDao().count())
        assertTrue(db.epgDao().distinctSources().isEmpty())
    }

    @Test
    fun `the recreated schema stores and reads source-attributed programmes`() = runBlocking {
        createV3Database()
        db = openAtV4()

        val key = epgSourceKey("3", "https://g.example/za.xml.gz")
        db.epgDao().replaceSource(
            key,
            listOf(
                EpgProgramme(
                    source = key,
                    channelId = "sabc.1",
                    startMs = 5_000,
                    stopMs = 6_000,
                    title = "News at six"
                )
            )
        )

        assertEquals(1, db.epgDao().count())
        assertEquals(1, db.epgDao().countBySources(listOf(key)))
        assertEquals(1, db.epgDao().countDistinctChannelsFor(listOf(key)))

        val upcoming = db.epgDao().upcomingForChannel("sabc.1", listOf(key), nowMs = 4_000)
        assertEquals("News at six", upcoming.first().title)
    }
}
