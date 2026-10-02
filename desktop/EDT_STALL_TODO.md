# Remaining desktop EDT stall audit / TODO

Static audit, 2026-10-01. Source references describe the current working tree,
including existing settings-pane edits. Only this new document was written.
No application changes, Gradle, runtime profiling, deployment, or ADB were used.

## Evidence and priority

- **Confirmed-blocking-call-path**: a traced Swing callback synchronously reaches
  filesystem/SQL/parsing/native-provider work. This confirms the threading boundary,
  **not** a measured freeze or a guaranteed long duration on every invocation.
- **Needsmeasurement**: EDT work is present, but cache residency, platform internals,
  or cost must be established before treating it as a material stall.
- **Runtime-reproduced**: requires a captured runtime trace and timing. **None of
  the findings below has this label.** Comments mentioning seconds are not measurements.
- **P1**: address first: recurring cache misses, startup/restore fan-out, unbounded
  scans, or synchronous disk/DB operations. **P2**: narrower user actions/native
  commands. **P3**: profile platform-required UI/layout and cold-resource costs.
  Priorities are exposure/complexity estimates, not measured severity rankings.

Path notation below is exact and relative to the repository root:

| Prefix | Directory |
| --- | --- |
| `G/` | `desktop/src/main/java/com/limegroup/gnutella/gui/` |
| `B/` | `desktop/src/main/java/com/frostwire/gui/bittorrent/` |
| `L/` | `desktop/src/main/java/com/frostwire/gui/library/` |
| `T/` | `desktop/src/main/java/com/frostwire/gui/components/transfers/` |
| `S/` | `desktop/src/main/java/com/frostwire/gui/components/slides/` |
| `R/` | `desktop/src/main/java/com/frostwire/search/relay/` |
| `U/` | `desktop/src/main/java/com/limegroup/gnutella/util/` |
| `Q/` | `desktop/src/main/java/org/limewire/setting/` |
| `CB/` | `common/src/main/java/com/frostwire/bittorrent/` |
| `CR/` | `common/src/main/java/com/frostwire/search/relay/` |

## Ordered actionable backlog

### 1. P1 — Native file icons: preload and library misses still execute on EDT

- [ ] **Confirmed-blocking-call-path.** Preload: `G/Initializer.java:401-406`
  initializes IconManager; `G/IconManager.java:37-42` posts controller creation;
  `G/NativeFileIconController.java:196-210` queues a worker that immediately
  `safeInvokeAndWait`s **back to EDT**; `getIconForExtension():152-187` creates a
  temp file (168), calls cached `VIEW.getIcon` (177), and deletes it (185).
  The recent fix correctly reuses VIEW: there is no per-extension chooser rebuild.
- Library painting/model access: `L/LibraryFilesTableDataLine.java:231-238`
  -> `getIcon():308-323` -> worker -> `safeInvokeAndWait` ->
  `G/IconManager.java:64-70` -> `NativeFileIconController.getIconForFile():137-147`
  -> `File.exists()` / native view. Windows miss reaches
  `FileSystemView.getSystemIcon` at 338; chooser view delegates at 392-395.
  `SmartChooserView.isIconCached():413-415` tracks that a file was requested;
  it does not prove a ready, immutable Icon is cached in the provider.
- Search result model access also directly requests extension icons:
  `G/search/SearchResultDataLine.java:140`. Extension-cache hits return early at
  `NativeFileIconController.java:158-165`; misses are the concern.
- Impact estimate: one disk/native lookup per cold extension, plus one per cold
  library file; serialized preload callbacks can occupy many consecutive EDT turns.
  Missing icons currently store null at 186 rather than NULL, so negative misses
  can repeat. Repaint refresh at `LibraryFilesTableDataLine.java:316` adds fan-out.
- Fix: cache-only EDT API returning a generic placeholder; bounded, deduplicated
  worker requests for file probes/temp lifecycle and a platform-supported native
  lookup provider. Publish actual Icon values and negative results, then repaint
  affected rows in bounded batches. Keep chooser/UI creation and Swing mutation
  on EDT. Do **not** call a chooser-owned FileView on an arbitrary worker without
  verifying its thread contract; use a separate supported native provider or a
  generic fallback where lookup cannot safely be isolated. Thread-safe ownership
  is required before moving current HashMap/VIEW caches across threads.
