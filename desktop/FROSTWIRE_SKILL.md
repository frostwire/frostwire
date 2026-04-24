---
name: frostwire-mcp
skill_id: frostwire-mcp
description: Reference for the FrostWire desktop MCP server — 38 tools across search, transfers, library, BitTorrent engine, settings, VPN, and IP filtering.
tags: [frostwire, mcp, bittorrent, p2p]
triggers:
  - frostwire mcp
  - frostwire tools
  - frostwire search
  - frostwire transfer
  - frostwire download
  - frostwire torrent
---

# FrostWire MCP Server

FrostWire desktop ships a built-in MCP server with **38 tools** and **7 live resources**. Use these endpoints when the user asks to search, download, manage transfers, configure settings, or inspect the FrostWire state via an AI agent.

## Resources (read-only snapshots)

| URI | What it returns |
|-----|-----------------|
| `frostwire://transfers` | All transfers (summary JSON) |
| `frostwire://transfers/detail` | All transfers with peer/seed counts |
| `frostwire://transfers/files` | File lists for every transfer |
| `frostwire://library` | Library status |
| `frostwire://settings` | Settings snapshot (connection, sharing, search, VPN) |
| `frostwire://btengine/status` | BitTorrent engine state |
| `frostwire://vpn/status` | VPN active + drop-protection flag |

## Tools by Category

### Search (6 tools)

- **`frostwire_search`** — Start a search. Args: `keywords` (required), `engines` (optional array of IDs), `timeout` (optional). Returns a `token`.
- **`frostwire_search_status`** — Check if a search is still running. Arg: `token`.
- **`frostwire_search_results`** — Fetch results. Args: `token` (required), `offset`, `limit` (default 50), `sortBy` (`seeds`/`size`/`creationTime`/`displayName`), `filter` (`minSize`, `maxSize`, `minSeeds`, `source`, `fileType: torrent|cloud`).
- **`frostwire_search_cancel`** — Stop a search. Arg: `token`.
- **`frostwire_search_engines`** — List all engines with `id`, `name`, `enabled`, `type`.
- **`frostwire_search_engine_toggle`** — Enable/disable an engine. Args: `engineId`, `enabled`.

Engine IDs: `TPB_ID`, `SOUNDCLOUD_ID`, `INTERNET_ARCHIVE_ID`, `YT_ID`, `TELLURIDE_ID`, `NYAA_ID`, `ONE337X_ID`, `IDOPE_ID`, `TORRENTZ2_ID`, `MAGNETDL_ID`, `TORRENTSCSV_ID`, `KNABEN_ID`, `TORRENTDOWNLOADS_ID`.

### Transfers (12 tools)

- **`frostwire_download_torrent`** — Start a torrent. Args: `source` (`magnet`/`url`/`file`), `value` (URI/path), `saveDir` (optional). Returns `downloadId` (info hash).
- **`frostwire_download_http`** — Start an HTTP download. Args: `url`, `saveDir` (optional).
- **`frostwire_transfers_list`** — List transfers. Args: `filter` (`all`/`downloading`/`seeding`/`completed`/`paused`/`error`), `offset`, `limit` (default 50).
- **`frostwire_transfer_detail`** — Full detail + file items for one transfer. Arg: `downloadId`.
- **`frostwire_transfer_pause`** / **`frostwire_transfer_resume`** — Control one transfer. Arg: `downloadId`.
- **`frostwire_transfer_remove`** — Remove a transfer. Args: `downloadId`, `deleteData` (boolean, default false).
- **`frostwire_transfer_set_speed_limit`** — Per-transfer speed limits (KB/s, 0 = unlimited). Args: `downloadId`, `downloadLimit`, `uploadLimit`.
- **`frostwire_transfer_sequential`** — Toggle sequential download. Args: `downloadId`, `enabled`.
- **`frostwire_transfer_recheck`** — Force hash re-check. Arg: `downloadId`.
- **`frostwire_transfer_announce`** — Force tracker re-announce. Arg: `downloadId`.
- **`frostwire_transfer_file_priority`** — Set per-file priority. Args: `downloadId`, `fileIndices` (array), `priority` (`skip`, `normal`, `high`).

Transfer summary fields: `id`, `name`, `displayName`, `state`, `progress`, `size`, `downloaded`, `uploaded`, `downloadSpeed`, `uploadSpeed`, `eta`, `seeds`/`peers` (connected/total), `infoHash`, `savePath`, `magnetUri`, `sequential`.

