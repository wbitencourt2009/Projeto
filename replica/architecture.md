# Architecture: your IPTV player (a rebuild of XCIPTV's core features)

Working name until `/replica-brand`: **"Player"**, package `com.example.player`
(placeholder, replaced at the brand step). Nothing in the code is named after
XCIPTV.

Shape of the app: **one Android app, no backend.** It talks straight to the
IPTV server the user types in, stores everything on the device and plays the
streams. That is what XCIPTV is too, and it is why most Replica defaults
(Postgres, auth, payments, email, hosting) do not apply here.

## Stack

| layer | choice | why |
| --- | --- | --- |
| language | Kotlin 2.x, JDK 17 toolchain | the native Android language; every library below is Kotlin-first |
| UI (TV) | Jetpack Compose + `androidx.tv:tv-material` | Google's current toolkit for D-pad UIs on Android TV and Fire TV; replaces Leanback |
| UI (phone/tablet) | Jetpack Compose Material 3, same screens with a touch layout | one codebase, two layouts picked by `UiModeManager` / screen size |
| player (main) | AndroidX Media3 ExoPlayer (HLS, MPEG-TS, progressive) | the standard Android player; adaptive HLS, track selection, good TV support |
| player (fallback) | libVLC for Android (`org.videolan.android:libvlc-all`) | plays odd codecs/containers ExoPlayer refuses, same two-player model as the original |
| database | Room (SQLite) + FTS4 for search | local, fast, typed queries, migrations; see `schema.sql` |
| settings | Jetpack DataStore (Preferences) | small key/value settings, async, no SQL needed |
| network | OkHttp + Retrofit, kotlinx.serialization (streaming decode) | handles 50 MB JSON without loading it into memory as one string |
| EPG parsing | `XmlPullParser` streaming + `GZIPInputStream` | XMLTV files are big; parse row by row, insert in batches |
| images | Coil 3 | poster and logo loading with disk cache, Compose-native |
| background work | WorkManager (refresh), AlarmManager (reminders) | periodic refresh survives reboots; exact alarms for programme reminders |
| DI | Hilt | standard on Android, keeps the player/repository wiring testable |
| secrets on device | Android Keystore AES key, encrypt passwords and M3U urls | provider credentials are the most sensitive data the app holds |
| build | Gradle (version catalog), single `app` module + `core` packages | simple; split into modules only if build times hurt |
| distribution | signed release APK, sideloaded (own use) | no Play Store needed for personal use; Play listing later if it becomes a product |
| min / target SDK | minSdk 23 (Android 6), targetSdk 35 | covers cheap TV boxes still on Android 7-9 and Fire OS |

Not used, on purpose: no server, no accounts, no analytics, no ads, no crash
reporting that would send stream URLs anywhere.

## Schema

Tables: **11** (+ 1 FTS index). Access rules: data-layer checks: every DAO
query takes `profileId`, and only the active profile is read. See
[`schema.sql`](schema.sql).

| table | holds | from |
| --- | --- | --- |
| profiles | saved logins (Xtream / M3U / EZServer), encrypted secrets | S03 S04 S05 |
| account_info | status, expiry, connections | S07 S26 |
| categories | live / movie / series categories, lock and hide flags | S08 S13 S15 S24 |
| channels | live channels, EPG id, archive flag | S08 S09 S12 |
| programmes | EPG rows, unique per channel + start | S10 S11 |
| movies | VOD list, detail fields filled lazily | S13 S14 |
| series / episodes | series list, seasons and episodes | S15 S16 |
| favourites | per profile, keyed by remote id, insertion order | S19 |
| watch_progress | resume points and recent channels | S14 S20 |
| reminders | programme reminders | S11 |

Hard constraints the recon found, enforced in the database:

- one EPG row per channel and start time (`idx_programmes_slot`): re-importing a guide never duplicates
- favourites and progress keyed by **remote** id, so a content refresh (which rewrites local ids) keeps them
- favourites keep insertion order with an explicit `sort` (the bug XCIPTV fixed in 7.0)
- a profile must have either an M3U url or server + user + password (check constraint)

## API

There is no API of our own. The app is a client of two public formats:
the **Xtream Codes player API** (de facto standard, documented publicly but
unofficially: verify every field against a real panel) and **M3U + XMLTV**.

### Outbound calls (to the user's server)

| method path | does | input | output | flow |
| --- | --- | --- | --- | --- |
| GET `/player_api.php?username&password` | log in, account status | credentials | `user_info`, `server_info` | F01, S26 |
| GET `…&action=get_live_categories` | live categories | — | `[{category_id, category_name, parent_id}]` | F01, S08 |
| GET `…&action=get_live_streams[&category_id]` | all live channels | — | `[{stream_id, num, name, stream_icon, epg_channel_id, category_id, tv_archive, tv_archive_duration}]` | F01, S08 |
| GET `…&action=get_vod_categories` / `get_vod_streams` | movie list | — | `[{stream_id, name, stream_icon, rating, added, container_extension, category_id}]` | S13 |
| GET `…&action=get_vod_info&vod_id=` | movie detail | id | `info{plot, cast, director, genre, releasedate, duration_secs, backdrop_path, tmdb_id, youtube_trailer}` | S14 |
| GET `…&action=get_series_categories` / `get_series` | series list | — | `[{series_id, name, cover, plot, rating, last_modified, category_id}]` | S15 |
| GET `…&action=get_series_info&series_id=` | seasons and episodes | id | `{info, seasons, episodes{"1":[…]}}` | S16 |
| GET `…&action=get_short_epg&stream_id=&limit=` | now / next for one channel | id | `epg_listings[]` (base64 titles) | S08, S09 |
| GET `…&action=get_simple_data_table&stream_id=` | full day guide for one channel, with archive flags | id | `epg_listings[]` | S11, S12 |
| GET `/xmltv.php?username&password` | full guide, all channels | credentials | XMLTV (often gzip) | S10 |
| GET user's M3U url | channel list for M3U profiles | url | `#EXTINF` lines with `tvg-id`, `tvg-logo`, `group-title` | F02 |
| GET user's XMLTV url | guide for M3U profiles | url | XMLTV | F02, S10 |

