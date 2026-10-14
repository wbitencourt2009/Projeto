# Components: IPTV player, futuristic dark theme

Look: **cinema-first and futuristic.** Big hero artwork that fades into a
near-black space-blue background, horizontal rows of posters, glass panels,
and a neon cyan focus glow that follows the remote. The *pattern* (hero plus
rows) is the common streaming-app layout. Nothing is taken from any one
service: no red-and-black palette, no logos, no copy, open fonts and open
icons only.

- Fonts: **Sora** for display and titles, **Inter** for everything else
  (both SIL OFL, bundled as font resources).
- Icons: **Material Symbols Rounded** (Apache 2.0), as vector drawables.
- Artwork: posters, logos and backdrops come from the user's own provider at
  run time. The app ships none. Placeholders are generated gradients with the
  title's initials.
- Tokens: `tokens.json` -> `Theme.kt` (`AppColors`, `AppType`, `AppShapes`,
  `AppSpacing`). Components use roles (`AppColors.surface`), never raw hex.
- TV rule: every focusable item shows the **focus glow** (scale 1.08, 3dp
  `focus` ring, 24dp soft `focus` glow) within `motion.fast`. Touch on phone
  uses ripple instead. Contrast: 24 pairs, 0 AA failures.

```
Focus glow  (shared modifier: Modifier.tvFocus())
  states    unfocused, focused (scale 1.08, ring 3dp focus, glow 24dp focus @45%), pressed (scale 1.02)
  motion    fast 120ms, ease
  a11y      focus is never only colour: scale + ring; initial focus set on every screen; focus restored on Back
  used on   every focusable item
```

```
Nav rail  (TV) / bottom bar (phone)
  items     Início, Ao vivo, Guia, Filmes, Séries, Busca, Favoritos, Ajustes
  states    collapsed 72dp (icons), expanded 240dp on focus (icons + labels, glass bg), item selected (accent bar 4dp + accent icon), item focused (focus glow)
  tokens    bg glass, icon text-muted -> text on focus, selected accent
  a11y      content descriptions on icons; Back from content opens the rail; Back on rail = exit confirm
  used on   S07, S08, S10, S13, S15, S18, S19, S21
```

```
Hero banner
  variants  movie/series (backdrop + title + meta + plot 2 lines + actions), live (channel logo + now-playing + progress + LIVE badge)
  layout    height 420dp TV / 56% width on phone, left scrim + bottom scrim into bg
  states    loading (shimmer), filled, crossfade 600ms when the focused row item changes (debounced 400ms)
  tokens    title display/Sora, meta caption text-muted, plot body text-muted, scrim from bg
  actions   Assistir (primary button), Mais info (secondary), favourite (icon button)
  used on   S07, S13, S15, S14 (as detail header)
```

```
Content row  (LazyRow)
  variants  posters 2:3, landscape 16:9 (channels, continue watching), compact chips (categories)
  parts     row title (title/Sora) + optional "Ver tudo", items with 16dp gap, 48dp start padding (overscan)
  states    loading (5 shimmer cards), empty (row hidden), filled
  behaviour focused item scrolls to keep 1 item of peek on the left; Up/Down jumps between rows keeping column
  used on   S07, S13, S15, S19, S20
```

```
Poster card
  size      150 x 225dp, radius md
  states    default, focused (focus glow + title label under card), watched (progress bar 4dp accent at bottom), favourite (small accent-2 heart), no image (gradient + initials)
  tokens    surface-raised placeholder, text label
  used on   S13, S15, S19, S20, S18
```

```
Channel card / channel row
  variants  card 260 x 146dp (logo on glass, now-playing title, progress), row 72dp (number, logo 48dp, name, now title + progress)
  states    default, focused, playing (accent dot + "Assistindo"), favourite, locked (lock icon, opens PIN)
  tokens    number mono text-faint, name label text, now caption text-muted, progress accent on border
  used on   S07, S08, S12, S19
```

```
Live badge
  "AO VIVO"  pill, bg live, text on-live, caption 700, pulsing dot (respects reduced motion)
  used on   S07 hero, S08, S09, S10 now cells
```

```
Category list (TV left column)
  states    default, focused (focus glow), selected (accent bar + text), locked (lock icon), favourites pinned on top
  used on   S08, S13, S15
```

```
EPG grid
  parts     sticky channel column 240dp, time ruler (30-min ticks, 360dp per hour), programme cells, NOW line (accent 2dp with glow)
  cell      states: past (text-faint, catch-up icon if archive), now (surface-raised + accent left edge), future, focused (focus ring, no scale to keep the grid aligned), reminder set (bell icon)
  states    loading (skeleton rows), no EPG for channel ("Sem guia" in text-faint), filled
  a11y      D-pad moves cell by cell; OK opens programme detail; long-press = reminder
  used on   S10, mini now/next in S08 and S09
```

```
Player overlay
  live      top: channel logo, number, name, clock; bottom glass bar: now title, time range, progress, next title; quick list on OK
  vod       top: title, episode; bottom glass bar: seek bar, elapsed / remaining, play/pause, -10/+10s, audio, subtitles, aspect, next episode
  states    hidden, shown (auto-hide 4s), buffering (accent ring spinner), error (glass card + Tentar novamente + Trocar player)
  used on   S09, S17
```

```
Seek bar
  track border, buffered text-faint, played accent, thumb 16dp focus glow when focused; D-pad left/right seeks 10s, hold accelerates
  used on   S17, S12
```

```
Button
  variants  primary (accent bg, on-accent text), secondary (glass bg, border, text), ghost (text only), danger (danger text on glass)
  sizes     md 48dp TV (min touch 48dp phone), lg 56dp
  states    default, focused (focus glow), pressed, disabled (40% opacity), loading (spinner, label kept for TalkBack)
  tokens    radius pill, label/Inter 600
  used on   S03, S04, S14, S16, S09 error, dialogs
```

```
Icon button
  48dp circle, glass bg; focused = focus glow; contentDescription required
  used on   hero, player overlay, top bars
```

```
Text field
  variants  text, url (shows "http://" hint), password (toggle show)
  states    default (border-input), focused (focus 2dp + glow), error (danger border + message under), disabled
  behaviour TV: OK opens the system keyboard; Next moves to the next field
  used on   S03, S04, S18, S24
```

```
Login type tile
  big glass tile 320 x 200dp, icon 48dp, title, one-line hint; focused = focus glow
  used on   S02
```

```
Progress steps (loading content)
  steps     Canais, Filmes, Séries, Guia; each pending / running (spinner) / done (success check) / failed (danger + retry)
  used on   S06
```

```
Dialog / glass sheet
  variants  info, confirm, error, programme detail
  states    open (fade + scale 0.96 -> 1, motion base), focus trapped inside, Back closes
  used on   S11, errors, exit confirm, delete profile
```

```
PIN pad
  4 dots, number grid for touch, D-pad number keys on TV; wrong PIN = shake + danger message
  used on   S24, S27
```

```
Toast / snackbar
  glass pill bottom-centre, 3s, optional action
  used on   favourite added, refresh done, errors
```

```
Settings row
  variants  navigation (chevron), toggle, radio, value (current value on right)
  states    default, focused (focus glow on the whole row)
  used on   S21-S25
```

```
Empty state
  icon 64dp text-faint, title, one line, optional action button
  copy      written fresh, e.g. "Nada nos favoritos ainda. Segure OK em um canal ou filme para adicionar."
  used on   S19, S18, S08 empty category, S10 no guide
```

```
Shimmer / skeleton
  surface -> surface-raised sweep 1.2s; static when reduced motion is on
  used on   all loading states
```
