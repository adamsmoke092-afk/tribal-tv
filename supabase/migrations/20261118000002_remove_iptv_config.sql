-- IPTV-tier settings do not belong in the backend config table.
--
-- Plan §4: the free tier is IPTV, and "Supabase is used only for optional
-- curated-playlist and feature-flag enrichment, cached locally. Its absence
-- must not break this path." A row here makes the IPTV path depend on a
-- network read it is defined not to need.
--
-- These three were seeded by 20261106000002_create_app_config.sql, which
-- predates that split. The seed no longer inserts them; this deletes the
-- copies already on the hosted project.
--
-- Where the values live instead:
--   default playlist URL  SettingsStore.DEFAULT_PLAYLIST_URL (za.m3u)
--   EPG refresh interval   device-side, added with the IPTV channel work
--   buffer size            device-side, added with the IPTV channel work
--
-- app_config keeps only app-level rows: maintenance_mode, force_update,
-- min_app_version. Plan §6 adds pool_size, session_rotation_days and
-- device_stale_days later, gated behind Phase 0.

delete from app_config
where key in (
    'default_playlist_url',
    'epg_auto_refresh_hours',
    'max_buffer_size_mb'
);
