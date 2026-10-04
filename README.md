# Tribal TV

Personal Android IPTV player. Loads an M3U playlist (default: South Africa
channels from [iptv-org](https://github.com/iptv-org/iptv)), caches it in a
Room database, and plays channels with a VPN-tunnel-tuned ExoPlayer — deep
buffers, long HTTP timeouts, and a retrying data source with exponential
backoff that fails fast on 4xx (geo-blocks) instead of pointlessly retrying.

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
- Rotating the device returns to the channel list (player state isn't
  retained across activity recreation yet — fine for v0.1).
