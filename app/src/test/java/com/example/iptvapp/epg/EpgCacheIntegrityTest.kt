package com.example.iptvapp.epg

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.example.iptvapp.playlist.AppDatabase

/**
 * Cache-integrity tests against a REAL Room database (Robolectric), covering
 * the safeguards that plain JVM tests cannot reach:
 *
 *  - two sources supplying the SAME channel coexist and merge deterministically,
 *  - one source failing leaves its own previous rows AND the other source's
 *    rows completely intact,
 *  - an empty-but-successful parse is reported as EMPTY, not FAILED, and does
 *    not wipe that source's cache,
 *  - a truncated guide merges exact matches without deleting anything and
 *    refuses to take a quality alias,
 *  - rows for the same channel id under a DIFFERENT playlist are invisible to
 *    this playlist's lookups.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EpgCacheIntegrityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "epg-cache-test-${System.nanoTime()}.db"
    private lateinit var db: AppDatabase
    private lateinit var dao: EpgDao
    private lateinit var repository: EpgRepository
    private var guideServer: java.net.ServerSocket? = null

    @Before
    fun setUp() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        dao = db.epgDao()
        repository = EpgRepository(dao)
    }

    @After
    fun tearDown() {
        guideServer?.close()
        db.close()
        context.getDatabasePath(dbName).delete()
    }

    /**
     * A minimal one-shot HTTP server on a loopback port, so a source can
     * genuinely SUCCEED over the network while another genuinely FAILS —
     * no mocking of the fetch path.
     */
    private fun startGuideServer(body: String): String {
        val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        guideServer = server
        val bytes = body.toByteArray()
        val thread = Thread {
            runCatching {
                server.accept().use { socket ->
                    socket.getOutputStream().write(
                        ("HTTP/1.1 200 OK\r\nContent-Type: application/xml\r\n" +
                            "Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n")
                            .toByteArray() + bytes
                    )
                    socket.getOutputStream().flush()
                }
            }
        }
        thread.isDaemon = true
        thread.start()
        return "http://127.0.0.1:${server.localPort}/guide.xml"
    }

    /** RFC-3339-ish XMLTV instant, always in the future so the window keeps it. */
    private fun xmltvInstant(offsetFromNowMs: Long): String =
        java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss ZZZ")
            .withZone(java.time.ZoneOffset.UTC)
            .format(java.time.Instant.ofEpochMilli(System.currentTimeMillis() + offsetFromNowMs))

    private fun programme(guideId: String, start: Long, stop: Long, title: String) =
        GuideProgramme(guideChannelId = guideId, startMs = start, stopMs = stop, title = title, description = null)

    private fun parse(vararg programmes: GuideProgramme, truncated: Boolean = false) =
        GuideParse(programmes.toList(), truncated)

    private val ids = setOf("nasaTV@HD")

    @Test
    fun `two sources for the same channel both survive and merge deterministically`() = runBlocking {
        val keyA = epgSourceKey("1", "https://a.example/g.xml")
        val keyB = epgSourceKey("1", "https://b.example/g.xml")

        val a = repository.storeParsedSource(
            keyA, "https://a.example/g.xml",
            parse(programme("nasaTV", 1_000, 2_000, "A show")),
            ids, looseMatching = true
        )
        val b = repository.storeParsedSource(
            keyB, "https://b.example/g.xml",
            parse(programme("nasaTV@HD", 1_000, 2_000, "B show")),
            ids, looseMatching = true
        )

        assertEquals(EpgSourceStatus.LOADED, a.status)
        assertEquals(EpgSourceStatus.LOADED, b.status)
        // Neither source erased the other, even though both describe one channel.
        assertEquals(1, dao.countBySource(keyA))
        assertEquals(1, dao.countBySource(keyB))
        assertEquals(2, dao.countBySources(listOf(keyA, keyB)))

        // Same start time in both: the tie is broken by source, so it is stable.
        val upcoming = dao.upcomingForChannel("nasaTV@HD", listOf(keyA, keyB), nowMs = 1_500)
        assertEquals(2, upcoming.size)
        assertEquals(upcoming.first().source, minOf(keyA, keyB))
    }

    @Test
    fun `a failed source keeps its own rows and never touches another source`() = runBlocking {
        val keyA = epgSourceKey("1", "https://a.example/g.xml")
        val keyB = epgSourceKey("1", "https://b.example/g.xml")

        repository.storeParsedSource(
            keyA, "https://a.example/g.xml",
            parse(programme("nasaTV@HD", 1_000, 2_000, "A cached")), ids, looseMatching = false
        )
        repository.storeParsedSource(
            keyB, "https://b.example/g.xml",
            parse(programme("nasaTV@HD", 3_000, 4_000, "B cached")), ids, looseMatching = false
        )
        val before = dao.count()

        // Simulate B being unusable this round: nothing may be written for it.
        val failed = repository.storeParsedSource(
            keyB, "https://b.example/g.xml",
            parse(truncated = false), ids, looseMatching = false
        )

        assertEquals(EpgSourceStatus.EMPTY, failed.status)
        assertEquals("B kept its previous schedule", 1, dao.countBySource(keyB))
        assertEquals("A was untouched", 1, dao.countBySource(keyA))
        assertEquals(before, dao.count())
    }

    @Test
    fun `an empty successful parse is EMPTY not FAILED and preserves the cache`() = runBlocking {
        val key = epgSourceKey("1", "https://a.example/g.xml")
        repository.storeParsedSource(
            key, "https://a.example/g.xml",
            parse(programme("nasaTV@HD", 1_000, 2_000, "Kept")), ids, looseMatching = false
        )

        val empty = repository.storeParsedSource(key, "https://a.example/g.xml", parse(), ids, looseMatching = false)

        assertEquals(EpgSourceStatus.EMPTY, empty.status)
        assertTrue(empty.errorMessage == null)
        assertEquals(1, dao.countBySource(key))
        assertEquals("Kept", dao.upcomingForChannel("nasaTV@HD", listOf(key), nowMs = 1_500).first().title)
    }

    @Test
    fun `a truncated guide merges exact matches without deleting cached rows`() = runBlocking {
        val key = epgSourceKey("1", "https://a.example/g.xml")
        repository.storeParsedSource(
            key, "https://a.example/g.xml",
            parse(programme("nasaTV@HD", 9_000, 10_000, "Earlier row")), ids, looseMatching = true
        )

        val truncated = repository.storeParsedSource(
            key,
            "https://a.example/g.xml",
            parse(programme("nasaTV@HD", 1_000, 2_000, "New exact row"), truncated = true),
            ids,
            looseMatching = true
        )

        assertEquals(EpgSourceStatus.TRUNCATED, truncated.status)
        assertTrue(truncated.errorMessage!!.contains("limit"))
        // The old row survived the truncation — the dataset was never replaced.
        assertEquals(2, dao.countBySource(key))
    }

    @Test
    fun `a truncated refresh updates a row that shares its primary key`() = runBlocking {
        val key = epgSourceKey("1", "https://a.example/g.xml")
        repository.storeParsedSource(
            key, "https://a.example/g.xml",
            parse(programme("nasaTV@HD", 1_000, 2_000, "Old title")), ids, looseMatching = false
        )

        // Same (source, channelId, startMs) key, different information: the
        // fresh row must REPLACE the stale one, not duplicate or hide it.
        val truncated = repository.storeParsedSource(
            key,
            "https://a.example/g.xml",
            parse(programme("nasaTV@HD", 1_000, 2_000, "Updated title"), truncated = true),
            ids,
            looseMatching = false
        )

        assertEquals(EpgSourceStatus.TRUNCATED, truncated.status)
        assertEquals("the same primary key must not produce two rows", 1, dao.countBySource(key))
        assertEquals(
            "Updated title",
            dao.upcomingForChannel("nasaTV@HD", listOf(key), nowMs = 1_500).first().title
        )
    }

    @Test
    fun `a non-truncated refresh still replaces its own dataset row by row`() = runBlocking {
        val key = epgSourceKey("1", "https://a.example/g.xml")
        repository.storeParsedSource(
            key, "https://a.example/g.xml",
            parse(
                programme("nasaTV@HD", 1_000, 2_000, "Row kept"),
                programme("nasaTV@HD", 3_000, 4_000, "Row dropped from the guide")
            ),
            ids, looseMatching = false
        )

        val loaded = repository.storeParsedSource(
            key, "https://a.example/g.xml",
            parse(programme("nasaTV@HD", 1_000, 2_000, "Row kept, new title")),
            ids, looseMatching = false
        )

        assertEquals(EpgSourceStatus.LOADED, loaded.status)
        // A full replace: the guide no longer lists the second row.
        assertEquals(1, dao.countBySource(key))
        assertEquals("Row kept, new title", dao.upcomingForChannel("nasaTV@HD", listOf(key), nowMs = 1_500).first().title)
    }

    @Test
    fun `a truncated guide will not guess a quality alias`() = runBlocking {
        val key = epgSourceKey("1", "https://a.example/g.xml")

        val result = repository.storeParsedSource(
            key, "https://a.example/g.xml",
            parse(programme("nasaTV", 1_000, 2_000, "Bare id only"), truncated = true),
            ids,
            looseMatching = true
        )

        assertEquals(EpgSourceStatus.TRUNCATED, result.status)
        assertEquals(0, result.programmeCount)
        assertEquals(0, dao.countBySource(key))
        assertTrue(result.unmatchedIds.contains("nasaTV@HD"))
    }

    @Test
    fun `loose matching off means only exact ids are ever stored`() = runBlocking {
        val key = epgSourceKey("1", "https://a.example/g.xml")

        val strict = repository.storeParsedSource(
            key, "https://a.example/g.xml",
            parse(programme("nasaTV", 1_000, 2_000, "Bare id")), ids, looseMatching = false
        )

        assertEquals(EpgSourceStatus.EMPTY, strict.status)
        assertEquals(0, dao.countBySource(key))
    }

    @Test
    fun `another playlist with the same channel id is not visible here`() = runBlocking {
        val otherPlaylistKey = epgSourceKey("2", "https://shared.example/g.xml")
        val myKey = epgSourceKey("1", "https://shared.example/g.xml")

        repository.storeParsedSource(
            otherPlaylistKey, "https://shared.example/g.xml",
            parse(programme("nasaTV@HD", 1_000, 2_000, "Other playlist's row")),
            ids, looseMatching = false
        )

        assertDifferentKeys(myKey, otherPlaylistKey)
        assertEquals(0, dao.countBySources(listOf(myKey)))
        assertEquals(1, dao.countBySources(listOf(otherPlaylistKey)))
        // Looking up with only my sources sees nothing of theirs.
        assertTrue(dao.upcomingForChannel("nasaTV@HD", listOf(myKey), nowMs = 1_500).isEmpty())
    }

    private fun assertDifferentKeys(mine: String, theirs: String) {
        assertFalse("same URL on two playlists must not share a key", mine == theirs)
        assertEquals(64, mine.length)
    }

    @Test
    fun `expired programmes are dropped while live ones stay`() = runBlocking {
        val key = epgSourceKey("1", "https://a.example/g.xml")
        repository.storeParsedSource(
            key, "https://a.example/g.xml",
            parse(
                programme("nasaTV@HD", 1_000, 2_000, "Finished"),
                programme("nasaTV@HD", System.currentTimeMillis(), System.currentTimeMillis() + 60_000, "Live")
            ),
            ids, looseMatching = false
        )
        assertEquals(2, dao.countBySource(key))

        dao.deleteExpired(System.currentTimeMillis())

        val remaining = dao.upcomingForChannel("nasaTV@HD", listOf(key), nowMs = System.currentTimeMillis())
        assertEquals(1, remaining.size)
        assertEquals("Live", remaining.first().title)
    }

    @Test
    fun `refresh with no reachable source still prunes expired rows and reports failure`() = runBlocking {
        val key = epgSourceKey("1", "http://127.0.0.1:1/gone.xml")
        val now = System.currentTimeMillis()
        repository.storeParsedSource(
            key, "http://127.0.0.1:1/gone.xml",
            parse(programme("nasaTV@HD", now + 60_000, now + 120_000, "Old")), ids, looseMatching = false
        )

        val result = repository.refresh(
            playlistKey = "1",
            guideUrls = listOf("http://127.0.0.1:1/gone.xml"),
            playlistIds = ids,
            looseMatching = false
        )

        assertEquals(1, result.sources.size)
        assertEquals(EpgSourceStatus.FAILED, result.sources.first().status)
        // Nothing was fetched, so nothing was wiped.
        assertEquals(1, dao.countBySource(key))
        assertEquals(EpgRefreshState.CACHED_DATA_AVAILABLE, result.state(isLoading = false))
    }

    @Test
    fun `two live sources, one failing and one succeeding, for the same channel`() = runBlocking {
        // One guide is served over real HTTP and carries the BARE id; the
        // playlist wants the @HD alias, so a successful load also proves the
        // alias mapping works through the real parser and network path.
        val served = startGuideServer(
            "<tv><programme " +
                "start=\"${xmltvInstant(60 * 60 * 1000L)}\" " +
                "stop=\"${xmltvInstant(2 * 60 * 60 * 1000L)}\" " +
                "channel=\"nasaTV\"><title>Served row</title></programme></tv>"
        )
        val unreachable = "http://127.0.0.1:1/gone.xml"

        val first = repository.refresh(
            playlistKey = "1",
            guideUrls = listOf(served, unreachable),
            playlistIds = ids,
            looseMatching = true
        )

        assertEquals(EpgSourceStatus.LOADED, first.sources[0].status)
        assertEquals(EpgSourceStatus.FAILED, first.sources[1].status)
        assertEquals(EpgRefreshState.PARTIALLY_LOADED, first.state(isLoading = false))
        // The alias mapped through to the playlist id.
        assertEquals(1, dao.countBySource(epgSourceKey("1", served)))
        assertEquals("Served row", dao.upcomingForChannel("nasaTV@HD", listOf(epgSourceKey("1", served)), nowMs = System.currentTimeMillis()).first().title)

        // Now the served source fails too: its cached row must survive.
        guideServer?.close()
        guideServer = null

        val second = repository.refresh(
            playlistKey = "1",
            guideUrls = listOf(served, unreachable),
            playlistIds = ids,
            looseMatching = true
        )

        assertEquals(EpgSourceStatus.FAILED, second.sources[0].status)
        assertEquals(EpgSourceStatus.FAILED, second.sources[1].status)
        assertEquals("the previously loaded row survived the failure", 1, dao.countBySource(epgSourceKey("1", served)))
        assertEquals(EpgRefreshState.CACHED_DATA_AVAILABLE, second.state(isLoading = false))
    }

    @Test
    fun `refresh reports redacted urls only, never the raw source`() = runBlocking {
        val result = repository.refresh(
            playlistKey = "1",
            guideUrls = listOf("https://user:pass@127.0.0.1:1/g.xml?token=SECRET"),
            playlistIds = ids,
            looseMatching = false
        )
        val shown = result.sources.first().redactedUrl
        assertFalse("raw credentials leaked into diagnostics: $shown", shown.contains("SECRET"))
        assertFalse("userinfo leaked into diagnostics: $shown", shown.contains("user:pass"))
        assertFalse("raw url echoed back: $shown", shown.contains("token"))
        assertTrue(shown.startsWith("https://127.0.0.1:1"))
    }
}