### Stream URLs (built at play time, never stored in logs)

| kind | url |
| --- | --- |
| live | `{server}/live/{user}/{pass}/{stream_id}.{ts\|m3u8}` |
| movie | `{server}/movie/{user}/{pass}/{stream_id}.{container_ext}` |
| episode | `{server}/series/{user}/{pass}/{episode_id}.{container_ext}` |
| catch-up | `{server}/timeshift/{user}/{pass}/{duration_min}/{YYYY-MM-DD:HH-MM}/{stream_id}.ts` (variant: `/streaming/timeshift.php?…`; verify on the user's panel) |
| m3u | the url from the playlist line |

### In-app navigation (the "routes")

`login`, `profiles`, `loading`, `home`, `live/{categoryId}`, `player/live/{channelId}`,
`guide`, `catchup/{channelId}`, `movies/{categoryId}`, `movie/{id}`,
`series/{categoryId}`, `series/{id}`, `player/vod/{type}/{id}`, `search`,
`settings/*`. Compose Navigation, one `NavHost`.

Webhooks in / out: none.

### Background jobs

| job | schedule | does |
| --- | --- | --- |
| ContentRefreshWorker | on launch if older than `content_refresh_hours` (default 12 h), and manual (S25) | re-download categories and lists, upsert by remote id, delete what disappeared, rebuild FTS |
| EpgRefreshWorker | every `epg_refresh_hours` (default 12 h), unmetered or any network | stream-parse XMLTV, batch insert, delete programmes ended > 1 day ago (or > archive days) |
| AccountCheck | on launch | refresh `account_info`, warn on S07 if expired or close to expiry |
| ReminderAlarm | exact alarm per reminder | local notification "starts in 5 min", tap opens the channel |

## The parts that bite

- **Big data on weak hardware.** Cheap TV boxes have 1-2 GB RAM. Never hold a
  whole playlist in memory: stream-decode JSON/M3U/XMLTV, insert in
  transactions of ~1 000 rows, use Paging 3 for every list and grid.
- **EPG grid performance.** Hundreds of channels × days of programmes: lazy
  rows and columns, query only the visible time window, precompute the "now"
  line.
- **Time zones.** XMLTV times carry their own offset (`20261014200000 +0000`);
  store UTC, show in the device zone, apply the user's `epg_time_shift` on top.
  Xtream `server_timezone` matters for catch-up start times. DST changes shift
  catch-up urls by an hour if handled naively.
- **TV focus.** Every screen must be usable with only D-pad + OK + Back:
  initial focus, focus restore when coming back, no focus traps, long-press as
  the "menu" action, number keys in the player.
- **Player robustness.** Retries with backoff on stream drops, switch to libVLC
  when ExoPlayer reports an unsupported format, keep one active stream at a
  time (providers limit `max_connections`; a preview tile plus a fullscreen
  player counts as two).
- **Cleartext HTTP.** Most IPTV panels are plain `http` on odd ports. Allow
  cleartext in the network security config (it is the user's own server), and
  accept self-signed HTTPS only if the user ticks a setting.
- **Credentials in URLs.** Xtream puts user and password in every stream url.
  Never log urls, strip them from crash traces, encrypt them at rest.
- **Idempotent refresh.** Upsert by `(profile_id, remote_id)` so a refresh that
  fails halfway leaves the old data usable.
- **Provider quirks.** Base64 EPG titles, `series_info` keyed `"1"`, `"2"`,
  numbers sent as strings, missing fields, `user_info.auth = 0` for wrong
  passwords with HTTP 200. Parse leniently, default everything.
- **Android 12+ exact alarms.** Reminders need `SCHEDULE_EXACT_ALARM` or fall
  back to inexact; Android 13+ needs the notification permission.
- **Fire TV.** No Google Play Services: nothing in the app may depend on them.

Not applicable: payments, email, multi-tenancy, GDPR deletion (no data
leaves the device; "delete profile" deletes everything).

## Build order

1. **Vertical slice** (proves the stack on a real TV box)
   - screens: S01, S03, S06, S07 (Live TV tile only), S08, S09
   - tables: profiles, account_info, categories, channels
   - calls: `player_api.php` login, `get_live_categories`, `get_live_streams`, live stream url
   - done when: log in with a real Xtream account on a TV box, pick a category,
     a channel plays fullscreen, up/down zaps.

2. **Must-haves**, by area
   - login: S02, S04 (M3U + external EPG), S26
   - EPG: programmes table, EpgRefreshWorker, now/next on S08 and S09, S10 grid
   - VOD: S13, S14, S17 (movies table, `get_vod_*`)
   - series: S15, S16, S17 (series, episodes, `get_series*`)
   - favourites S19, search S18 (FTS), error and retry states on S09/S17
   - phone layout for every screen above

3. **Should-haves**
   - catch-up S11, S12 (timeshift urls), reminders
   - resume / continue watching S20, next episode, tracks and aspect ratio
   - libVLC fallback and player-per-category S22
   - parental PIN S24, S27; profiles S05; refresh S25; EPG time shift S23

4. **Could-haves**: backup/restore, DVR recording, EZServer, trailers, hidden
   categories, decoder/buffer settings, language, 12/24 h.

5. **Fixes from replica-entrepreneur**: added once it has run.