- Validate: injected slow filesystem/icon provider; cold extension, warm hit,
  missing icon, repeated same file, cache eviction and a 10,000-file library.
  Assert provider work is off EDT, one in-flight request per key, heartbeat keeps
  running, late results for removed rows are ignored, and rich icons still work
  on macOS/Windows. Test cancellation/disposal and queue saturation.

### 2. P1 — Local .torrent opening performs copy, parse, and engine work on EDT

- [ ] **Confirmed-blocking-call-path.** `G/VisualConnectionCallback.java:70-71`
  posts -> `G/GUIMediator.java:1128-1146` ->
  `B/BTDownloadMediator.java:789-867` posts another EDT callback. That callback
  constructs `PartialFilesDialog` (800-802), runs the UI callback (809-810),
  probes seed data (817), creates directories (827-829), copies the torrent
  with `Files.copy` (836-839), and directly calls `BTEngine.download` (852).
  The UI callback itself reparses the file at `G/GUIMediator.java:1138`.
- `CB/BTEngine.java:382-415` synchronously sets up storage, constructs
  `TorrentInfo(torrent)` (390), queries existing native priorities (401), and
  enters the engine download path. New handles save resume-torrent data;
  `CB/BTEngine.java:881-889` bencodes and writes bytes synchronously.
  The seed branch is different: `B/BTDownloadMediator.java:710-714` explicitly
  reschedules off EDT; do not flag its later worker download as EDT execution.
- Impact estimate: at least copy + repeated parse + storage/native work per
  file; slow/removable/network-backed paths and large metadata amplify latency.
- Fix: one worker prepares the torrent and selection rows, chooses/copies paths,
  and performs engine work; EDT shows the selection dialog and applies a small
  result snapshot. Reuse parsed metadata/hash instead of reopening the file.
  Preserve VPN checks, existing-transfer selection, save-folder/seed behavior,
  copy fallback and error reporting.
- Validate: controlled blocked copy/parser/engine seam with EDT heartbeat;
  partial/full/seed, existing hash, read failure, copy failure, large v2 metadata,
  cancel while preparation is blocked, and disposal before completion. A canceled
  request must not start a torrent or select a stale transfer.

### 3. P1 — Download addition still forces native status and conditional cleanup

- [ ] **Confirmed-blocking-call-path.** `G/VisualConnectionCallback.java:84-86,112-113`
  -> EDT -> `B/BTDownloadMediator.java:977-979` ->
  `B/BittorrentDownload.java:57-77`. Constructor calls `dl.isFinished(true)` at
  70; `CB/BTDownload.java:209-217` uses `th.status(force)` for that forced branch.
  Finished transfers with seeding disabled then call `getIncompleteFiles()` and
  `finalCleanup()` at 73. `CB/BTDownload.java:813-839` queries native file progress
  and filesystem timestamps; `B/BittorrentDownload.java:377-390,80-110` deletes
  files and recursively canonicalizes/lists/deletes directories. Constructor
  pause/resume also reaches native commands (`CB/BTDownload.java:526-547`).
- Impact estimate: restored transfers multiply the forced status call; cleanup
  is proportional to file/tree size. Size/item calculation at 66-69 is already
  queued and does not isolate the remaining constructor work.
- Fix: worker-owned preparation/lifecycle sequencing, followed by lightweight
  EDT row addition. Use the asynchronously maintained status snapshot for UI;
  perform required forced check/cleanup on the worker. Preserve explicit pause
  state and finished-seeding policy and prevent cleanup after removal/replacement.
- Validate: block forced-status and cleanup seams during mass restore/add; verify
  heartbeat, exactly-once listener wiring, pause/resume ordering, cleanup policy,
  explicit-paused transfers, cancellation and atomic publication of prepared data.

### 4. P1 — Identity, Karma and shared-database panes synchronously query SQL

- [ ] **Confirmed-blocking-call-path.** Options tree selection ->
  `G/options/OptionsMediator.java:138-139` ->
  `G/options/OptionsPaneManager.java:72-81` -> `pane.initOptions()` ->
  `G/options/OptionsPaneImpl.java:93-95` -> the following pane operations:
  - `G/options/panes/IdentitySettingsPaneItem.java:162-205` ->
    `getKarmaScore():280-289` -> `KarmaChainTable.open/loadChain`; also
    `getSharedCount():297-302` -> `LocalIndex.size()`.
  - `G/options/panes/KarmaSettingsPaneItem.java:153-211` ->
    open/load chain, score traversal, `getTopPeers(20)`. Its Refresh button at 86
    reaches the same path.
  - `G/options/panes/SharedTorrentsDatabasePaneItem.java:136-157` ->
    `index.size()` + `formatDbSize():200-204`. Listing/search is already threaded
    at 180-197, but these header operations are not.
