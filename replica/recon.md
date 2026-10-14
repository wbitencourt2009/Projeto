# Recon map: XCIPTV (Android TV + phone)

Scope: the core of an IPTV player. Xtream Codes and M3U login, live TV with
categories, the EPG guide, movies, series, favourites, search, playback, and
D-pad navigation for TV remotes.
For: the user's own use (personal player, not published).
Date: 2026-10-14

The clone is a **player only**. Like XCIPTV, it ships with no channels,
playlists or provider. The user brings their own subscription or M3U.

## Sources

| # | source | URL | notes |
| --- | --- | --- | --- |
| 1 | Play Store developer page | https://play.google.com/store/apps/developer?id=OTTRUN | developer OTTRUN, 4.0 stars, package `com.nathnetwork.xciptv` |
| 2 | APKMirror listing (store description mirror) | https://www.apkmirror.com/apk/ottrun/xciptv-player | features: Xtream/EZServer/M3U EPG, catch-up, VOD with IMDb info, series, favourites, backup/restore, DVR, reminders, ExoPlayer + VLC |
| 3 | APKMirror 7.0 release notes | https://apkmirror.com/apk/ottrun/xciptv-player/xciptv-player-7-0-release | VLC downgraded to 3.6.0, EPG fixes, favourites ordering fix |
| 4 | APKMirror 6.0 release notes | https://www.apkmirror.com/apk/ottrun/xciptv-player/xciptv-player-6-0-release/ | built-in OpenVPN removed |
| 5 | Provider setup guide | https://iptvsupportcenter.zohodesk.eu/portal/en/kb/articles/xciptv | login flow: Xtream Codes login, server URL, username, password |
| 6 | Setup guide | https://moviesnova.ca/set-up-iptv-with-xciptv-player | home sections, server URL with http and port |
| 7 | Firestick guide | https://troypoint.com/xciptv-apk/ | Settings bottom right on home, player per category |
| 8 | Android login guide | https://ge.m3uiptv.com/xciptv-player-android-setup-login-guide/ | playlist name field before Xtream credentials |
| 9 | Community Xtream API docs (unofficial) | https://drm-free.duckdns.org/thegame_1980/xtream-codes-api-documentation/src/branch/master/ENDPOINT_REFERENCE.md | `player_api.php` actions, `xmltv.php`, stream URL patterns. Unofficial, verify against a real panel |
| 10 | Fermata discussion on Xtream API | https://github.com/AndreyPavlenko/Fermata/discussions/434 | `get_series_info` param naming, `get_short_epg` |

Not used: the APK itself. Per the recon rules, the app is not decompiled and
its network calls are not logged. The Xtream Codes API is a de facto public
protocol implemented by many players and documented publicly (sources 9-10).

Gaps to fill with the user's own install (optional): exact home layout,
every settings label, player overlay controls. Screenshots go in
`replica/screens/` and are reference only.

## Core loop

Open the app on the TV, pick a channel or a movie with the remote, and it
plays instantly, with the guide showing what is on now and next.

## Screens

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Splash | app launch | load saved profile, refresh data | logo, progress | loading, offline |
| S02 | Login type picker | first launch, or "add user" | choose Xtream Codes, M3U URL or EZServer | large tiles | default |
| S03 | Xtream login form | S02 -> Xtream | enter playlist name, server URL (http/https + port), username, password | text fields, on-screen keyboard, sign-in button | empty, filled, wrong credentials, server unreachable, account expired |
| S04 | M3U login form | S02 -> M3U | playlist name, M3U URL, optional EPG URL | text fields | empty, filled, invalid URL, parse error |
| S05 | Profiles / user list | settings or launch with more than one saved login | switch between saved playlists | list rows, add, edit, delete | single, many |
| S06 | Content loading | after login or refresh | download categories, streams, EPG | progress bar with steps (live, movies, series, EPG) | progress, partial failure |
| S07 | Home dashboard | after S06 | entry to every section | big tiles: Live TV, Movies, Series, Catch-up, TV Guide, Settings (bottom right); account expiry date; clock | filled, expired account warning |
| S08 | Live TV: categories + channels | S07 -> Live TV | browse channels by category | category column, channel list (number, logo, name, now-playing title), mini preview | loading, empty category, filled |
| S09 | Live player (fullscreen) | select channel | watch live | video, info overlay (channel, now/next, progress), channel up/down, quick channel list | buffering, playing, stream error, retry |
| S10 | TV Guide (EPG grid) | S07 -> TV Guide, or from S08 | see the schedule across channels | time ruler, channel rows, programme cells, now line, programme detail popup | loading, no EPG, filled |
| S11 | Programme detail / reminder | select a cell in S10 | read the description, set a reminder, play catch-up if past | modal | future, live, past with catch-up, past without |
| S12 | Catch-up | S07 -> Catch-up | channels with archive, pick a past day and programme | channel list, day picker, programme list | no archive, filled |
| S13 | Movies: categories + grid | S07 -> Movies | browse VOD | category column, poster grid, sort | loading, empty, filled |
| S14 | Movie detail | select poster | info and play | backdrop, poster, title, year, rating, genre, plot, cast, trailer button, play, resume, favourite | info missing, resume available |
| S15 | Series: categories + grid | S07 -> Series | browse series | same as S13 | loading, empty, filled |
| S16 | Series detail | select series | seasons and episodes | season tabs, episode list (thumb, title, duration, watched mark), play, favourite | loading, single season, many |
| S17 | VOD player | play from S14 / S16 | watch on demand | video, seek bar, play/pause, ±10 s, audio track, subtitles, aspect ratio, next episode | buffering, error, end of episode |
| S18 | Search | search icon in S08/S13/S15 | find channels, movies, series by name | search field, grouped results | empty query, no results, results |
| S19 | Favourites | category "Favourites" in S08/S13/S15 | the user's picks | same list/grid, reorder | empty, filled |
| S20 | Recently watched / continue watching | row or category | resume VOD, last channels | list with progress | empty, filled |
| S21 | Settings | S07 -> Settings | configure the app | list of setting groups | default |
| S22 | Settings: player | S21 | pick player per category (Live, VOD, Series, Catch-up), buffer size, decoder (HW/SW), aspect ratio | radio lists | default |
| S23 | Settings: EPG | S21 | EPG source, time shift, refresh interval | inputs | default |
| S24 | Settings: parental control | S21 | PIN, lock categories | PIN pad, category checkboxes | no PIN, PIN set |
| S25 | Settings: other | S21 | refresh content, clear cache, time format, language, backup/restore, account info, logout | rows | default |
| S26 | Account info | S25 or S07 | status, expiry, max connections | read-only card | active, expired |
| S27 | PIN prompt | opening a locked category | enter parental PIN | PIN pad | default, wrong PIN |

