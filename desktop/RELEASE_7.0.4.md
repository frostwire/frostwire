# FrostWire 7.0.4 — MCP Server, No More Freezes, and a Faster UI

We're excited to announce FrostWire 7.0.4 for desktop — a release packed with a brand-new built-in MCP server, comprehensive EDT freeze fixes that eliminate UI hangs across the app, major rendering improvements to the transfer Pieces panel, and several quality-of-life features for torrent creation and management.

---

## What's New

### Built-in MCP Server

FrostWire now ships with a built-in **Model Context Protocol (MCP) server** — a first for any BitTorrent client. This opens up FrostWire to the AI tooling ecosystem with **38 desktop tools across 8 categories**, settings-pane controls, and client configuration support for:

- GitHub Copilot
- Codex
- Claude Desktop
- OpenCode
- Qwen
- ChatGPT Desktop

AI agents can now interact with FrostWire's desktop functionality through a standardized protocol.

### Torrent Creation: Piece Sizes Up to 128 MB

The Torrent Creation Dialog now supports piece sizes up to **128 MB** (previously capped at 4 MB). When creating a torrent, the dialog automatically recommends an optimal piece size based on your total content — targeting 1,000–3,000 pieces. This dramatically reduces RAM usage when seeding large files like 4K Linux ISOs.

### Per-Torrent Save Location

When selecting files to download from a torrent, the dialog now shows the current save folder with **Change...** and **Reset to Default** buttons. You can pick a custom download location for each individual torrent without changing your global default.

### Per-Torrent File Priority Control

The transfer detail **Files** tab now gives you full control over which files to download and how much bandwidth each gets:

