# family-night-site — NEW version with the Zivarnet archive

Base: the live `index.html` from `alireza-spec/family-night-site` (fetched
2026-11-18 05:50 Tehran, 1,109,966 bytes), patched ONLY for the new archive.
Nothing else in the site was changed.

## What is in this folder
- `index.html` — patched live site (see below)
- `download-data/` — same folder layout as the old FN archive:
  - `archive-part-1.html` … `archive-part-16.html` (~2.8 MB each, ~44 MB total)
  - `download-index.json` — `{ "ttXXXXXXX": partNumber }`, 7,956 entries
  - `README.md` — counts and caveats for the archive itself

## Patches applied to index.html (all verified, all 13 script blocks pass `node --check`)
1. `DL_BASE`: `https://alireza-spec.github.io/FN/download-data/` →
   `https://alireza-spec.github.io/family-night-site/download-data/`
2. `loadDlPart`: parts 1–3 → parts 1–16.
3. `recordFromArchive`: new `nosub` group (a `NoSub` heading used to fall
   through to the previous group and misfile its links); season/episode now
   also read from `S01E02` in the link label / file name, because the Zivarnet
   archive has no `<p>Season N</p>` markers.
4. Both download-box renders: `nosub` included in the empty-check, totals and
   as a third group «بدون زیرنویس فارسی (NoSub)».
5. Archive browser: loads parts 1–16 (was 1–3), loading text «بخش n از ۱۶»,
   year also read from Zivarnet URLs (`/pnot/Movie/2026/…`).
6. Encoder pill: `Film2Media` and `YIFY` added (the Zivarnet encoders).
7. **v2 — ALL titles readable, not just the ones with an IMDb code:**
   `recordFromArchive(doc, imdb, num)` can now find a record by its H3 number
   (1–10,876) when there is no IMDb code. The archive browser keeps all 10,876
   titles (was 7,992 with an IMDb code only); entries without an IMDb code are
   keyed by H3 number, show «بدون کد IMDb», have no «مشاهده» (TMDB) button and
   no OMDb enrichment call, but their download sheet opens by H3 number and
   shows all their links. Verified on real entries: #9152 (no-IMDb series,
   48 links, seasons 1–3), #1 Subedaar (no-IMDb movie, 3 dubbed links),
   #9139 Abbott Elementary (153 SoftSub + 1 NoSub).
8. **v3 — old FN archive as fallback (new archive is priority):**
   `OLD_DL_BASE` = `https://alireza-spec.github.io/FN/download-data/`.
   `resolveDlRecord` tries the new archive first and the old archive only when
   the title is not there at all; the per-title download box shows
   «(از آرشیو قبلی)» when its links came from the old archive.
   The archive browser loads new parts 1–16 then old parts 1–3, skipping old
   titles the new archive already has (by IMDb code). Merged, verified by
   emulation: **18,472 titles** (10,876 new + 7,596 old-only), **235,904 links**
   (201,949 new + 33,955 old). The old archive's own index is stale (5,667 of
   its 17,941 entries are not in its parts), so the merge is computed from the
   actual part files, not from the old index.
9. **v4 — links MERGE per title (not title-level fallback):**
   A title in both archives shows the UNION of its links, deduped by URL
   (`mergeDlRecs`): `resolveDlRecord` (per-title download box) reads both
   archives and merges; the archive browser merges old chunks into the
   existing entry (`entryByImdb`, `hasOld`/`oldPart`) and `openArchiveEntry`
   merges the old part's record the same way. Titles whose links came from
   both archives show «(آرشیو جدید + قبلی)». Verified by emulation: all
   4,709 titles present in both archives get their old links merged in
   (+17,132 links), **235,885 unique links** shown in total (19 exact
   duplicate URLs deduped), 18,472 entries unchanged.
10. **v5 — links sorted smallest-size-first:**
    Inside every group (SoftSub / Dubbed / NoSub, per season for series) the
    links are now ordered from the smallest size to the largest
    (`sizeSortMB`: links with no verified size sort LAST, not first).
    Verified on a realistic set: 480p 732 MB → 720p 1.41 GB → 1080p 2.71 GB
    → 2160p 8.40 GB → unsized links.
11. **v6 — download box no longer depends on the worker for the IMDb id:**
    When TMDB `external_ids` fails (worker blocked without VPN) the box
    used to show «no link» for every title. Now `loadDownloadBox` falls
    back to `findImdbByTitle` over the archives themselves. Two real bugs
    found and fixed in that fallback: it only searched new parts 1–3 (now
    all 16 new + 3 old parts); and its year check looked only at visible
    text, but the year lives only inside link URLs (hrefs) — now both are
    checked, a trailing year in the H3 («Subedaar 2026») is stripped before
    matching, and an exact title match with no year anywhere (e.g. series
    URLs without a year) is kept as a fallback. Emulation verified:
    Shawshank 1994 → tt0111161, Subedaar 2026 → tt31852278 (old archive),
    The 12 Dates of Christmas 2026 → tt39219651, Abbott Elementary 2021 →
    tt14218830, nonexistent title → ''.

