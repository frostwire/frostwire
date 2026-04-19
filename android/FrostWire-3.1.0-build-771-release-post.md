# FrostWire for Android 3.1.0 build 771: modern playback, safer downloads, and much stronger Android compatibility

FrostWire for Android 3.1.0 build 771 is a major stability and modernization release centered on one high-impact outcome: the app now behaves much more like a modern Android media and download app.

This build rebuilds the music player around Media3/ExoPlayer, restores reliable standard Android media notifications, hardens YouTube and Internet Archive downloads, migrates preferences to Jetpack DataStore, modernizes VPN/Wi-Fi protection logic, and removes a long list of UI-thread bottlenecks that could lead to freezes or ANRs.

## By the numbers

FrostWire for Android 3.1.0 build 771 includes **120 documented Android release-note items**:

- **69 fixes**
- **13 improvements**
- **33 maintenance items**
- **5 new features**

There is no separate `crash:` bucket in the 3.1.0 changelog, but many of the fixes are crash-class or ANR-class hardening, especially around **Chaquopy/yt-dlp startup**, **music playback transitions**, **DataStore persistence**, **MediaStore access**, and **deprecated Android API migrations**.

## Highlights, from most important to least important

### 1. The music player was rebuilt around ExoPlayer and Media3

This is the biggest change in the release.

FrostWire’s Android player moved from the deprecated `MediaPlayer` and `RemoteControlClient` stack to **ExoPlayer / Jetpack Media3 / MediaSessionService**. That migration fixed long-standing race conditions and made the player work like a current Android media app.

The result:

- a standard Android media notification instead of a fragile custom `RemoteViews` implementation
- output device selection in the notification for Bluetooth/Cast-style routing
- more reliable play/pause, shuffle, repeat, and metadata refresh behavior
- much better behavior during cold start, background playback, next/previous, and auto-advance transitions

### 2. Android media notifications now survive real playback use

The media notification work was not just a visual refresh. It fixed a whole class of playback-control failures that made background listening unreliable.

This release fixes:

- missing media notifications on Android 12+
- notification disappearance during engine-service notification refreshes
- notification loss during next/previous/auto-advance transitions
- stale `Not playing` state during active playback
- notification controls failing to keep metadata, cursor state, and queue state in sync
- previous-track history breaking after several songs
- playback being killed when backing out through FrostWire’s “go home” dialog

In practice, the player notification is now much closer to how people expect Spotify, YouTube Music, or Pocket Casts to behave.

### 3. Fresh downloads and direct-file playback now work much better

3.1.0 removes a major source of frustration around newly downloaded songs.

The app now handles audio files more intelligently before MediaStore has indexed them:

- freshly downloaded audio can start immediately instead of waiting for slow MediaStore indexing
- YouTube audio downloads now open in FrostWire instead of a third-party app
- direct-file playback no longer depends on a valid MediaStore ID
- freshly completed YouTube downloads from Transfers can replace the current song correctly
- album art fallback works even when MediaStore IDs or album IDs are not ready yet

This means FrostWire now behaves correctly in the exact window where users most often try to play a file: right after the download completes.

### 4. YouTube download reliability was substantially hardened

The YouTube pipeline got a deep reliability pass.

Notable fixes include:

- fixing the Chaquopy/yt-dlp startup crash path caused by Android-incompatible subprocess behavior
- threading per-format `http_headers` through the full download pipeline so requests keep the headers YouTube expects
- preventing malformed headers from dropping all subsequent headers
- treating non-2xx responses as failures so FrostWire no longer leaves behind misleading 0-byte files
- improving browser-like default headers in the OkHttp wrapper

This release also updates the Python/Chaquopy environment and the `yt_dlp` integration so Android playback and downloading are much less brittle.

### 5. Search and download sources are more dependable again

Several search and download integrations were repaired or improved:

- SoundCloud search was restored by validating remote credentials before caching and falling back safely when necessary
- SoundCloud result filtering was corrected to match real downloadable/streamable behavior
- Internet Archive downloads from search results were restored, including nested archive paths for composite crawled results
- YouTube playlist URL search was added, with playlist partial-result support in Telluride
- FrostWire is now offered more reliably for `.torrent` files and magnet links from browsers and download managers

The common theme is less silent failure and better handoff from search results into actual playback or downloads.

### 6. Preferences and settings were modernized with Jetpack DataStore

This release migrates FrostWire away from `SharedPreferences` to **Jetpack DataStore**, with a central configuration repository and safer default handling.

Benefits include:

- atomic preference writes
- safer migration from older installs
- less risk of preference corruption or type mismatch crashes
- settings writes moved off the UI thread to reduce ANR risk
- a single source of truth for defaults and volatile preference resets

