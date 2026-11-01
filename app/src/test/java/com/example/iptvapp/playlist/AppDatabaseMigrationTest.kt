package com.example.iptvapp.playlist

import com.example.iptvapp.epg.EpgProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural checks on the 3 -> 4 EPG upgrade.
 *
 * LIMITATION, stated plainly: this project has no instrumented-test setup and
 * no Robolectric, and a plain JVM test cannot open a Room database. So these
 * tests pin everything that is checkable without one — the version pair, that
 * only the programmes table is touched, and that the recreated table's columns
 * agree with the entity — but they do NOT prove Room accepts the DDL at
 * runtime. Proving that needs a device/emulator run (or adding Robolectric).
 */
class AppDatabaseMigrationTest {

    @Test
    fun `migration targets exactly version 3 to version 4`() {
        assertEquals(3, MIGRATION_3_4.startVersion)
        assertEquals(4, MIGRATION_3_4.endVersion)
    }

    @Test
    fun `only the programmes table is dropped and recreated`() {
        assertEquals(2, MIGRATION_3_4_STATEMENTS.size)
        assertEquals("DROP TABLE IF EXISTS `programmes`", MIGRATION_3_4_STATEMENTS[0])
        assertTrue(MIGRATION_3_4_STATEMENTS[1].startsWith("CREATE TABLE IF NOT EXISTS `programmes`"))
    }

    @Test
    fun `the channels cache and other tables are never touched`() {
        MIGRATION_3_4_STATEMENTS.forEach { statement ->
            assertFalse(
                "the migration must not touch the channels cache: $statement",
                statement.contains("channels", ignoreCase = true)
            )
        }
    }

    @Test
    fun `the recreated table carries source in the primary key`() {
        assertTrue(CREATE_PROGRAMMES_V4.contains("PRIMARY KEY(`source`, `channelId`, `startMs`)"))
    }

    @Test
    fun `the recreated table columns match the entity exactly`() {
        val entityFields = EpgProgramme::class.java.declaredFields.map { it.name }.toSet()
        assertEquals(
            setOf("source", "channelId", "startMs", "stopMs", "title", "description"),
            entityFields
        )
        assertTrue(CREATE_PROGRAMMES_V4.contains("`source` TEXT NOT NULL"))
        assertTrue(CREATE_PROGRAMMES_V4.contains("`channelId` TEXT NOT NULL"))
        assertTrue(CREATE_PROGRAMMES_V4.contains("`startMs` INTEGER NOT NULL"))
        assertTrue(CREATE_PROGRAMMES_V4.contains("`stopMs` INTEGER NOT NULL"))
        assertTrue(CREATE_PROGRAMMES_V4.contains("`title` TEXT NOT NULL"))
        assertTrue(CREATE_PROGRAMMES_V4.contains("`description` TEXT,"))
    }
}