## Verified before delivery
- The 16 parts are byte-identical to the extracted archive; H3 numbering is
  continuous 1–10,876; `download-index.json` maps every IMDb code to the part
  it is actually in (6 cross-part duplicates exist — same title twice, e.g.
  Final Destination 3 / Bohemian Rhapsody — the index points at the first one).
- Parser logic emulated on real entries: a series entry yields seasons 01–03,
  episodes 1–12 (48 links); a NoSub entry (Abbott Elementary, tt14218830,
  part 4) yields SoftSub + NoSub groups with its NoSub link correctly separated.

## Known limits (unchanged from the archive)
- 175,341 of 201,949 links have no size text (7 of 8 CDN nodes return 503
  from a foreign IP). The parser tolerates a missing size — it just shows no
  size pill. Do not invent sizes.
- 2,884 titles have no IMDb code: they are in the archive files and now
  readable in the archive browser + download sheet by H3 number (see v2 patch
  above), but they are NOT in `download-index.json`, so the per-title download
  box on a TMDB title page still cannot find them until their IMDb codes are
  matched (TMDB title+year). That matching is a separate step, not done here.
- 91 titles could not be fetched at all (pages redirect to login); 64 old
  series have season-folder links only. Both lists are in
  `~/workspace/zivarnet-full/` (`unfetchable.json`, summary JSON).

## v7 — TMDB title index fallback for the download box (2026-11-18 ~06:25)
- The 2,884 no-IMDb titles were matched to TMDB (strict title+year, worker
  `family-night-api`): 2,271 matched, 4 already covered by download-index.json,
  479 unmatched, 130 ambiguous, 0 query failures.
  `download-data/download-title-index.json` (2,273 entries, keyed
  `movie:<tmdbId>` / `tv:<tmdbId>` -> {part, num, title, year, imdb_id}).
  Full lists in `download-data/title-match-summary.json`.
- `loadDownloadBox(imdbId, token, fbTitle, fbYear, tmdbKey)` fallback chain:
  IMDb index -> archive title search (v6) -> title index via `getTitleIndex()`
  + `resolveDlRecordByPart('', num, part, 'new')` -> "no link" message.
  Both callers pass the key: renderDetail and hydrateLocalDetail
  (`(type === 'tv' ? 'tv:' : 'movie:') + id`); the hydrate fallback now calls
  loadDownloadBox and lets it run the whole chain.
- Verified: all 13 script blocks pass `node --check`; 5/5 sampled title-index
  entries resolve to the exact H3 record in their part (title match), and
  their imdb_ids are NOT in download-index.json (the fallback is genuinely
  needed). 479 unmatched + 130 ambiguous titles stay out of the index.
- Upload: `download-title-index.json` (new, into download-data/) + the new
  index.html. download-index.json and the 16 parts are unchanged.

## Daily updater (2026-11-18, set up, first run next morning)
- `~/workspace/zivarnet-full/work/daily-update/update.py` + cron (daily):
  fresh sitemap diff vs `state.json` (seeded from the full crawl); new
  Zivarnet titles are appended as new H3 records at the end of part 16,
  new links of existing titles (new episode / new quality) are merged into
  their existing record in the right group; download-index.json updated
  when a new title has an IMDb code; no-IMDb new titles go to
  `pending_tmdb.jsonl` for a later TMDB match. The script NEVER pushes to
  GitHub — it writes the changed files + a run report (`run-YYYY-MM-DD.json`)
  and the cron agent uploads the changed files via the browser.
- Part 16 has ~1.9 MB headroom (limit 2.9 MB); when it fills, the script
  flags `part17_needed` and stops appending — part 17 needs a site-code
  change (loadDlPart maxPart 16 -> 17) first.

## v8 — Family Night playback: play list + option popup + built-in player (2026-11-18)
- Entry point: `openPlayListOrPlayer()` rewires the detail `#d-play` and the
  hero `data-hero-play` buttons. Only new-archive titles with at least one
  joined play link get the play list; everything else falls back to the
  existing external-servers `openPlayer()` untouched.
- Play list (`#ov-playlist`, Family Night theme): poster thumb, Persian
  labels, sizes when known, sorted quality highest-first. Movie rows:
  «کیفیت ۱۰۸۰ زیرنویس فارسی (هاردساب)»; subtitle labels: hardsub ->
  «زیرنویس فارسی (هاردساب)», softsub default «زیرنویس فارسی», dubbed ->
  «دوبله فارسی», no-sub -> «بدون زیرنویس», CAM -> «کیفیت پرده». Series:
  season dropdown of (season, group) pairs — «فصل اول زیرنویس»,
  «فصل دوم دوبله» — subtitled first, then dubbed/no-sub; rows ordered by
  episode, all versions inside each episode («قسمت اول ۱۰۸۰ زیرنویس»).