File item fields: `name`, `size`, `downloaded`, `progress`, `complete`, `skipped`, `path`.

### Cloud / Telluride (2 tools)

- **`frostwire_cloud_search`** — Resolve a cloud URL (YouTube, SoundCloud, etc.). Arg: `url`. Returns `title`, `thumbnail`, and `formats` array with `formatId`, `ext`, `resolution`, `fileSize`, `vcodec`, `acodec`.
- **`frostwire_cloud_download`** — Download a specific format. Args: `url`, `formatId`, `saveDir` (optional).

### Library (5 tools)

- **`frostwire_library_scan`** — Trigger a library rescan.
- **`frostwire_library_list`** — List files. Args: `mediaType` (`all`/`audio`/`video`/`images`/`documents`/`torrents`), `sortBy` (`name`/`size`/`date`), `offset`, `limit` (default 100, max 1000).
- **`frostwire_library_file_detail`** — Metadata for one file. Arg: `path`.
- **`frostwire_library_open`** — Open file with system default app. Arg: `path`.
- **`frostwire_library_delete`** — Delete file from disk. Arg: `path`.

### BitTorrent Engine (4 tools)

- **`frostwire_btengine_status`** — Returns `running`, `paused`, `totalDownload`, `totalUpload`.
- **`frostwire_btengine_pause`** / **`frostwire_btengine_resume`** — Global pause/resume.
- **`frostwire_btengine_create_torrent`** — Create a `.torrent`. Args: `path` (required), `trackers` (array), `webSeeds` (array), `torrentType` (`v1`/`v2`/`hybrid`, default hybrid), `pieceSize` (bytes, 0 = auto). Returns `torrentPath`, `magnetUri`, `infoHash`.

### Settings (2 tools)

- **`frostwire_settings_get`** — Read a category. Arg: `category` (`connection`/`library`/`sharing`/`search`/`vpn`).
- **`frostwire_settings_set`** — Write a whitelisted setting. Args: `key`, `value`.

Allowed keys: `VPN_DROP_PROTECTION`, `ENABLE_DHT`, `I2P_ENABLED`, `I2P_HOSTNAME`, `I2P_PORT`, `I2P_ALLOW_MIXED`, `SEED_FINISHED_TORRENTS`, `ALLOW_PARTIAL_SHARING`, plus all `*_SEARCH_ENABLED` keys.

### VPN (2 tools)

- **`frostwire_vpn_status`** — Returns `active`, `vpnName` (if detected), `dropProtectionEnabled`.
- **`frostwire_vpn_drop_protection`** — Toggle drop protection. Arg: `enabled`.

### IP Filter (4 tools)

- **`frostwire_ipfilter_list`** — List blocked ranges. Args: `offset`, `limit`.
- **`frostwire_ipfilter_add`** — Add a range. Args: `startIp`, `endIp`, `description`.
- **`frostwire_ipfilter_clear`** — Clear all ranges.
- **`frostwire_ipfilter_import`** — Import from a URL or file. Args: `source` (`url`/`file`), `value`.

## Common Workflows

**Search and download a torrent:**
1. `frostwire_search` → get `token`
2. `frostwire_search_results` (token, sortBy: `seeds`) → pick a result
3. `frostwire_download_torrent` (source: `magnet`, value: result.magnetUri or torrentUrl)

**Inspect an active transfer:**
1. `frostwire_transfers_list` (filter: `downloading`) → get `downloadId`
2. `frostwire_transfer_detail` (downloadId) → see file list and progress
3. `frostwire_transfer_file_priority` to skip unwanted files

**Create and seed a torrent:**
1. `frostwire_btengine_create_torrent` (path, trackers) → get `torrentPath`
2. `frostwire_download_torrent` (source: `file`, value: torrentPath) to start seeding

## Important Notes

- All heavy operations (JNI, I/O) run on background threads; tools return JSON immediately.
- Search is asynchronous — always poll with `frostwire_search_results` using the `token`.
- `downloadId` is always the torrent info hash (hex string).
- File priorities: `skip` = `Priority.IGNORE` (won't download), `normal` = `Priority.NORMAL`, `high` = `Priority.SEVEN`.
- Settings writes are whitelisted; unknown keys return an error with the allowed list.