- Backend: `R/KarmaChainTable.java:88-106` opens JDBC, configures pragmas and
  initializes schema; `loadChain():203-212`, `getTopPeers():300-314` execute SQL.
  `R/LocalIndexTable.java:661-665` takes the connection monitor and counts rows;
  `sizeInBytes():697-704` probes database/WAL/SHM files. Connection contention
  can make even a count block behind indexing. Reopening options initializes
  all previously created panes (`OptionsMediator.java:96`, `OptionsPaneManager.java:99-102`).
- Impact estimate: DB open/schema/query per refresh, chain-size-dependent scoring,
  and serialized connection wait; hidden panes can add to reopening time.
- Fix: bounded/coalesced DB-worker snapshots, including headers/counts/size;
  placeholders on EDT, generation/identity checks on publication, and reuse an
  appropriately owned database service rather than opening it in UI callbacks.
- Validate: stalled connection/SQL seam, a long chain, concurrent indexing,
  repeated refresh/reopen, identity replacement and close/cancel. Assert heartbeat,
  single-flight work, consistent counts, and no stale results. Shared database
  list results at 194 also need generation checks and bounded UI publication.

### 5. P1 — Create-torrent content selection recursively scans before hashing

- [ ] **Confirmed-blocking-call-path.** Select file/folder Swing action ->
  `B/CreateTorrentDialog.java:423-433` -> chooser result at 374-376 ->
  `setChosenContent():380-388` -> canonicalization at 396-409 ->
  `recommendPieceSizeForSelectedContent():308-315` ->
  `calculateTotalSize():322-332` recursively calls `isFile/isDirectory/length/listFiles`.
  Save As additionally lists the selected directory at 443-447.
- Impact estimate: O(number of descendants) storage probes on every selection;
  large trees and symlink cycles are important fixtures. Hashing/makeTorrent
  itself is already worker-queued at 471-480.
- Fix: cancellable bounded worker traversal with cycle policy, immutable selection
  snapshot and request generation; update recommendation/progress on EDT only if
  the same content is still selected. Avoid duplicating the scan when creation
  starts; revalidate storage conditions off EDT before committing output.
- Validate: delayed list/stat seam, 100,000 entries, cyclic links, inaccessible
  child, rapidly select A then B, close during scan, and switch piece-size choice
  while calculation runs. A late recommendation must not overwrite a newer
  selection or explicit user choice; hashing correctness remains unchanged.

### 6. P1 — TorrentFetcher moves download work but still decodes on EDT

- [ ] **Confirmed-blocking-call-path.** Fetch completion on `Torrent-Fetcher`
  (`B/TorrentFetcherDownload.java:357-386`) -> `downloadTorrent():272-288` ->
  EDT callback -> `TorrentInfo.bdecode(data)` at 288. Partial path first
  constructs `B/PartialFilesDialog.java:72-80`, which also bdecodes (73) and
  builds selection rows (`PartialFilesDialog.java:482-491`). File-based dialog
  constructor at 68-69 reads/parses a torrent too. Per-file path/size conversion
  adds O(file count) JNI/row construction; partial fetch decodes twice.
- HTTP/mesh/magnet fetching at 365-376, relativePath selection at 262-267, and
  the final download at 291-301 are already off EDT. The comment "Decode on
  EDT (fast)" at 287 is not a bound on native decoding time.
- Fix: decode and construct plain selection metadata once on the fetch/preparation
  worker. Pass immutable rows to EDT dialog; capture selection/save-folder on
  EDT and schedule engine work without reparsing. Bound UI installation batches.
- Validate: delayed decoder, many-file/v2/hybrid/malformed fixtures, full/partial/
  relativePath flows, cancellation before the posted dialog and while it is open,
  generation replacement, heartbeat, and no late download after cancel. Distinguish
  the fetcher's internal row-removal state from user cancellation in that design.

### 7. P1 — New transfer rows lazily load torrent metadata during EDT initialization

