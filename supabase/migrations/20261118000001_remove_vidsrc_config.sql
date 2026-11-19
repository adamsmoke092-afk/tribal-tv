-- VidSrc is retired. The Movies tab is served by the VOD backend
-- (supabase/functions/vod), per the 2026-11-16 plan decision that VidSrc is
-- replaced entirely by the VOD provider in the Movies tab.
--
-- These two rows were seeded by 20261106000002_create_app_config.sql, which
-- was written 2026-11-06 -- ten days before that decision. The seed no longer
-- inserts them; this deletes the copies already present on the hosted project.
--
-- delete, not update: a row nothing reads is a row that will later be misread
-- as "the app still supports VidSrc". Neither key has a consumer in the app
-- (verified by grep: only FeatureFlags, its parser, and its test referenced
-- them), so removing them cannot change behaviour.

delete from app_config
where key in ('enable_vidsrc_movies', 'vidsrc_base_url');
