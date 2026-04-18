# FrostWire 7.0.4: Faster transfers, safer UI, and built-in MCP support

FrostWire 7.0.4 is a stability and responsiveness release focused on the parts of the app people touch the most: transfer details, the library, search, and UI reliability on modern macOS/Java runtimes.

This build also introduces a major new power-user feature: a built-in **Model Context Protocol (MCP) server** for desktop automation and agent integrations.

## By the numbers

FrostWire 7.0.4 includes **77 documented desktop/common release-note items**:

- **29 fixes**
- **15 EDT freeze fixes**
- **8 performance improvements**
- **16 cleanup items**
- **5 maintenance/dependency updates**
- **2 new features**
- **1 update item**
- **1 removal** (GloTorrents)

There is no separate `Crash:` bucket in the 7.0.4 changelog, but several of the fixes directly address crash-class issues, especially around **macOS/Java 21 text rendering**, **JNI font-layout warnings**, and **UI deadlock/freeze paths**.

## Highlights, from most important to least important

### 1. Transfer details are dramatically faster and safer

The transfer detail stack received a deep threading and rendering overhaul:

- libtorrent/JNI calls were moved off the EDT across General, Files, Peers, Trackers, and Pieces
- the Pieces panel no longer blocks the UI while rendering large swarms
- HexHive rendering was reworked to preserve the 3D cube look while fixing overlap, stale draws, starvation, missing right-edge pieces, and scroll-time repaint artifacts
- visible pieces are now rendered first, with retained bitmap caching and viewport-aware backfilling so scrolling feels much more responsive
- transfer detail views now ignore stale async updates when switching torrents quickly

In practice, the transfer detail pane should feel much more solid under heavy torrents and frequent selection changes.

### 2. Major macOS UI freeze and Java 21 text-rendering crash hardening

FrostWire 7.0.4 fixes several classes of UI problems seen on macOS:

- modal dialog deadlocks caused by AWT/AppKit lock contention
- text-rendering JNI warnings and crashes triggered by malformed or unsupported Unicode content
- tooltip and renderer paths that could trigger expensive or unsafe font-layout work

We added broader text sanitization, removed problematic HTML rendering in sensitive paths, and tightened dialog creation behavior to keep the UI responsive.

### 3. New built-in MCP server for desktop automation

FrostWire Desktop now includes built-in **MCP server support** with:

- 38 desktop tools across 8 categories
- a settings pane for enabling and controlling the server
- client configuration support for Copilot, Codex, Claude Desktop, OpenCode, Qwen, and ChatGPT Desktop
- improved startup/status handling, config detection, and certificate generation support

This opens the door for safer local automation, search tooling, and agent-assisted desktop workflows directly against FrostWire.

### 4. Faster library browsing and fewer EDT stalls

The library received another big performance pass:

- file I/O and directory scanning were pushed off the EDT in more places
- file table metadata loading no longer leaves entries stuck at `0.0 KB`
- refresh/re-sort behavior was fixed so new downloads appear in the correct date order after async metadata loads
- drag-and-drop and media file checks no longer trigger unnecessary recursive filesystem work on the EDT
- cover art scaling and cell rendering were trimmed to reduce paint cost and GC pressure

### 5. Search reliability and result quality improved

Search reliability got several practical fixes:

- removed the dead **GloTorrents** engine
- **MagnetDL** parsing now tolerates quoted JSON-array responses
- **iDope** was updated to the new `idope.pics` domain and now filters placeholder API responses
- **SoundCloud** results now exclude tracks that are not actually downloadable
- **YouTube channel and playlist URLs** now open Telluride playlist search mode, and playlist partial results show correctly in the Video tab

### 6. Better controls and settings behavior

This release also fixes a few long-standing settings and transfer-control issues:

- the **IP Filter** UI is now actually wired to the BitTorrent engine
- transfer detail General now includes **download/upload speed limit adjustment buttons**
- settings load/save failures now surface through UI notifications
- shutdown now has a timeout guard to avoid indefinite hangs

### 7. Important correctness fixes and maintenance

Other notable fixes in 7.0.4:

- correct YCCK JPEG color handling using the Adobe APP14 marker transform
- fix `BTDownload.getSize()` precision loss above 2 GB by returning `long`
- auto-extract completed `.zip` slide downloads with zip-slip protection
- quoted string tokenization now supports backslash-escaped quotes
- dependency updates including FlatLaf, SQLite JDBC, BouncyCastle, JetBrains annotations, and JUnit

## Why 7.0.4 matters

This release is less about flashy UI changes and more about making FrostWire behave correctly under load: large torrents, fast transfer switching, heavy library views, modern macOS text rendering, and automation-heavy desktop environments.

The result is a desktop build that feels noticeably more stable, more responsive, and more future-ready than 7.0.3.

## Suggested short release summary

> FrostWire 7.0.4 is a major desktop stability and responsiveness update. It dramatically improves transfer-detail rendering and threading, hardens the UI against macOS/Java 21 deadlocks and text-layout crashes, adds built-in MCP server support for desktop automation, speeds up library operations, and improves search reliability across MagnetDL, iDope, SoundCloud, and YouTube playlist/channel searches.