- [ ] **Confirmed-blocking-call-path.** Same AddDownload EDT entry as item 3 ->
  `B/BTDownloadMediator.java:427-429` -> table add ->
  `G/tables/BasicDataLineModel.java:370-371` initializes row ->
  `B/BTDownloadDataLine.java:204-210` calls `getPaymentOptions()` ->
  `B/BittorrentDownload.java:261-268,357-365` -> `dl.getTorrentFile()` and
  `new BTInfoAdditionalMetadataHolder(torrent, ...)` ->
  `CB/BTInfoAdditionalMetadataHolder.java:43-44` -> `new TorrentInfo(torrent)`.
  `CB/BTDownload.java:809-810` -> `CB/BTEngine.java:852-857` reads resume data
  and native-bdecodes its path. File existence/readability and metadata parsing
  then happen synchronously. Initial `update()` at 210 can call these getters again.
- Impact estimate: disk read/parse on each newly added transfer; when holder is
  absent or loading fails, future attempts are not represented by a negative/loading
  snapshot. Normal periodic row update is already backgrounded in
  `B/BTDownloadModel.java:153-168`; that does not cover initial row creation.
- Fix: prepare metadata with item 3 or single-flight lazy worker loading; getters
  return snapshots/placeholders with explicit loading/absent/failed states.
- Validate: block resume-path/metadata reads during row initialization; missing
  torrent, missing optional metadata, changed transfer and many restored transfers.
  Assert bounded attempts and correct license/payment updates on EDT.

### 8. P2 — Identity import/export/restore and fallback resolution perform disk work

- [ ] **Confirmed-blocking-call-path.** Identity button listeners ->
  `G/options/panes/IdentitySettingsPaneItem.java:392-393,425-426,462-463` ->
  `CR/IdentityLifecycle.java:113-118,144-150,170-184,191-206` -> seed/key work,
  file load, backup copy, save, output flush and verification. Refresh/copy/show
  actions resolve identity through pane 208-209 -> lifecycle 44-52: live identity
  is a cheap fast path; only missing-live fallback probes/loads storage.
- Fix: keep prompts/choosers on EDT, snapshot inputs, worker-own file/crypto
  lifecycle, publish completion/error on EDT; serialize identity mutations and
  prevent overlapping import/restore/export with generation. New-identity PoW
  is already worker-executed at pane 243-248 and must remain there.
- Validate: slow backup/export/read seam; active-live and fallback cases, disk
  errors, invalid mnemonic, repeated actions, pane closure and mutation ordering.
  Restart only after the selected operation successfully finishes; do not expose
  seeds/keys in performance logs. Measure first-use mnemonic resource loading too.

### 9. P1 — Options Apply/OK persists settings synchronously under locks

- [ ] **Confirmed-blocking-call-path.** `G/options/OptionsButtonPanel.java:83-88,110-112`
  -> `G/options/OptionsMediator.java:151-153` ->
  `Q/SettingsGroupManager.java:62-68` holds PROPS while calling each group save ->
  `Q/BasicSettingsGroup.java:73-75` -> synchronized
  `Q/SettingsFactory.java:249-282` -> FileOutputStream / Properties.store.
  This is actual disk persistence, separate from in-memory setting setters.
- Fix: validate/capture Swing values on EDT, serialize persistence through an
  owned worker and report success/failure back; retain ordering between Apply,
  later edits and shutdown save. Audit factory/group lock ownership so scheduling
  does not leave UI getters waiting behind a worker's disk write.
- Validate: blocked save seam with heartbeat, two rapid Apply operations, edit
  during save, write failure and shutdown after Apply. Verify saved snapshots and
  restart-dialog timing; preserve the existing restart-dialog gate behavior.

### 10. P2 — Native network settings and VPN gating are still synchronous

- [ ] **Confirmed-blocking-call-path** for EDT native-provider calls; actual native
  session wait time **Needsmeasurement**. Options selection -> pane construction
  -> `G/options/panes/NetworkInterfacePaneItem.java:51-65` enumerates interfaces
  and converts native addresses. Apply entry from item 9 -> pane 147-165 rebinds
  listener; `RouterConfigurationPaneItem.java:195-200` calls applySettings/rebind;
  `I2PPaneItem.java:191-205` calls applySettings;
  `TorrentConnectionPaneItem.java:141-182` calls session getters/commands/DHT
  start-stop and `VPNs.isVPNActive()` at 167.