## Flows

```
F01 First login with Xtream Codes
    S01 -> S02 -> S03 -> S06 -> S07
    happy path clicks: 6 (choose Xtream, 4 fields, sign in)
    edge: URL without http, wrong port, wrong password, expired account,
          server down, very large playlist (50k+ channels) loading slowly

F02 First login with an M3U URL
    S01 -> S02 -> S04 -> S06 -> S07
    happy path clicks: 4
    edge: huge M3U, no EPG in the file, tvg-id not matching the EPG,
          gzip-compressed EPG, invalid M3U

F03 Watch a live channel
    S07 -> S08 (category) -> channel -> S09
    happy path clicks: 3 (Live TV, category, channel)
    edge: stream offline, slow buffering, format the default player can't decode
          (fall back to the second player), channel change with the remote up/down

F04 Zap between channels while watching
    S09: up/down or number keys -> next channel, OK -> quick list overlay
    happy path clicks: 1
    edge: end of category wraps, last-channel key

F05 Check the guide and set a reminder
    S07 -> S10 -> programme -> S11 -> reminder
    happy path clicks: 4
    edge: no EPG for the channel, time zone shift, reminder when the app is closed

F06 Watch a past programme (catch-up)
    S10 past cell -> S11 -> play   or   S07 -> S12 -> day -> programme
    happy path clicks: 4
    edge: channel without archive, archive older than the server keeps

F07 Watch a movie and resume later
    S07 -> S13 -> poster -> S14 -> play -> S17, later S14 "resume"
    happy path clicks: 4
    edge: no metadata, subtitles/audio tracks, resume position near the end

F08 Watch a series episode, then the next
    S07 -> S15 -> poster -> S16 -> season -> episode -> S17 -> next episode
    happy path clicks: 5
    edge: missing season numbers, watched markers

F09 Search
    any section -> S18 -> type -> result -> S09/S14/S16
    happy path clicks: 3 + typing on the remote keyboard
    edge: accents, no results, typing with a D-pad keyboard

F10 Add and use favourites
    long-press / menu on a channel or poster -> add; open "Favourites" category
    happy path clicks: 2
    edge: order of favourites (XCIPTV 7.0 fixed a bug here), favourites per profile

F11 Lock adult categories with a PIN
    S21 -> S24 -> set PIN -> pick categories; later S08 -> locked category -> S27
    happy path clicks: 6
    edge: forgot PIN, hide vs. lock

F12 Switch between saved playlists
    S21/S07 -> S05 -> pick -> S06 -> S07
    happy path clicks: 3

F13 Refresh content
    S25 -> refresh  (also automatic on launch after N hours)
    edge: refresh fails, keep the old cache
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| Home tile | large, small | default, focused (scale + glow), disabled | S07 |
| Category list | vertical column | default, focused, selected, locked | S08, S13, S15 |
| Channel row | with logo, without logo | default, focused, playing, favourite | S08, S19 |
| Poster card | movie, series | default, focused, watched progress, favourite | S13, S15, S19, S20 |
| EPG grid | full, mini (now/next) | loading, empty, focused cell, live cell, past cell | S10, S08, S09 |
| Player overlay | live, VOD | hidden, shown, auto-hide | S09, S17 |
| Seek bar | VOD, catch-up | idle, seeking, buffered range | S17 |
| Track picker | audio, subtitles, aspect | open, selected | S17 |
| Text field | text, URL, password | default, focused, error | S03, S04, S18 |
| On-screen keyboard | system IME on TV | open | S03, S04, S18 |
| PIN pad | 4 digits | empty, entering, error | S24, S27 |
| Modal / dialog | info, confirm, error | open | S11, errors |
| Toast | info, error | shown | everywhere |
| Settings row | navigation, toggle, radio, value | default, focused | S21-S25 |
| Progress screen | stepped | in progress, failed step | S06 |
| Focus ring | TV D-pad focus | focused | every focusable item (TV is driven by remote, not touch) |

## Inferred data model

```
Profile      id, name, type (xtream | m3u | ezserver), server_url, username,
             password (encrypted on device), m3u_url, epg_url, created_at,
             last_refresh_at
             evidence: S03, S04, S05, sources 5 and 8
             confidence: high

