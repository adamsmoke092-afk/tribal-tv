# Tribal TV

Personal Android IPTV player. Loads an M3U playlist (default: South Africa
channels from [iptv-org](https://github.com/iptv-org/iptv)), caches it in a
Room database, and plays channels with a VPN-tunnel-tuned ExoPlayer — deep
buffers and long HTTP timeouts, plus a bounded, classification-aware retry
policy on the media source (see "Playback reliability" below).

Built entirely via GitHub Actions — the dev device can't run Gradle.

## Dev loop

1. Edit files locally (Termux).
2. Commit and push:
   ```bash
   git add .
   git commit -m "describe the change"
   git push
   ```
3. GitHub Actions builds the debug APK (~5–15 min): repo → **Actions** tab →
   latest run → **Artifacts** → `tribal-tv-debug-apk`.
4. Download, unzip, install `app-debug.apk`.

## Project layout

| Path | What |
|---|---|
| `app/src/main/java/.../playlist/` | M3U parser, Room cache, repository |
| `app/src/main/java/.../player/` | ExoPlayer factory + retrying DataSource |
| `app/src/main/java/.../*.kt` | MainActivity, channel list + player screens |
| `.github/workflows/build.yml` | CI: JDK 17 → Gradle → assembleDebug → artifact |

## Notes

- Default playlist: `https://iptv-org.github.io/iptv/countries/za.m3u` —
  change `playlistUrl` in `MainActivity.kt` to point anywhere else.
- Some channels in public playlists are geo-blocked or dead. Those surface an
  inline "not playable" message on the player screen — by design, not a bug.
- First launch fetches the playlist over the network; later launches serve
  from the Room cache until you hit the refresh button.
- App state lives in a ViewModel, so rotation and other activity
  recreations keep you where you were (the player keeps playing either
  way — it lives in PlaybackService).
- Streams can carry `#EXTVLCOPT` HTTP headers (user-agent/referrer/origin);
  the app parses them and sends them with every request for that stream.
- EPG: NOW/NEXT on the player. Configuration is per playlist — a playlist's
  own `url-tvg` / `x-tvg-url` header is used unless you set a manual guide URL
  for that playlist in Settings (which wins). Several comma-separated URLs get
  fetched and merged, so mixed playlists can combine country guides. The
  iptv-org country auto-pair was removed: its generated URLs were unreliable.
  The cache is scoped to your playlist's `tvg-id`s and a 48h window to stay
  light; channels not in any guide show nothing. Settings also shows a compact
  per-source status (loaded / failed / truncated, unmatched ids with
  unconfirmed suggestions), plus an optional "Loose quality matching" switch
  for guides that tag the same channel `name` vs `name@HD`.
- Upgrading from the pre-source EPG cache (database v3 → v4): only the cached
  EPG table is dropped and recreated, so **existing EPG schedules are
  discarded and re-downloaded on the next guide refresh**. Channels, playlists,
  favourites and every other setting are untouched.

## Playback reliability

Retry behaviour is a custom `LoadErrorHandlingPolicy` wired through
`DefaultMediaSourceFactory` (Media3's supported mechanism) — not a wrapper
around the `DataSource`.

- **Retried:** socket timeouts, connection refused/reset, DNS failures,
  HTTP 5xx, and HTTP 408/429. Backoff is exponential and capped, for at most
  three retries per load. A valid `Retry-After` on 429 is honoured (capped).
- **Not retried (fail fast):** 401/403/404 and all other 4xx, plus parsing
  and unsupported-format errors. Retrying a geoblock or a missing stream only
  wastes time before the same failure.
- Buffering for a long time shows a *warning*; it never marks a channel
  "dead" on its own. Only a genuine playback error does, and a successful
  playback forgives the channel.
- `#EXTVLCOPT` headers are injected per request (playlist **and** segments)
  by `ResolvingDataSource`, so retries never drop them.