- Torrent open also calls `G/GUIMediator.java:1129-1130` ->
  `G/VPNDropGuard.java:40-47` -> `G/VPNs.java:35-51,71-84` -> native route
  enumeration and POSIX VPN-provider refresh, when protection is enabled.
  The periodic `G/VPNStatusRefresher.java:55-70` already runs checks on a worker.
- Fix: worker-load interface snapshots; capture settings on EDT and apply session
  commands in an ordered worker. For VPN admission, perform fresh policy/provider
  evaluation off EDT, recheck current protection/policy before download start,
  and keep unknown status fail-closed. Do not replace a necessary admission check
  with an indefinitely stale cached "VPN active" value.
- Validate: delayed native/session/VPN-provider seam, VPN disconnect during pending
  admission, changed settings while pending, and ordered DHT/rebind transitions.
  Measure native command submission separately from asynchronous network effects.

### 11. P2 — Transfer speed/tracker/pause/share actions retain native or file calls

- [ ] **Confirmed-blocking-call-path.** Speed buttons ->
  `T/TransferDetailGeneral.java:439-460,466-487` ->
  `CB/BTDownload.java:721-734` native limit getters/setters.
  `B/BTDownloadActions.java:61-62,147-155` pause loops ->
  `B/BittorrentDownload.java:157-159` -> `CB/BTDownload.java:526-534` native pause.
  Resume-file persistence is separately threaded at common 536/547.
- Tracker Add button -> `B/BTDownloadMediatorAdvancedMenuFactory.java:369,385`
  -> `CB/BTDownload.java:752-760` native tracker list. Add replacement is already
  queued at desktop 391. Edit dialog open -> desktop 405-406,485,496-500 -> same
  native list; Edit Accept -> 488,521,549-550 -> common 764-770 replaces trackers
  and invokes resume-data work synchronously. Update/scrape/recheck actions at
  desktop 420/434/448 are already backgrounded.
- Share action -> `B/BTDownloadActions.java:322-331` ->
  `B/BittorrentDownload.java:353-354` -> common torrent-path disk read (item 7)
  -> `new TorrentInfo(file)`; seed renderer shares through wrapper 440-444 too.
- Fix: ordered worker commands and metadata snapshots; EDT captures selected
  target/values, shows dialogs and renders results. Prevent stale target selection
  from redirecting a pending command. Do not label every native flag/getter as a
  multi-second stall: profile contention and multi-selection scale.
- Validate: blocked limit/tracker/read seam, large multi-selection, removed target,
  switching transfer with dialogs open, command failure, and heartbeat.

### 12. P2 — Drag export and Explore synchronously traverse/launch

- [ ] **Confirmed-blocking-call-path.** Swing drag callback ->
  `B/BTDownloadTransferHandler.java:81-109` -> recursive filesystem listing of
  save locations to construct FileTransferable. Separately Explore action ->
  `B/BTDownloadActions.java:77-91` -> `G/GUIMediator.java:854-856` ->
  `U/Launcher.java:138-159`: canonical path/stat, Runtime.exec via
  `U/FWProcess.java:26-35`, or Linux Desktop.open. General save-location click
  at `T/TransferDetailGeneral.java:365-368` reaches the same explorer path.
- Fix: precompute bounded drag manifests off EDT or export a directory directly
  where semantics permit; Swing's synchronous createTransferable callback must
  consume a ready snapshot, not wait for a Future. Queue explorer path validation
  and OS launch; capture target first and marshal errors to EDT.
- Validate: huge/deep/cyclic tree, stale/deleted data, delayed listing/launch seam,
  repeat drag and heartbeat. Launcher.launchFile's Desktop.open at 119-126 and
  PlaySingleMediaFileAction's scan at `BTDownloadActions.java:347-366` already
  run in workers; do not flag those scans as EDT work.

### 13. P2 — Library-folder JTree model scans during layout/expansion

- [ ] **Confirmed-blocking-call-path.** Options Library Folders initialization
  `G/options/panes/LibraryFoldersPaneItem.java:100-106` ->
  `L/RecursiveLibraryDirectoryPanel.java:92-102,202-221` installs FileTreeModel
  and expands roots. Swing JTree model queries (`isLeaf/getChildCount/getChild`)
  -> `G/trees/FileTreeModel.java:65-84,120-130` -> canRead/listFiles/filter/sort.
  The one-directory cache distinguishes sorted/unsorted queries, so alternation
  may repeat a scan; cache hits do not remove the cold expansion path.
