# Tribal TV Audit

_Date: 2026-10-11_

Reviewed the actual Kotlin/manifest/Gradle/CI sources (read ~20 of 47 files, all the
load-bearing ones). This is a well-structured app — clean parser/Room/repository
separation, careful MediaController lifecycle, genuine unit tests. The findings below are
where real risk or correctness gaps live, ordered by severity.

---

## 🔴 Critical

### C1 — Signing key and its password are compromised-by-design in CI
Two committed workflow files burn the release signing identity:

- **`.github/workflows/generate-keystore.yml`**
  - Hardcodes the keystore/key password `tribaltv-release-2026` in the committed YAML.
  - **Prints the base64 of the private `.jks` signing key to the workflow step summary**
    (readable by anyone with repo/Actions read access).
  - **Uploads the `.jks` itself as a build artifact.**
- **`.github/workflows/build.yml`** (release job) hardcodes
  `TRIBAL_TV_KEYSTORE_PASSWORD: tribaltv-release-2026` and the key password in plaintext.

**Impact:** anyone who can read the repo or its Actions logs/artifacts can obtain **both**
the signing key and its password, then forge APKs signed as "Tribal TV". Android treats a
same-signature APK as a legitimate update — it can install over the real app and inherit its
identity. Secrets printed to logs/artifacts persist and are hard to scrub, so the current
keystore should be considered **burned**.

**Fix:**
1. **Regenerate the keystore** — the current one is compromised.
2. Store the keystore **and both passwords** as GitHub Actions **secrets** (never in YAML).
   `app/build.gradle.kts` already reads them from env vars — just feed those from secrets.
3. Remove the base64-to-summary step and the `.jks` artifact upload (or, if a backup is
   truly needed, encrypt it with a passphrase not stored in the repo).

_Mitigating context:_ this is a personal, not-yet-distributed app, which lowers real-world
exposure today — but the signing identity is still effectively public.

---

## 🟡 Medium

### M1 — Player-level infinite re-prepare loop (no backoff, no limit)
`player/PlayerFactory.kt` `loadChannel()`: on a recoverable error it calls `player.prepare()`
immediately, with no delay and no max-attempt cap, and `isRecoverable()` includes the broad
`ERROR_CODE_IO_UNSPECIFIED`. A stream that persistently fails with a "recoverable" code
produces a tight `error → prepare → error` loop → CPU/network/battery drain. The
DataSource-layer backoff does **not** cover this player-level loop. Add a bounded retry
count + delay (and reset it on successful playback).

### M2 — `RetryingDataSource.read()` retry can corrupt the stream
`player/RetryingDataSource.kt` wraps **both** `open()` and `read()` in `withRetry`. Retrying
`delegate.read()` after a *partial* read on a streaming HTTP source is unsafe — `read()`
isn't idempotent, so re-calling it on a half-consumed/broken stream can duplicate or corrupt
bytes. `open()` retry is sound; `read()` retry is the risk. Prefer retrying only `open()`, or
reopen the connection before retrying a read. Also note: `Thread.sleep` backoff (up to ~7s)
on the loading thread **plus** 25s connect/read timeouts × 3 means one bad segment can stall
for a long time.

### M3 — `allowBackup="true"` exposes playlist URLs / credentials
`AndroidManifest.xml` sets `allowBackup="true"`. Playlist URLs live in plaintext
SharedPreferences (`PlaylistStore`, `SettingsStore`), and Xtream-codes providers embed
`username=`/`password=` directly in the URL. That makes credentials extractable via
`adb backup` / cloud backup. Tribal-VPN correctly uses `allowBackup="false"`. Set it false
here too, or add backup rules excluding the prefs files.

---

## 🟢 Low

- **L1 — `PlaybackService` exported + unconditional `onGetSession`.** `exported="true"` and
  `onGetSession()` returns the session to any controller → any installed app can bind,
  control playback, and read media metadata (`mediaId` = stream URL, possibly with creds).
  Restrict by caller package in `onGetSession` if that matters.
- **L2 — `#EXTVLCOPT` / per-stream headers ignored.** `M3uPlaylistParser` drops
  `#EXTVLCOPT` (http-user-agent, http-referrer) and `#EXTGRP`. Streams that require a
  specific UA/Referer won't play — a real-world IPTV gap (documented as "ignored for now").
- **L3 — `PlaylistRepository.fetchRawText` hardening.** No explicit `responseCode` check, no
  response size cap (`readText()` on a user-set URL could OOM), no `User-Agent` (some servers
  reject the default Java UA), and cross-protocol redirects (http↔https) aren't followed.
- **L4 — Main-thread SharedPreferences init.** All `*Store` first-access + `ensureSeeded()`
  run in `onCreate()` on the main thread → disk IO on the UI thread (minor jank/ANR risk as
  prefs grow).
- **L5 — `release { isMinifyEnabled = false }`.** No shrinking/obfuscation for release —
  larger APK, easier to reverse. Minor for a personal app.

---

## ✅ Verified-good (keep these)

- Clean layering: parser ↔ Room ↔ repository with explicit `toEntity()/toDomain()` mapping.
- `ChannelDao.replaceAll` is `@Transaction` (no empty-table window); `refreshChannels`
  refuses to overwrite the cache with an empty parse result.
- `fallbackToDestructiveMigration` is justified (channel table is a re-fetchable cache), and
  user data (favourites/recents/playlists) is deliberately kept out of the destructible DB.
- `MainActivity` MediaController lifecycle is correct: `isDestroyed` guard on async connect,
  `future.cancel`, and releases both session and player.
- Thoughtful VPN-tuned buffering/timeouts; **4xx fail-fast** in the data source avoids
  pointless geoblock retries.
- Genuine JVM unit tests for pure logic (URL validation, stores, parser display, dead/recents).
- `usesCleartextTraffic="true"` is justified for public HTTP IPTV and documents a scope-down
  path (per-domain `network_security_config.xml`) for distribution.

---

## Recommended priority
1. **C1** — rotate the keystore and move all signing secrets out of committed YAML. Do this first.
2. **M3** — flip `allowBackup` to false (one line, protects credentials).
3. **M1 / M2** — bound the player re-prepare loop and stop retrying `read()`.
4. Then the L-items as polish, especially L2 (EXTVLCOPT) for real-world stream compatibility.