AccountInfo  profile_id, status, exp_date, max_connections, active_cons,
             is_trial, server_timezone
             evidence: S07 expiry, S26; Xtream user_info (source 9)
             confidence: high

Category     id, profile_id, type (live | movie | series), remote_id, name,
             parent_id, sort, is_locked, is_hidden
             evidence: S08, S13, S15, S24; get_*_categories (source 9)
             confidence: high

Channel      id, profile_id, category_id, remote_stream_id, number, name,
             logo_url, epg_channel_id (tvg-id), stream_url_template,
             has_archive, archive_days
             evidence: S08, S09, S12; get_live_streams, M3U attributes
             confidence: high

Programme    id, epg_channel_id, start_at, end_at, title, description,
             has_archive
             evidence: S10, S11; xmltv.php, get_short_epg (sources 9-10)
             confidence: high

Movie        id, profile_id, category_id, remote_stream_id, name, poster_url,
             backdrop_url, year, rating, genre, plot, cast, director,
             duration, container_ext, tmdb_id, trailer_url
             evidence: S13, S14; get_vod_streams, get_vod_info
             confidence: high (metadata fields vary by provider)

Series       id, profile_id, category_id, remote_series_id, name, cover_url,
             plot, cast, genre, rating, year
             evidence: S15, S16; get_series, get_series_info
             confidence: high

Episode      id, series_id, season, episode_num, title, plot, duration,
             still_url, container_ext, remote_episode_id
             evidence: S16; get_series_info
             confidence: high

Favourite    profile_id, item_type (channel | movie | series), item_id, sort
             evidence: S19, F10, 7.0 release notes
             confidence: high

WatchProgress profile_id, item_type, item_id, position_ms, duration_ms,
             updated_at
             evidence: S14 resume, S20
             confidence: medium

Reminder     profile_id, programme_id, channel_id, notify_at
             evidence: S11, source 2 ("program reminders")
             confidence: high

Settings     profile_id, player_per_type (json), buffer_ms, hw_decoding,
             aspect_ratio, epg_time_shift, epg_refresh_hours, time_24h,
             language, parental_pin_hash
             evidence: S22-S25, source 7
             confidence: medium (exact options are a guess)
```

Relationships: Profile 1-1 AccountInfo, Profile 1-n Category, Category 1-n
Channel / Movie / Series, Series 1-n Episode, Channel n-n Programme (via
epg_channel_id), Profile 1-n Favourite / WatchProgress / Reminder.

All data lives on the device (Room database). There is no backend: the
app talks directly to the server the user enters.

## Feature matrix

See `features.csv`. Must: 21, should: 14, could: 9, skip: 4.

## Out of scope (cannot or should not be cloned)

- **Any content, channel list or provider.** The clone ships empty, like
  XCIPTV. No bundled playlists, no default servers, no "free channels".
- **The XCIPTV name, logo, icon and look-alike branding.** /replica-brand
  picks your own name before anything leaves your device.
- **XCIPTV's white-label / reseller panel (OTTRUN's business).** Not in this
  slice.
- **Built-in VPN.** XCIPTV itself dropped it in 6.0.
- **Bypassing DRM or paid services.** The player only plays streams the user
  is entitled to.

## Size

Screens 27, flows 13, entities 12. Hard parts:

1. **Smooth TV navigation with the remote** (D-pad focus everywhere, Compose
   for TV / Leanback), which is most of what makes an IPTV app feel good.
2. **Big data, fast.** Playlists with 50k+ channels and multi-megabyte XMLTV
   guides: streaming parsers, Room with indexes, background refresh, an EPG
   grid that scrolls without lag.
3. **Playback robustness.** Media3/ExoPlayer for HLS and MPEG-TS, plus a
   fallback (libVLC) for streams ExoPlayer can't handle, buffering, retries,
   audio/subtitle tracks.

Size: **M** (a few weeks for the must-haves; the should list adds another
couple of weeks).