- Fix: asynchronous directory snapshots with loading nodes; tree getters are
  cache-only. Fire model events on EDT after generation-checked completion;
  cancel/coalesce expansion and avoid blocking comparator/filter filesystem calls.
- Validate: blocked enumeration/filter, large root, rapid expansion/collapse,
  changed roots, permissions, symlinks, heartbeat and EDT-only model events.

### 14. P1 — Slideshow image cache reads and HTTPS misses can run on EDT

- [ ] **Confirmed-blocking-call-path.** Background slideshow load ->
  `S/MultimediaSlideshowPanel.java:105-118` posts setup to EDT -> new SlidePanel
  -> `S/SlidePanel.java:91-97` -> `S/ImageCache.java:52-59`.
  Cached disk hit -> exists probe (73-75) and `ImageIO.read(file)` (78-82).
  Uncached URL whose protocol is not exactly `http` -> `loadFromResource():94-104`
  -> synchronous HTTP client getBytes/decode/cache write. **HTTPS also follows
  that branch**. Only plain-http cache misses reach worker `loadFromUrl():113-131`.
- Impact estimate: a cache hit still reads disk; an HTTPS miss can cost a network
  request per slide while all panels are created in one EDT event. Actual feed
  URL schemes/slide counts and observed latency remain unmeasured.
- Fix: classify resource/HTTP(S) correctly and perform all cache probes/read/decode/
  fetch/write through a bounded image worker; publish ready images on EDT with
  generation checks. Avoid instantiating/loading every slide synchronously.
- Validate: blocked cached-image read, HTTPS response and decode seams; http/https/
  local/jar resources, corrupt cache, slideshow replaced/closed and many slides.
  Assert heartbeat and no stale image publication. Slideshow JSON loading is
  already off EDT; its java.util.Timer callback at panel 235-248 mutates Swing
  off-thread and needs an EDT-only UI publication check during this work.

### 15. P2 — Deferred/lazy theme image loading is still EDT loading on cold misses

- [ ] **Confirmed-blocking-call-path** on a cold theme-cache miss;
  normal warm-cache impact **Needsmeasurement**. `G/MediaButton.java:61-73`
  invokeLater -> loadIcons -> theme lookup; `G/StatusLine.java:500-501` defers
  updateTheme to EDT and bandwidth icons are loaded at 525-529.
  `G/search/SourceRenderer.java:104-110` -> synchronized ensureIconsLoaded at
  44-55 -> seven theme-image lookups on first use.
  `G/ResourceManager.java:143-154,185-210` returns a cached Icon immediately or
  resolves resources and calls `ImageIO.read(url)`. Lazy/invokeLater does not
  change the thread or bound resource decoding/class loading.
- Fix: preload immutable decoded image data off EDT or single-flight async
  cache misses with placeholders; EDT installs icons and repaints. Include theme
  generation/invalidation so old images cannot overwrite a new theme. Preserve
  animated GIF loading rather than treating every image as a static bitmap.
- Validate: empty/warm theme cache, delayed resource/decode seam, theme switch
  during loading, missing image, GIF animation and first search rendering.
  Measure cold path before deciding which images merit eager startup preloading.

### 16. P2/P3 — Setup folder checks and platform-required chooser/startup UI

- [ ] **Confirmed-blocking-call-path** for application storage probes:
  Next/Finish -> `G/init/SetupManager.java:252-255,287` ->
  `G/init/BitTorrentSettingsWindow.java:87-119` -> exists/isDirectory/canWrite/
  mkdirs and initial library setup. Options folder validation at
  `G/options/panes/TorrentSaveFolderPaneItem.java:53-61` ->
  `B/TorrentSaveFolderComponent.java:73-102` performs filesystem checks.
  Chooser helper `G/FileChooserHandler.java:51-68` probes remembered directories
  before/after dialogs. Move application validation/probing into a worker with
  generation-aware continuation; keep component input capture on EDT.
- [ ] **Needsmeasurement**, P3: native chooser/UI construction and layout itself.
  `G/Initializer.java:359` -> EDT setup creation ->
  `G/init/SetupManager.java:93-172`; pack/show at 234-239 is another EDT turn,
  not background work. `G/IconManager.java:37-38` ->
  `G/NativeFileIconController.java:79-119` constructs/installs JFileChooser UI
  (up to ten attempts); `G/FileChooserHandler.java:340-439` creates/shows platform
  choosers; `B/PartialFilesDialog.java:201-205` creates a chooser for folder selection.
  MainFrame minimum-size calculation is posted back to EDT at
  `G/MainFrame.java:178-185`; its sleep is on the helper thread, not EDT.
