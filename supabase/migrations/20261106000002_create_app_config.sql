-- App configuration / feature flags table for Tribal TV
-- Managed via admin panel, consumed by the Android app on startup

create table if not exists app_config (
    key text primary key,
    value jsonb not null,
    description text,
    is_public boolean not null default true,
    updated_at timestamptz not null default now()
);

-- Seed default config
insert into app_config (key, value, description, is_public) values
    ('maintenance_mode', '{"enabled": false, "message": "App is under maintenance. Please try again later."}'::jsonb, 'Enable maintenance mode with custom message', true),
    ('force_update', '{"enabled": false, "min_version_code": 1, "message": "A new version is required. Please update from the store."}'::jsonb, 'Force update with minimum version code', true),
    ('min_app_version', '{"min_version_code": 1, "message": "This version is no longer supported. Please update."}'::jsonb, 'Minimum supported app version', true),
    ('default_playlist_url', '"https://iptv-org.github.io/iptv/countries/za.m3u"'::jsonb, 'Default playlist URL for first launch', true),
    ('epg_auto_refresh_hours', '6'::jsonb, 'EPG auto-refresh interval in hours', true),
    ('max_buffer_size_mb', '50'::jsonb, 'Maximum buffer size for playback in MB', true),
    ('enable_vidsrc_movies', 'true'::jsonb, 'Enable VidSrc movies/TV shows feature', true),
    ('vidsrc_base_url', '"https://vidsrc.sh"'::jsonb, 'VidSrc API base URL', true)
on conflict (key) do nothing;

-- Trigger to auto-update updated_at
create or replace function update_app_config_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end $$;

create trigger update_app_config_updated_at
    before update on app_config
    for each row execute function update_app_config_updated_at();

-- RLS: public read for public configs, admin write
alter table app_config enable row level security;

-- Public can read public configs
create policy "Public read app config"
    on app_config
    for select
    using (is_public = true);

-- Admin can do everything (hardcoded emails)
create policy "Admin full access app config"
    on app_config
    for all
    using (auth.jwt() ->> 'email' in ('adam@tribal.tv', 'admin@tribal.tv'))
    with check (auth.jwt() ->> 'email' in ('adam@tribal.tv', 'admin@tribal.tv'));