- Option popup: «پخش با فمیلی نایت» (gold, full width), then MX (intent
  `com.mxtech.videoplayer.ad`), VLC (`openInPlayer`), copy stream link.
- Built-in player (`#ov-fnplayer`, fullscreen via `fnEnterFs`): loading
  text «در حال پخش، کمی منتظر بمانید», double-tap right/left +/-10s,
  center pause/-10/+10, gold progress bar (drag + tap-to-seek), aspect
  cycle (متناسب/برش/کشیده/اندازه واقعی), rotate to vertical in-frame,
  subtitle size cycle, subtitle menu (hardsub note / uploaded / off),
  settings (subtitle colour, size, upload .srt/.vtt, manual sync +/-100ms,
  persisted), resume dialog («ادامه از …» / «پخش از ابتدا», localStorage,
  works hours later), played checkmark on the row once playback starts.
  Error panel distinguishes network failure (HEAD probe: server refuses
  the file) from format failure (MKV/x265 the browser cannot decode),
  with retry + MX/VLC/copy buttons.
- BUG FOUND IN TESTING, FIXED: Profile 2018 (tt7826276, the newest title,
  added by the daily updater ~07:00) had 0 joined play links — the
  play-index build joins against `zivarnet-full-summary.json` (05:45
  snapshot, before the updater added it). Fixed by adding the title + its
  3 download links to the summary and rebuilding: joined 191,204 ->
  191,207, unjoined 7,352 -> 7,349, `verify_play_index.py` PASS. Part 16
  + manifest re-uploaded (only part 16 changed; parts 1/15 byte-identical
  to live). NOTE: the daily updater does not yet refresh the play index
  for new titles — each new title needs the same join refresh (future work).
- Tested: module harness 32 checks pass (playlist, popup, player, SRT
  upload/render, sync, rotate, aspect, resume, checkmark); integration
  test against the patched page with real data 15 checks pass (Profile 3
  rows, Senna 30 rows, Secret Level dropdown, zero-joined fallback).
  Real mp4 playback is NOT verifiable from this VM (CDN nodes return 503
  to this IP); the phone test is the decider. In-browser MKV shows the
  format error panel with MX/VLC — expected.
- Upload: new index.html + `download-data/play-index-part-16.json` +
  `play-index-manifest.json`. All 14 script blocks pass `node --check`.

## v8.1 — Android app bridge hook (2026-11-18)
- The «پخش با فمیلی نایت» button (`#fpo-fn`) now checks for
  `window.FamilyNightApp` first: inside the Family Night Android app it
  calls `FamilyNightApp.play(play_url, title)` and the native ExoPlayer
  opens (it plays MKV / H.265, which browsers cannot decode); in a normal
  browser the bridge does not exist and the in-browser player opens,
  exactly as before. Nothing else changed.
- The app project lives in `~/workspace/family-night-app/android/` (WebView
  shell around the live site + ExoPlayer); CI workflow at
  `~/workspace/family-night-app/workflow/android-release.yml` (goes to the
  repo root as `.github/workflows/android-release.yml`).

## v8.2 — Lazy sizes, real icons, truthful error message (2026-11-18)
- Play-list rows now show each file's size: after the list renders, the
  viewer's own device probes the size (HEAD, then a 1-byte Range GET;
  the CDN blocks HEAD from the build VM but answers Iranian IPs), up to
  4 at a time, and paints it into the row in place; found sizes are
  cached in localStorage, so a reopened list/popup shows them at once.
  KB sizes are now formatted too (previously dropped by the size
  formatter).
- The popup's MX and VLC buttons now show the REAL brand icons (96px
  PNGs from the apps' Google Play pages, base64-embedded), not
  hand-drawn SVGs; the download box's «دانلود با ADM» button now shows
  the real ADM icon (48px). The ADM button itself already existed.
- The player error panel no longer blames MKV for every failure: it
  first shows a neutral «در حال بررسی علت…», then probes the URL from
  the device. If the server delivers the file, the cause is the
  format/codec, named by the file's real extension (MKV message for
  .mkv; «این فایل MP4 است، ولی مرورگر کدک صدا یا تصویر آن را پشتیبانی
  نمی‌کند» for .mp4 — this was the Gardener 720p case on the user's
  phone); if the server refuses, it says the file could not be fetched.
  The v8.1 FamilyNightApp bridge hook is now part of the play-module
  source (play.js), so future module refreshes keep it.
- Tests: new `test82.py` 13/13 PASS (lazy size, real icons, mp4/mkv/
  404 error messages); `run_test.py` 32/32 and `integration_test.py`
  15/15 still PASS. All 14 script blocks pass `node --check`.

## To publish
Upload this whole folder to the `family-night-site` repo root (browser upload —
GitHub connector pushes return 403). The site reads the archive from its own
Pages URL, so no other repo needs to change. The old FN repo is untouched.