- **"Show skipped files" checkbox** — persisted globally (default ON). Skipped files appear dimmed in gray with a **Download** button to resume them individually.
- **Priority column** — right-click any file to open an 8-level popup menu (from libtorrent's `IGNORE` to `SEVEN`). Adjust bandwidth allocation per file within a torrent without affecting the rest.
- All JNI calls for file metadata run on background threads, so the UI stays responsive even with torrents containing thousands of files.

### Restart Button Dialog

When you change settings that require a restart (language, theme, network mode), FrostWire now shows a **"Restart Now"** / **"Restart Later"** dialog instead of just a passive message. Clicking **Restart Now** gracefully shuts down and relaunches the app with the new settings applied.

### YouTube Playlists & Channels

Pasting a YouTube channel or playlist URL now opens Telluride playlist search mode. Playlist partial results also appear correctly in the Video tab.

### IP Filtering

The IP Filter settings pane is now fully wired to BTEngine — the clear and import buttons actually update the libtorrent `ip_filter`. The settings tab is no longer hidden.

---

## No More UI Freezes

This release tackles the root cause of UI freezes across FrostWire. We identified and fixed **21 EDT (Event Dispatch Thread) violations** that were blocking the UI during file I/O, torrent parsing, JNI calls, and dialog creation.

### macOS Deadlock Fixes

- Fixed a macOS EDT deadlock in `FramedDialog` where AppKit held `awtLock` during modal JDialog creation while EDT needed it for text rendering
- Changed `APPLICATION_MODAL` to `MODELESS` in `CreateTorrentDialog`, `SendFileProgressDialog`, and `EditTrackerDialog` to prevent nested modal dialog deadlocks
- Added `pack()` before `setVisible(true)` in `SendFileProgressDialog`, `EditTrackerDialog`, `Options dialog`, and `About dialog` to prevent macOS `awtLock` contention

### Off-EDT Operations

The following operations now run on background threads instead of blocking the UI:

- **Theme loading at startup** — Nimbus and FlatLaf Look-and-Feel initialization is now performed on the main thread before any Swing components exist, eliminating a consistent >2 second EDT freeze on every launch
- **Language flag image preloading** — all locale flag icons are loaded into the ResourceManager cache before the setup wizard opens, so JComboBox layout no longer triggers `MediaTracker.waitForID()` on the EDT during startup
- **Transfer detail panels** (General, Trackers, Peers, Pieces) — all JNI data gathering is now off-EDT
- **Native magnet URI generation** — no longer blocks >2 seconds on large torrents
- **Resume data generation** during pause/resume — `need_save_resume_data()` no longer stalls transfer actions
- **File progress computation** — table painting no longer calls `libtorrent file_progress()` JNI on EDT
- **File I/O and torrent parsing** in library table initialization
- **Directory scanning** in library table updates
- **Future.get() blocking** in library table selection removal
- **Recursive I/O guards** added to `DirectoryHolder.getFiles()`
- **Drag-and-drop file existence checks** — no longer triggers `file.exists()` syscall on EDT
- **File priority changes** — all libtorrent `filePriority()` JNI calls run off-EDT via `BackgroundQueuedExecutorService`

---

## Stability & Crash Fixes

### JNI Crash Prevention

We've hardened the app against macOS Java 21 font-layout JNI crashes caused by malformed Unicode content:

- Sanitize and de-HTML external text across search results, transfer names, locale labels, tooltips, and renderers
- Normalize library text before Swing/Nimbus measures it, stripping Unicode format/control glyphs
- Replace `LineBreakMeasurer` with `JTextArea` preferred-size calculation in tooltip UI
- Sanitize transfer detail table strings (Files, Peers, Trackers) before Swing paints them
- Added `-Dsun.java2d.fontlayout=0` JVM argument to suppress JDK 21 `SunLayoutEngine` JNI warning spam during `SynthComboBoxUI` font layout on startup

### Other Critical Fixes

- **Audio extraction crash**: After extracting audio from a video download, selecting the newly added file in the library table could crash with `IllegalArgumentException: Row index out of range` if the file hadn't been scanned yet. Added a bounds check to `LimeJTable.setSelectedRow()` to prevent this race condition.
- **Library Audio/Video nodes empty**: Clicking Audio or Video in the library tree showed no files even though the Default Save Folder displayed downloads correctly. Fixed three root causes: `MediaTypeSavedFilesDirectoryHolder.getFiles()` was hardcoded to return an empty array instead of the cached files; `LibraryMediator.scanInternal()` refused to add newly downloaded files to empty caches; and `SearchByMediaTypeRunnable` didn't fall back to the torrent data directory when `DIRECTORIES_TO_INCLUDE` was empty.
- **Shutdown hang**: Added 30s timeout to `LifecycleManager` shutdown latch to prevent indefinite hangs
- **File size precision**: `BTDownload.getSize()` return type changed from `double` to `long` — no more precision loss above 2GB
- **Library showing 0.0KB**: File metadata loading now wrapped in try-catch with proper cell invalidation after background load
- **Library refresh (F5)**: Now properly re-sorts files after async `lastModified` load without flooding the resort executor
- **Transfer detail stale data**: `TransferDetailGeneral` now discards stale async metadata updates when switching transfers quickly
- **Piece panel sync**: `TransferDetailPieces` now ignores late selection updates so piece counts stay in sync with the selected transfer
- **Settings failures**: UI notification now fires when settings load/save fails
- **YCCK JPEG fix**: Color inversion corrected using Adobe APP14 marker Transform field
- **CreateTorrentDialog**: Bottom buttons now visible on first open without requiring manual resize
- **Speed limit controls**: Added download/upload speed limit adjustment buttons to `TransferDetailGeneral`
- **First-view detail panel**: The transfer detail panel now populates correctly on first view. A `HierarchyListener` detects when the Transfers tab becomes visible and triggers an initial selection update, fixing the empty detail panel bug on first open.
- **Checkbox flicker eliminated**: The "Show skipped files" checkbox no longer flickers or corrupts its saved setting when toggled. Fixed a race condition between the 1-second refresh timer and user-initiated clicks.
- **Table refresh fixed**: The Files tab now rebuilds its table with fresh data on every refresh instead of calling `tableMediator.update()`, which was a no-op for immutable pre-computed holder objects.
- **Priority popup themed**: The priority popup menu now uses FrostWire's themed `SkinMenuItem`/`SkinMenu` components for consistent dark theme rendering.
- **File progress refresh**: File progress percentages now update correctly after priority changes — no more stale 0% displays.

### Settings Persistence

- **Tip of the Day**: The "Show Tips at Startup" checkbox now saves immediately when toggled. Previously, the unchecked state could be lost if the app was force-quit before the normal exit save path ran.
- **Setup wizard infinite loop fixed**: The wizard now saves its completion state **synchronously** instead of asynchronously. Previously, force-killing the JVM (e.g., IDE stop button) before the background save task executed caused the wizard to run again on next startup, which in turn reset `SHOW_TOTD=true` every time.
- **Setup wizard no longer resets tips unconditionally**: The wizard only resets "Show Tips at Startup" on actual **version updates**, not when it appears for other reasons (intent window, associations, etc.).

---

## HexHivePanel Overhaul

The transfer Pieces panel (HexHivePanel) received a complete rendering overhaul:

### Fixes
- Consistent hex tiling math with the fast flat-hex path for large swarms — no more overlapping piece cells
- Restored the 3D cube-shaded look while keeping corrected tiling math and off-EDT cached rendering
- Rows now wrap against the full viewport width — no more missing right-edge cubes
- No longer cancels queued renders before rasterization starts — prevents refresh starvation (blank panel)
- Coalesces rapid refresh requests through a single background render loop — 1-second detail updates no longer invalidate in-progress rendering
- Merges retained viewport batches using non-overlapping seam bands — no more moving white gaps between rendered cube rows

### Performance
- Renders and publishes visible viewport cubes first — the Pieces tab paints quickly before the full scrollable bitmap finishes
- Tracks viewport movement and resizes — only renders the visible region plus directional lookahead margins instead of repainting thousands of off-screen cubes
- Retains a full-size backing bitmap and incrementally patches new viewport batches — previously drawn cubes stay visible when scrolling back
- Prioritizes a smaller visible-band render with directional buffer and shorter row batches — reduces transient blank patches while scrolling

---

## Search & Download Improvements

- **Tracker list refresh**: Default tracker lists updated across desktop torrent creation, magnet URLs, and `CreateTorrentDialog` with current reliable public trackers (opentrackr, openbittorrent, stealth.si, exodus, torrent.eu.org, moeking, explodie, coppersurfer)
- **SoundCloud search** now excludes tracks not marked as downloadable — only shows content the platform explicitly allows for download
- **Idope search** updated to the new `idope.pics` domain and filters placeholder "No results returned" API responses
- **MagnetDL search parser** now accepts both raw JSON arrays and quoted JSON-array payloads
- **SlideDownload** now extracts `.zip` downloads with zip-slip protection
- **PerformersHelper** now filters hidden files alongside pad files in search results
- **QuotedStringTokenizer** now supports backslash quote escaping
- **TorrentFileFilter** display string now properly internationalized
- **GloTorrents** search engine removed (domain `gtso.cc` is parked and no longer functional)

---

## Dependency Upgrades

| Library | Old Version | New Version |
|---------|-------------|-------------|
| BouncyCastle | 1.80 | 1.83 |
| FlatLaf | 3.6 | 3.7.1 |
| SQLite JDBC | 3.50.3.0 | 3.51.3.0 |
| JetBrains Annotations | 26.0.2 | 26.1.0 |
| JUnit | 5.12.2 | 5.13.4 |

---

## Cleanup

Removed dead EDT-blocking code from `BTDownloadMediator`, unused utility methods, ancient try/catch hacks, `Thread.sleep()` scroll hacks, and dozens of stale TODO comments throughout the codebase.

---

**Download FrostWire 7.0.4**: [frostwire.com](https://www.frostwire.com)