- Fix/measure: instrument constructor/installUI/pack separately from caller-owned
  file work. Keep Swing/AWT UI creation, UI delegates, layout, dialog visibility
  and mutation on EDT. Reuse appropriately scoped chooser/UI instances, choose
  safe prepared starting directories, reduce eager component creation and large
  layout work. Treat unavoidable platform UI costs as measured platform behavior,
  not a reason to silence watchdogs or move Swing constructors onto workers.
- Validate: first/warm chooser, first-run/version-update wizard, unavailable mount,
  large folders, all supported desktop platforms, language/theme changes and
  cancel/reopen. Modal dialog waiting is not itself an EDT freeze: its nested
  event loop must still process the heartbeat and repaint.

## Existing protections / rejected false positives

- General recheck and sequential-toggle commands are already queued:
  `T/TransferDetailGeneral.java:493-511`. Its state machine computes off EDT at
  519 onward. Transfer selection/timer refresh ->
  `T/TransferDetailComponent.java:198-239` queues updateData; timer refresh uses
  a single-flight gate. Selection-queued tasks still deserve saturation/generation
  validation. General's sequential display getter at 344 reaches
  `CB/BTDownload.java:849-851`: cached status/flags, not a forced session status query.
- `CB/BTDownload.java:355-388` returns stale/null status and asynchronously
  refreshes it; do not call ordinary UI progress/isFinished getters forced status.
  Cached native wrapper access may still need profiling, but differs from
  `isFinished(true)` in item 3.
- RefreshTimer is Swing (`G/RefreshTimer.java:23,40-41`) and calls every registered
  listener (`G/GUIMediator.java:1004-1014`). Connection reachability is then sent
  to pool.execute (1017-1027); do not flag that network check as synchronous EDT.
  Transfer model periodic updates/sorting and aggregate data are worker-based;
  cached row paints differ from initial add and lazy cold metadata.
- Library metadata/length/mtime loading is worker-queued at
  `L/LibraryFilesTableDataLine.java:158-204`; icon lookup is the exception in item 1.
  Library scans/search are queued (`L/LibraryMediator.java:197`,
  `L/LibrarySearch.java:229`); media-type scan requests coalesce at
  `L/LibraryExplorer.java:228-258`. Artwork retrieval is worker-based at
  `L/LibraryCoverArtPanel.java:59-63,78-80,95-110`; constructing TagsReader selects
  a parser, not the AudioFileIO read itself. Selection still stats a file at
  `L/LibraryFilesTableMediator.java:549` (include in slow-storage measurements).
- Library removal's Future.get is **inside** CompletableFuture.runAsync at
  `L/LibraryFilesTableMediator.java:396-419`, not an EDT executor wait.
  UI/model access from workers merits separate correctness review, not a fabricated
  EDT wait finding. PeerDirectory SwingWorker.get at
  `G/options/panes/PeerDirectorySettingsPaneItem.java:266-268` is in done() after
  worker completion, not a wait for an unfinished worker.
- SmartSearch DB count (`G/options/panes/SmartSearchDBPaneItem.java:87-97`),
  PeerDirectory refresh/browse (pane 121,250-283), IceBridge ping/telemetry (pane
  331-376), MCP start/stop/agent detection (pane 215-307) are asynchronous.
  IceBridge apply (257-303) sets restart-required configuration; it does not launch
  the child on Apply. Do not lump these together with Identity/Karma SQL.
- Initializer's relay identity/database setup is worker-dispatched after restored
  downloads (`G/Initializer.java:179-185,428-452`); Main's JNI extraction and
  startup service work are not proven EDT callbacks merely because they do I/O.
- Whole-source wait-pattern scan found MCP tool .join/latch/Future use and service
  lifecycle awaits. No completed Swing-callback-to-unfinished-executor-wait trace
  was established here. Worker -> invokeAndWait is not an EDT wait by itself;
  examine lock cycles if a runtime stall points there. Do not turn unrelated
  background transport/installer sleeps into EDT findings.

## Regression and measurement contract for follow-up fixes

These are **proposed acceptance targets**, not results from this audit:

