-- Per-principal entitlement and bounds for the VOD (Movies/TV) tier.
-- One row per auth.users principal, created by trigger on signup.
--
-- The IPTV tier never reads this table: it is free and anonymous, and it
-- works with no Supabase session at all.

create table if not exists vod_access (
    user_id uuid primary key references auth.users(id) on delete cascade,
    is_active boolean not null default true,
    device_limit integer not null default 2
        check (device_limit between 1 and 10),
    rate_limit_per_hour_per_device integer not null default 60
        check (rate_limit_per_hour_per_device between 1 and 1000),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create or replace function update_vod_access_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end $$;

drop trigger if exists update_vod_access_updated_at on vod_access;
create trigger update_vod_access_updated_at
    before update on vod_access
    for each row execute function update_vod_access_updated_at();

-- Provision on signup. Without this a new principal has no row, and the
-- proxy's is_active check either 403s every new user or silently defaults
-- to allow -- neither is a decision anyone made.
--
-- security definer is required: the trigger runs as supabase_auth_admin,
-- which has no rights on public.vod_access. search_path is pinned so the
-- function cannot be hijacked by a shadowing schema.
create or replace function create_vod_access()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    insert into public.vod_access (user_id) values (new.id)
    on conflict (user_id) do nothing;
    return new;
end $$;

drop trigger if exists on_auth_user_created_vod on auth.users;
create trigger on_auth_user_created_vod
    after insert on auth.users
    for each row execute function public.create_vod_access();

-- Backfill: principals created before this migration (test accounts,
-- dashboard-created users) would otherwise 403 on their first request.
insert into vod_access (user_id)
select id from auth.users
on conflict (user_id) do nothing;

-- RLS: service-role only. The proxy reads and writes this table with the
-- service key; the app never queries it directly. No policy rows and no
-- grants to anon/authenticated, so PostgREST returns nothing even with the
-- anon key that ships in the APK.
--
-- An owner-read policy was considered and dropped: `revoke all` plus a
-- policy `to authenticated` is a contradiction -- the policy can never
-- fire without a SELECT grant. If the app ever needs to show its own
-- entitlement, add a /vod/me route to the proxy instead of a second
-- auth path.
alter table vod_access enable row level security;
revoke all on vod_access from public, anon, authenticated;
grant all on vod_access to service_role;
revoke all on function public.create_vod_access() from public, anon, authenticated;
