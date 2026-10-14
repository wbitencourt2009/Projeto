-- On-device database (SQLite via Room). Version 1.
-- There is no server database: every row belongs to one Profile on this device.
--
-- Deviations from the Replica defaults, on purpose:
--   * ids are INTEGER PRIMARY KEY (rowid). No sync between devices, so uuids buy nothing
--     and cost space on 50k-channel playlists.
--   * times are INTEGER epoch milliseconds in UTC (SQLite has no timestamptz).
--   * there is no money and no auth server; "access rules" = every query in the
--     DAOs filters by profile_id, and the active profile is the only one read.

PRAGMA foreign_keys = ON;

create table profiles (
  id               integer primary key,
  name             text    not null,
  type             text    not null check (type in ('xtream','m3u','ezserver')),
  server_url       text,                      -- xtream / ezserver, normalised: scheme://host:port
  username         text,
  password_enc     blob,                      -- encrypted with an Android Keystore key, never plain
  m3u_url_enc      blob,                      -- m3u urls usually embed credentials too
  epg_url          text,                      -- optional external XMLTV
  user_agent       text,
  last_refresh_at  integer,
  created_at       integer not null,
  updated_at       integer not null,
  check ((type = 'm3u' and m3u_url_enc is not null)
      or (type <> 'm3u' and server_url is not null and username is not null and password_enc is not null))
);

create table account_info (
  profile_id       integer primary key references profiles(id) on delete cascade,
  status           text,                      -- Active, Expired, Banned, Disabled (as sent by the server)
  exp_date         integer,                   -- epoch ms, null = unlimited
  max_connections  integer,
  active_cons      integer,
  is_trial         integer not null default 0,
  server_timezone  text,
  fetched_at       integer not null
);

create table categories (
  id          integer primary key,
  profile_id  integer not null references profiles(id) on delete cascade,
  kind        text    not null check (kind in ('live','movie','series')),
  remote_id   text    not null,
  name        text    not null,
  parent_id   text,
  sort        integer not null default 0,
  is_locked   integer not null default 0,     -- parental PIN required
  is_hidden   integer not null default 0,
  unique (profile_id, kind, remote_id)
);
create index idx_categories_profile_kind_sort on categories (profile_id, kind, sort);

create table channels (
  id               integer primary key,
  profile_id       integer not null references profiles(id) on delete cascade,
  category_id      integer references categories(id) on delete set null,
  remote_stream_id text    not null,          -- xtream stream_id, or a hash of the m3u url
  number           integer,
  name             text    not null,
  logo_url         text,
  epg_channel_id   text,                      -- xtream epg_channel_id / m3u tvg-id
  direct_url       text,                      -- m3u only; xtream urls are built at play time
  container_ext    text,
  has_archive      integer not null default 0,
  archive_days     integer not null default 0,
  sort             integer not null default 0,
  unique (profile_id, remote_stream_id)
);
create index idx_channels_category_sort on channels (category_id, sort);
create index idx_channels_profile_number on channels (profile_id, number);
create index idx_channels_epg on channels (epg_channel_id);

create table programmes (
  id              integer primary key,
  profile_id      integer not null references profiles(id) on delete cascade,
  epg_channel_id  text    not null,
  start_at        integer not null,
  end_at          integer not null,
  title           text    not null,
  description     text,
  check (end_at > start_at)
);
create unique index idx_programmes_slot on programmes (profile_id, epg_channel_id, start_at);
create index idx_programmes_end on programmes (profile_id, end_at);   -- purge old rows

create table movies (
  id               integer primary key,
  profile_id       integer not null references profiles(id) on delete cascade,
  category_id      integer references categories(id) on delete set null,
  remote_stream_id text    not null,
  name             text    not null,
  poster_url       text,
  rating           real,
  added_at         integer,
  container_ext    text,
  -- detail fields, filled lazily by get_vod_info when the detail page opens
  backdrop_url     text,
  year             integer,
  genre            text,
  plot             text,
  cast_list        text,
  director         text,
  duration_secs    integer,
  tmdb_id          text,
  trailer_url      text,
  details_fetched_at integer,
  sort             integer not null default 0,
  unique (profile_id, remote_stream_id)
);
create index idx_movies_category_sort on movies (category_id, sort);
create index idx_movies_profile_added on movies (profile_id, added_at desc);

create table series (
  id               integer primary key,
  profile_id       integer not null references profiles(id) on delete cascade,
  category_id      integer references categories(id) on delete set null,
  remote_series_id text    not null,
  name             text    not null,
  cover_url        text,
  backdrop_url     text,
  plot             text,
  cast_list        text,
  genre            text,
  rating           real,
  year             integer,
  last_modified    integer,
  details_fetched_at integer,
  sort             integer not null default 0,
  unique (profile_id, remote_series_id)
);
create index idx_series_category_sort on series (category_id, sort);

create table episodes (
  id                 integer primary key,
  series_id          integer not null references series(id) on delete cascade,
  remote_episode_id  text    not null,
  season             integer not null,
  episode_num        integer not null,
  title              text,
  plot               text,
  duration_secs      integer,
  still_url          text,
  container_ext      text,
  unique (series_id, remote_episode_id)
);
create index idx_episodes_series_season on episodes (series_id, season, episode_num);

create table favourites (
  id          integer primary key,
  profile_id  integer not null references profiles(id) on delete cascade,
  item_type   text    not null check (item_type in ('channel','movie','series')),
  remote_id   text    not null,             -- remote ids survive a content refresh, local ids do not
  sort        integer not null,             -- insertion order; new ones go to the end
  created_at  integer not null,
  unique (profile_id, item_type, remote_id)
);
create index idx_favourites_list on favourites (profile_id, item_type, sort);

create table watch_progress (
  id           integer primary key,
  profile_id   integer not null references profiles(id) on delete cascade,
  item_type    text    not null check (item_type in ('channel','movie','episode')),
  remote_id    text    not null,
  series_remote_id text,                    -- for episodes: lets "continue watching" show the series
  position_ms  integer not null default 0,
  duration_ms  integer,
  updated_at   integer not null,
  unique (profile_id, item_type, remote_id)
);
create index idx_progress_recent on watch_progress (profile_id, updated_at desc);

create table reminders (
  id               integer primary key,
  profile_id       integer not null references profiles(id) on delete cascade,
  channel_remote_id text   not null,
  epg_channel_id   text    not null,
  programme_start  integer not null,
  title            text    not null,
  notify_at        integer not null,
  unique (profile_id, epg_channel_id, programme_start)
);
create index idx_reminders_due on reminders (notify_at);

-- Search: full-text index over names (Room @Fts4 entity, content-synced).
create virtual table search_index using fts4(
  name, kind, ref_id, profile_id, tokenize=unicode61 "remove_diacritics=2"
);

-- Settings live in Jetpack DataStore (per profile key prefix), not in SQL:
--   player_live / player_vod / player_series / player_catchup  ('exo' | 'vlc')
--   buffer_ms, hw_decoding, aspect_ratio, epg_time_shift_minutes,
--   epg_refresh_hours, content_refresh_hours, time_24h, language,
--   parental_pin_hash (PBKDF2 + salt), last_channel_remote_id
