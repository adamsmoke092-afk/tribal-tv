-- App configuration / feature flags table for Tribal TV
-- Managed via admin panel, consumed by the Android app on startup

create table if not exists app_config (
    key text primary key,
    value jsonb not null,
    description text,
    is_public boolean not null default true,
    updated_at timestamptz not null default now()
);

-- Seed default config.
--
-- App-level only. IPTV settings (default playlist, EPG refresh, buffer size)
-- are deliberately absent: the free tier is defined in the plan as needing no
-- backend, so its configuration lives in SettingsStore on the device. Adding
-- them here would make the IPTV path depend on a network read it must not need.
insert into app_config (key, value, description, is_public) values
    ('maintenance_mode', '{"enabled": false, "message": "App is under maintenance. Please try again later."}'::jsonb, 'Enable maintenance mode with custom message', true),
    ('force_update', '{"enabled": false, "min_version_code": 1, "message": "A new version is required. Please update from the store."}'::jsonb, 'Force update with minimum version code', true),
    ('min_app_version', '{"min_version_code": 1, "message": "This version is no longer supported. Please update."}'::jsonb, 'Minimum supported app version', true)
on conflict (key) do nothing;

-- Trigger to auto-update updated_at
create or replace function update_app_config_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end $$;

drop trigger if exists update_app_config_updated_at on app_config;
create trigger update_app_config_updated_at
    before update on app_config
    for each row execute function update_app_config_updated_at();

-- RLS: public read for public configs, admin write
alter table app_config enable row level security;

-- Public can read public configs
drop policy if exists "Public read app config" on app_config;
create policy "Public read app config"
    on app_config
    for select
    using (is_public = true);

-- Admin can do everything (hardcoded emails)
drop policy if exists "Admin full access app config" on app_config;
create policy "Admin full access app config"
    on app_config
    for all
    using (auth.jwt() ->> 'email' in ('adam@tribal.tv', 'admin@tribal.tv'))
    with check (auth.jwt() ->> 'email' in ('adam@tribal.tv', 'admin@tribal.tv'));