This is one of the most important under-the-hood changes in 3.1.0, even though it is less visible than the player work.

### 7. UI freezes, disk I/O, and ContentResolver work were pushed off the main thread

A large part of 3.1.0 is Android hygiene work that directly affects responsiveness.

The release removes:

- 38 `ContentResolver.query()` calls from the UI thread
- 12 disk I/O operations from the UI thread
- 9 unnecessary async roundtrips for simple in-memory reads

It also fixes multiple StrictMode violations across music, transfers, suggestions, settings, and file opening flows.

These are the kinds of changes that make the app feel less fragile even when they are not visible as a single headline feature.

### 8. VPN Guard and Wi-Fi Only protections were updated for modern Android APIs

Networking protections had been weakened by deprecated Android APIs.

3.1.0 fixes this by:

- migrating network monitoring from deprecated broadcast-based connectivity APIs to `NetworkCallback`
- restoring correct Wi-Fi Only behavior
- restoring correct VPN Guard behavior
- adding faster periodic protection-state monitoring
- surfacing transfer-screen indicators when protections pause downloads

This is important correctness work for anyone relying on FrostWire to respect network constraints.

### 9. Transfers, My Music, and deletion flows were cleaned up

Several library and transfer-list correctness bugs were fixed:

- transfer rows and transfer detail file lists now refresh against the correct live state
- transfer audio play buttons now open the same player flow used by My Music
- deleting songs no longer leaves ghost entries in Recent/Favorites
- 0-byte broken downloads are now filtered out of Recent
- track deletion avoids a lifecycle-related `NullPointerException`

This reduces a class of bugs where FrostWire appeared to “undo” the user’s action because the UI reloaded stale data too early.

### 10. New power-user and privacy features were added

The release also adds a few meaningful new capabilities:

- **I2P network integration** for anonymous proxy support
- **I2P settings UI** under Settings > Advanced
- **`frostwire_launcher.py` TUI** to build, install, run, and inspect the app without Android Studio
- **live `logcat` viewing** from the launcher

These are not the core reason 3.1.0 matters, but they make the build more capable for both advanced users and developers.

### 11. Android compatibility and dependency modernization continued

This build also includes a broad modernization sweep:

- `EngineIntentService` moved away from deprecated `JobIntentService`
- deprecated fragment/activity/notification APIs were migrated across the app
- old custom media notification plumbing was removed in favor of Media3
- Gradle/AGP/Python/build tooling and multiple Android dependencies were updated
- search and media helper code was cleaned up and consolidated

That maintenance work matters because much of the 3.1.0 stability gain comes from removing old code paths that modern Android no longer treats kindly.

## Types of work in 3.1.0

This release is not just “a player update.” It spans several categories:

- **Playback architecture modernization**: MediaPlayer -> ExoPlayer/Media3, MediaSessionService, notification/output controls
- **Playback correctness fixes**: queue sync, metadata refresh, previous/next behavior, cold-start playback, direct-file playback
- **Download hardening**: yt-dlp/Chaquopy reliability, YouTube headers, 0-byte download prevention, archive.org result downloads
- **Search/source fixes**: SoundCloud, Internet Archive, YouTube playlist URLs, intent filters for torrents/magnets
- **Settings/persistence modernization**: SharedPreferences -> Jetpack DataStore, safer migrations, centralized defaults
- **Responsiveness and ANR prevention**: main-thread query and disk-I/O removal, StrictMode cleanup, fewer async hops
- **Network protection correctness**: Wi-Fi Only, VPN Guard, and modern connectivity monitoring
- **Developer and power-user tooling**: I2P support, launcher TUI, live logcat

## Why 3.1.0 matters

FrostWire for Android 3.1.0 build 771 matters because it fixes the seams between searching, downloading, and actually using media on a modern Android device.

Before this release, the most visible pain points were exactly where users notice breakage fastest: fresh downloads not opening correctly, notifications disappearing, playback controls getting stuck, settings writes freezing the UI, and modern Android API changes quietly breaking network protections or media behavior.

This build addresses those pain points directly and moves the app onto a much healthier foundation.

## Suggested short release summary

> FrostWire for Android 3.1.0 build 771 is a major modernization and stability update. It rebuilds the music player around ExoPlayer and Media3, restores reliable Android media notifications and transport controls, hardens YouTube and Internet Archive downloads, migrates preferences to Jetpack DataStore, fixes VPN/Wi-Fi protection behavior on modern Android, and removes a long list of UI-thread bottlenecks and playback edge cases.
