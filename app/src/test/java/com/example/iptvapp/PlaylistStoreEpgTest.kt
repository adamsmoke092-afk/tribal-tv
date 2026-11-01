package com.example.iptvapp

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The preference-backed half of the EPG work: per-playlist manual overrides
 * and header URLs, plus the one-time migration of the legacy global EPG
 * setting. Needs a Context, hence Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistStoreEpgTest {

    private lateinit var store: PlaylistStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = PlaylistStore(context)
    }

    @Test
    fun `override and header default to empty`() {
        assertEquals("", store.epgOverride(1L))
        assertEquals("", store.epgHeaderUrl(1L))
    }

    @Test
    fun `each playlist keeps its own override and header`() {
        store.setEpgOverride(1L, "https://mine.example/g.xml")
        store.setEpgHeaderUrl(1L, "https://declared.example/g.xml")
        store.setEpgOverride(2L, "https://other.example/g.xml")

        assertEquals("https://mine.example/g.xml", store.epgOverride(1L))
        assertEquals("https://other.example/g.xml", store.epgOverride(2L))
        assertEquals("https://declared.example/g.xml", store.epgHeaderUrl(1L))
        assertEquals("", store.epgHeaderUrl(2L))
    }

    @Test
    fun `values are trimmed on write`() {
        store.setEpgOverride(1L, "  https://g.example/x.xml.gz  ")
        assertEquals("https://g.example/x.xml.gz", store.epgOverride(1L))
    }

    @Test
    fun `removing a playlist removes its epg metadata too`() {
        val added = store.add("Mine", "https://p.example/list.m3u")
        store.setEpgOverride(added.id, "https://g.example/x.xml")
        store.setEpgHeaderUrl(added.id, "https://h.example/x.xml")

        store.remove(added.id)

        assertEquals("", store.epgOverride(added.id))
        assertEquals("", store.epgHeaderUrl(added.id))
    }

    // ---- legacy migration ----

    @Test
    fun `a legacy global url migrates onto the active playlist once`() {
        val active = store.ensureSeeded("https://p.example/list.m3u")

        val migrated = store.migrateLegacyEpgOverride("https://legacy.example/g.xml", active.id)

        assertEquals("https://legacy.example/g.xml", migrated)
        assertEquals("https://legacy.example/g.xml", store.epgOverride(active.id))
    }

    @Test
    fun `the migration copies to the active playlist only`() {
        val active = store.ensureSeeded("https://p.example/list.m3u")
        val other = store.add("Other", "https://other.example/list.m3u")

        store.migrateLegacyEpgOverride("https://legacy.example/g.xml", active.id)

        assertEquals("https://legacy.example/g.xml", store.epgOverride(active.id))
        assertEquals("", store.epgOverride(other.id))
    }

    @Test
    fun `the migration runs only once`() {
        val active = store.ensureSeeded("https://p.example/list.m3u")
        store.migrateLegacyEpgOverride("https://legacy.example/g.xml", active.id)

        // A later call must NOT overwrite whatever the user configured since.
        store.setEpgOverride(active.id, "https://user-changed.example/g.xml")
        val second = store.migrateLegacyEpgOverride("https://legacy.example/g.xml", active.id)

        assertEquals("https://user-changed.example/g.xml", second)
        assertEquals("https://user-changed.example/g.xml", store.epgOverride(active.id))
    }

    @Test
    fun `an empty legacy url is a no-op that still retires the migration`() {
        val active = store.ensureSeeded("https://p.example/list.m3u")

        assertNull(store.migrateLegacyEpgOverride("", active.id))
        assertEquals("", store.epgOverride(active.id))
        // Second call reports the (still empty) override; the flag is set.
        assertNull(store.migrateLegacyEpgOverride("https://late.example/g.xml", active.id))
        assertEquals("", store.epgOverride(active.id))
    }

    @Test
    fun `the legacy value is not discarded before an active playlist exists`() {
        // No ensureSeeded: there is nothing to attach the legacy value to.
        val migrated = store.migrateLegacyEpgOverride("https://legacy.example/g.xml", NEW_PLAYLIST_ID)

        assertNull(migrated)
        // The migration flag must NOT be set, so the value survives for later.
        val active = store.ensureSeeded("https://p.example/list.m3u")
        val later = store.migrateLegacyEpgOverride("https://legacy.example/g.xml", active.id)
        assertEquals("https://legacy.example/g.xml", later)
    }

    @Test
    fun `a legacy url the user never set migrates to nothing`() {
        val active = store.ensureSeeded("https://p.example/list.m3u")
        assertNull(store.migrateLegacyEpgOverride("", active.id))
        assertFalse(store.epgOverride(active.id).isNotEmpty())
    }

    @Test
    fun `header urls survive a playlist switch back`() {
        val first = store.add("First", "https://first.example/list.m3u")
        val second = store.add("Second", "https://second.example/list.m3u")
        store.setEpgHeaderUrl(first.id, "https://first.example/g.xml")
        store.setEpgHeaderUrl(second.id, "https://second.example/g.xml")

        store.setActive(second.id)
        assertEquals("https://second.example/g.xml", store.epgHeaderUrl(store.activePlaylist().id))
        store.setActive(first.id)
        assertEquals("https://first.example/g.xml", store.epgHeaderUrl(store.activePlaylist().id))
    }
}
