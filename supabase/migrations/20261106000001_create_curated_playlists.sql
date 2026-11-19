-- Curated playlists table for Tribal TV
-- Managed via admin panel, consumed by the Android app

create table if not exists curated_playlists (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    description text,
    m3u_url text not null,
    logo_url text,
    category text not null check (category in ('country', 'fast', 'sports', 'news', 'entertainment', 'kids', 'music', 'other')),
    country_code char(2),
    is_active boolean not null default true,
    sort_order integer not null default 0,
    metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- Index for active playlists ordered by sort_order
create index if not exists idx_curated_playlists_active
    on curated_playlists (is_active, sort_order, category)
    where is_active = true;

-- Trigger to auto-update updated_at
create or replace function update_updated_at_column()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end $$;

drop trigger if exists update_curated_playlists_updated_at on curated_playlists;
create trigger update_curated_playlists_updated_at
    before update on curated_playlists
    for each row execute function update_updated_at_column();

-- RLS: public read for active playlists, admin write
alter table curated_playlists enable row level security;

-- Public can read active playlists
drop policy if exists "Public read active playlists" on curated_playlists;
create policy "Public read active playlists"
    on curated_playlists
    for select
    using (is_active = true);

-- Admin can do everything (hardcoded emails)
drop policy if exists "Admin full access playlists" on curated_playlists;
create policy "Admin full access playlists"
    on curated_playlists
    for all
    using (auth.jwt() ->> 'email' in ('adam@tribal.tv', 'admin@tribal.tv'))
    with check (auth.jwt() ->> 'email' in ('adam@tribal.tv', 'admin@tribal.tv'));