1. Capture baseline and fixed packaged builds on macOS/Windows/Linux, recording
   commit/tree state, JVM, host/storage, cache state, fixture sizes and repetitions.
   Separate cold start, warm UI, restore, first dialog, steady scroll and teardown.
2. Target 60-Hz frame work: individual render/model callbacks fit a 16.7-ms budget;
   target ordinary action/EDT publication slices <=16 ms, split large installs.
   Use a 16-ms Swing heartbeat: target p95 delay <=32 ms, p99 <=50 ms, and no
   application-owned I/O/native/DB-induced gap >100 ms in controlled scenarios.
   Record max and deadline misses as well as percentiles; explicitly report any
   unavoidable native chooser/layout exceptions instead of silently accepting them.
3. For startup, measure launcher-to-first-painted-shell and first responsive input
   separately from all-services-ready. Across at least ten cold/warm runs, target
   no application-owned EDT I/O/SQL/forced-status slices >50 ms and no regression
   >5% in median first-responsive-shell time on the same fixture/host. Icon preload,
   restore and optional network metadata must not gate shell responsiveness.
4. Use injectable filesystem/decoder/native/DB/network seams and latches: hold a
   worker operation for 500 ms or more while asserting a separately posted EDT
   sentinel and several heartbeat ticks execute **before releasing the seam**.
   This detects incorrect invokeLater/invokeAndWait placement without relying on
   a normally fast SSD. Never await an unfinished worker from EDT in the test.
5. For every asynchronous fix, test cache miss/hit/negative-hit, saturation,
   repeated request coalescing, cancel/dispose, A-to-B selection/identity/theme
   generation changes, errors and late completion. Verify Swing writes happen
   on EDT and heavy seams do not. Preserve semantic results and worker cleanup;
   canceling UI publication alone does not prove a native call was interrupted.
6. Profile with JFR wall-clock/I/O/monitor data and repeated EDT thread dumps;
   include native frames where available. Runtime confirmation must record the
   action, inputs, blocked frame, duration, cache state and frequency. Keep EDT
   diagnostics enabled: no warning silencing, threshold inflation or timeout
   removal to make a check pass. No performance-fix claim until measurements exist.

## Scanned scopes and explicit gaps

**Deep call-chain reads:** NativeFileIconController/IconManager/ResourceManager;
VisualConnectionCallback; BTDownloadMediator/BittorrentDownload/TorrentFetcherDownload;
common BTDownload/BTEngine/IdentityLifecycle; PartialFilesDialog/CreateTorrentDialog;
BTDownloadDataLine/model/add path, actions/advanced trackers/drag handler;
TransferDetailGeneral/Component refresh; library rows/mediator/explorer/search,
folder tree model/panel, removal waits, cover-art/tag dispatch; options show/init/
Apply/OK/pane manager/factory and persistence; Identity/Karma/SharedTorrents DB,
NetworkInterface/Router/I2P/TorrentConnection/IceBridge/LibraryFolders/SmartSearch
pane boundaries; RefreshTimer/GUIMediator; Initializer/setup/BitTorrent settings;
MediaButton/StatusLine/SourceRenderer; chooser helper; Launcher/FWProcess;
slideshow setup/SlidePanel/ImageCache.

**Pattern scans and threading-boundary spot checks:** all desktop main Java for
executor waits/sleeps; GUI/library/transfer code for directory enumeration,
native metadata/icons, HTTP/image/tag loading, renderers and timer callbacks;
MCP settings/peer refresh/SwingWorker completion and PeerCatalog generation;
VPNStatusRefresher/VPNDropGuard/VPNs; startup extraction/relay dispatch; update/
installer and transport/background lifecycle wait hits. These are screening
scopes, not a line-by-line certification of every listener or renderer.

**Remaining gaps:** no running desktop, timings, flame graph, packaged launch,
JDK/Aqua/Windows shell thread-contract experiment, native library source inspection,
or regression execution. Native setters may submit commands quickly; their
contention cost is unknown. Unread branches in search-result details, IP-filter,
association/registry handling, feedback, updater launch, shutdown, custom chooser
delegates and every registered RefreshListener require targeted follow-up; broad
pattern coverage does not establish their safety. Full table sorting/layout and
worker-to-EDT lock cycles remain unprofiled. Extend this backlog when an actual
trace identifies these paths, rather than claiming the audit found every stall.
