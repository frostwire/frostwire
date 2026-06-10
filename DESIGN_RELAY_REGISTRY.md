# FrostWire Distributed Search & Relay Registry

> Combined design for: (1) the relay-node registry (Phase 4a), and (2) the
> end-to-end distributed search system (Phase 4) that makes every FrostWire
> user a search engine for every other user.

---

## 0. Vision

Every FrostWire user automatically indexes the torrents they seed and
publishes that index to a global, DHT-backed registry. Every user also
relays encrypted search queries on behalf of firewalled/symmetric-NAT
peers. Searches run against the union of:

  * the user's own local index (what they seed)
  * the indexes of every other FrostWire user they trust

with results appearing in the standard search UI next to TPB, Bitsearch,
etc. A new "Distributed" top-level panel shows a real-time event log
(indexes added, queries in/out, relay handoffs) where clicking a peer
ID opens that peer's full published index as browsable search results.

Trust is enforced via Ed25519 signatures on every record, with a
Web-of-Trust overlay (rooted at FrostWire release-signing keys) for
spam and malware mitigation once the network is large enough to be
attacked.

**Non-goals (this design):** onion routing, full-text search across
file contents, mobile UI for relay metrics.

---

## 1. Architecture Overview

```
+--------------------------------------------------------------+
|  FrostWire Desktop                                           |
|                                                              |
|  BTEngine ──► SharedTorrentIndexer ──► Local Index (SQLite)  |
|                    │                       │                 |
|                    │ on add/update         │ query           |
|                    ▼                       ▼                 |
|         RelayAnnouncementPublisher   LocalSearchPerformer   |
|                    │                       ▲                 |
|                    │ publish               │ merge           |
|                    ▼                       │                 |
|         BEP 46 DHT (via jlibtorrent)  ◄────┘                 |
|                    │                                          |
|                    │ fetch                                    |
|                    ▼                                          |
|         IndexDiscoveryService                                 |
|                    │                                          |
|                    ▼                                          |
|         DistributedSearchEventBus ──► DistributedSearchLogPanel
|                    │                                          |
|                    ▼                                          |
|         PeerBrowseSearchPerformer  (click a peer in the log)  |
+--------------------------------------------------------------+
```

Components:

| # | Component | Package | Status |
|---|-----------|---------|--------|
| 1 | `LocalDhtCluster` (test JUnit ext) | `com.frostwire.tests.dht` | **Done** |
| 2 | `IdentityRecord` (DHT identity)   | `com.frostwire.search.relay` | **Done** |
| 3 | `SharedTorrentIndexer` (auto-magic on downloadAdded) | `com.frostwire.search.relay` | Pending |
| 4 | `LocalIndexTable` (SQLite FTS5)   | `com.frostwire.search.relay` | Pending |
| 5 | `LocalSharedTorrentSearchPerformer` | `com.frostwire.search.relay` | Pending |
| 6 | `DistributedSearchEventBus` (in-process pub/sub) | `com.frostwire.search.relay` | Pending |
| 7 | `DistributedSearchLogPanel` (Swing UI) | `com.limegroup.gnutella.gui.search` | Pending |
| 8 | `IndexAnnouncement` (signed, DHT-published) | `com.frostwire.search.relay` | Pending |
| 9 | `RelayAnnouncementPublisher` (background re-publisher) | `com.frostwire.search.relay` | Pending |
| 10 | `RelayRecord` (DHT relay candidate record) | `com.frostwire.search.relay` | Pending |
| 11 | `IndexDiscoveryService` (pull-mode fetcher) | `com.frostwire.search.relay` | Pending |
| 12 | `PeerBrowseSearchPerformer` (browse a peer) | `com.frostwire.search.relay` | Pending |
| 13 | `SignatureVerifier` (Ed25519 on every fetched record) | `com.frostwire.search.relay` | Pending |
| 14 | `TrustStore` + `WoTValidator` (delegation graph) | `com.frostwire.search.relay` | Pending |
| 15 | `RelayServer` (forwards encrypted queries, rate-limited) | `com.frostwire.search.relay` | Pending |
| 16 | `RelayClient` (uses relay when direct uTP fails) | `com.frostwire.search.relay` | Pending |


---

## 2. Hook Points & Existing Infrastructure We Reuse

### 2.1 BTEngine listener (the "auto-magic" trigger)

`BTEngineListener.downloadAdded(BTEngine, BTDownload)` fires whenever a
torrent is added to libtorrent — seeding, downloading, or magnet-open.
This is the single entry point for `SharedTorrentIndexer`. We do NOT
need to scan directories or hook `LibraryFilesTableMediator`.

`BTEngineListener.downloadUpdate(BTEngine, BTDownload)` is the
secondary hook for when a torrent's metadata becomes available (magnet
→ torrent) or its file list is finalized.

### 2.2 BTContext identity persistence

`BTContext.homeDir` already exists. Identity keys persist at:
* Desktop: `~/.frostwire/identity.dat` (Ed25519 + X25519 keys, 32 bytes
  each, plain — file is created with mode 0600)
* Android: app-private `identity.dat` (same scheme)

Loaded once at startup; created on first run.

### 2.3 SQLite infrastructure

`com.frostwire.database.sqlite.SQLiteOpenHelper` and
`SQLiteDatabase` already wrap the JDBC driver and handle schema
migrations. Our new tables live in the existing
`~/.frostwire/frostwire.db` (desktop) / `frostwire.db` (android).

### 2.4 Search engine wiring

`SearchEngine.getEngines()` returns the list shown in the search UI.
Adding a new engine is the same pattern as Bitsearch:

```java
private static final SearchEngine DISTRIBUTED =
    new SearchEngine(SearchEngineID.DISTRIBUTED_ID, "Distributed",
        SearchEnginesSettings.DISTRIBUTED_SEARCH_ENABLED, null) {
        @Override public SearchPerformer getPerformer(String token, String query) {
            return new DistributedSearchPerformer(token, query);
        }
    };
```

`SearchMediator.performSearch()` already fans out to all enabled
engines and merges results — zero changes needed there.

### 2.5 BEP 46 DHT (jlibtorrent)

* `SessionManager.swig().dht_put_item(sha1_key, salt, entry, sig)` —
  mutable item with explicit key/salt/entry/sig
* `SessionManager.swig().dht_get_item(sha1_key)` — fetch
* `DhtMutableItemAlert` and `DhtImmutableItemAlert` — alerts with
  fetched item and signature/key
* We use **mutable** items for everything (identity, relay, index
  announcement) so we can re-publish with an updated `last_seen` and
  rotate signatures. The native DHT does the storage, replication, and
  per-publisher pubkey-keyed addressing.

---

## 3. DHT Record Types

All records are BEP 46 mutable items. Canonical bytes are the
bencode-encoded dict; signatures are Ed25519 over the bencode bytes
excluding the `sig` field itself.

### 3.1 Identity Record  *(done — see `IdentityRecord.java`)*

| Field | Type | Notes |
|-------|------|-------|
| `v` | int | version, currently 1 |
| `node_id` | 20 bytes hex | libtorrent node id (DHT address) |
| `ed25519_pub` | 32 bytes b64 | for signing & WoT |
| `x25519_pub` | 32 bytes b64 | for E2E payload encryption |
| `utp_port` | int | uTP listen port |
| `first_seen` | epoch sec | |
| `last_seen` | epoch sec | refreshed on re-publish |
| `sig` | 64 bytes b64 | Ed25519 over canonical bytes w/o sig |

DHT key: `SHA-256("frostwire-identity-v1:" + node_id)[:32]`
TTL: 30 min, re-publish every 5 min.

### 3.2 Relay Record  *(commit 3 in original plan)*

Identifies a node willing to forward search queries. The DHT key is
`SHA-256("frostwire-relay-nodes-v1")[:32]` — a well-known key, the
mutable item holds a single seq-numbered record per publisher.

```json
{
  "v": 1,
  "node_id": "A1B2...",
  "ed25519_pub": "BASE64(...)",
  "utp_port": 49152,
  "first_seen": 1749400000,
  "last_seen": 1749403600,
  "relay_count": 47,
  "max_qps": 5,
  "hashcash": "0000a1f2...",
  "sig": "BASE64(64 bytes)"
}
```

Hashcash is 4 leading zero bits of `SHA-256(pubkey || nonce)` — a
defense against Sybil relays, recomputed every re-publish.

### 3.3 Index Announcement  *(new — the core of distributed search)*

One per `(publisher_node_id, info_hash)` tuple. Immutable key so all
publishers compete on the same keyspace and the DHT deduplicates
storage.

**DHT key:** `SHA-256("frostwire-index-v1:" + info_hash)[:32]`

**Value (mutable item so we can rotate publisher):**

```json
{
  "v": 1,
  "info_hash": "AABBCC...",        // 20 bytes hex (v1) or 32 bytes hex (v2)
  "name": "ubuntu-24.04.iso",
  "size": 5347732480,
  "file_count": 1,
  "tags": ["linux", "iso", "ubuntu"],
  "publisher_node_id": "A1B2C3...",
  "publisher_ed25519_pub": "BASE64(32)",
  "publisher_utp_port": 49152,
  "first_seen": 1749400000,
  "last_seen": 1749403600,
  "ttl": 1800,                      // 30 min
  "sig": "BASE64(64)"
}
```

The mutable item's natural key is the `info_hash` (well-known), and
the publisher's pubkey (Ed25519) is part of the value. The DHT's
last-writer-wins on `last_seen` is acceptable because all publishers
sign their own entries; verifiers pick the freshest entry whose
signature checks out.

### 3.4 Trust Delegation  *(commit 6 in original plan)*

```json
{
  "v": 1,
  "subject_id": "A1B2...",
  "issuer_id": "ROOT1...",
  "delegation_depth": 1,
  "expiry": 1780936000,
  "sig": "BASE64(64 bytes)"
}
```

Validation: BFS from hardcoded root keys. Accept subject if a path
exists with `delegation_depth ≤ 3` and all entries unexpired.

---

## 4. The Auto-Magic Indexing Pipeline  *(new section)*

### 4.1 SharedTorrentIndexer

Singleton, registered as a `BTEngineListener`. Lives in
`com.frostwire.search.relay`.

Responsibilities:

1. On `downloadAdded(BTEngine, BTDownload dl)`:
   * Wait for `dl.torrentFile()` to be valid (poll with 5s timeout)
   * If `info_hash` already in `local_index` table, update only
     `last_seen_at` and re-publish announcement
   * Otherwise, insert new row and publish fresh announcement
2. On `downloadUpdate(BTEngine, BTDownload dl)`:
   * If torrent transitioned from magnet → with metadata, do step 1
   * Otherwise, no-op
3. On app shutdown: stop the re-publisher timer, persist final state

All file I/O is off the EDT (`com.frostwire.concurrent.ThreadExecutor`).

### 4.2 Local Index Table (SQLite)

```sql
CREATE TABLE shared_torrents (
    info_hash        TEXT PRIMARY KEY,    -- hex
    name             TEXT NOT NULL,
    size_bytes       INTEGER NOT NULL,
    file_count       INTEGER NOT NULL,
    files_json       TEXT NOT NULL,        -- [{path, size}, ...]
    tags             TEXT,                 -- comma-joined
    publisher_node_id TEXT NOT NULL,
    publisher_ed25519_pub BLOB NOT NULL,   -- 32 bytes
    publisher_utp_port INTEGER,
    added_at         INTEGER NOT NULL,     -- epoch sec
    last_seen_at     INTEGER NOT NULL,
    last_published_at INTEGER
);
CREATE INDEX idx_shared_torrents_added ON shared_torrents(added_at);
CREATE INDEX idx_shared_torrents_name  ON shared_torrents(name);

CREATE VIRTUAL TABLE shared_torrents_fts USING fts5(
    name, tags, content='shared_torrents', content_rowid='rowid'
);
```

`LocalSharedTorrentSearchPerformer` queries
`shared_torrents_fts MATCH ?` with the user's query, returns up to
`MAX_RESULTS` (default 50) as `SearchResult`s with
`source = "Local"`. The DHT layer is invisible here — this is a
purely local feature that works even offline.

### 4.3 RelayAnnouncementPublisher

Background `ScheduledExecutorService` running every
`REPUBLISH_INTERVAL_SEC` (default 300). Two independent timers:

* **Identity timer**: re-publishes own `IdentityRecord` with updated
  `last_seen`
* **Index timer**: scans `shared_torrents` for entries where
  `last_published_at IS NULL OR last_published_at < now - 270`,
  re-publishes each `IndexAnnouncement` with updated `last_seen`

### 4.4 IndexDiscoveryService (pull mode)

Triggered by a search. In parallel with HTTP search engines:

1. For each keyword in the query, compute
   `keyword_hash = SHA-256("frostwire-search-hint-v1:" + keyword)[:32]`
   and `dhtGetItem(keyword_hash)` to fetch a "search hint" entry
   pointing to recently-active `IndexAnnouncement` keys
2. Fetch each hinted `IndexAnnouncement`, verify Ed25519 sig, dedup
   by `info_hash`, score by (name/tag match) × (trust score)
3. Return top N as `SearchResult`s

Caching: LRU of last 10k `IndexAnnouncement`s per topic, evict by
expired-first then LRU.

### 4.5 Subscription / push mode (later)

BEP 46 supports "subscribing" to a mutable item — get notified when
a new version is published. The `IndexAnnouncement` is mutable, so
a node can `dhtGetItem` with a `seq > X` filter to get only newer
versions of a publisher's index. We can use this to get a
near-real-time feed without polling.

For the initial release, **pull mode is sufficient**. Push mode is
a follow-up.

---

## 5. The Real-Time Event Log UI  *(new section)*

### 5.1 DistributedSearchEventBus

In-process pub/sub, thread-safe, EDT-dispatched for UI consumers.

```java
public final class DistributedSearchEventBus {
    public enum Type {
        IDENTITY_PUBLISHED,
        INDEX_ANNOUNCED,           // we published a new torrent
        INDEX_DISCOVERED,          // we found a peer's index for a search
        SEARCH_INCOMING,           // someone queried via us (as relay)
        SEARCH_OUTGOING,           // we queried
        SEARCH_RELAYED,            // our query went through a relay
        PEER_BROWSE_STARTED,       // user clicked a peer_id in the log
        PEER_INDEX_FETCHED,        // we finished pulling a peer's index
        TRUST_VERIFIED,            // signature passed
        TRUST_REJECTED,            // signature failed
        RELAY_COUNT_INCREMENTED
    }

    public static void publish(Type type, Map<String, Object> payload);
    public static Disposable subscribe(Type type, Consumer<Event> handler);
}
```

All publishers use `GUIMediator.safeInvokeLater` when the handler
needs to touch Swing components.

### 5.2 DistributedSearchLogPanel

New top-level tab (next to Library / Search / Transfers), registered
in `MainFrame` and `OptionsConstructor`. Built as a subclass of
`AbstractTableMediator<DistributedSearchLogTableModel,
DistributedSearchLogTableDataLine, DistributedSearchEvent>` so it
follows the existing pattern and integrates with the GUI's tab/pane
system.

Columns:

| Time | Type | Peer / Query | Detail | Action |
|------|------|--------------|--------|--------|
| 14:23:01 | IDX_PUB | — | ubuntu-24.04.iso (5.0 GB) | [view] |
| 14:23:14 | SRC_IN  | A1B2C3D4 | "ubuntu 24" | [browse peer] |
| 14:23:14 | SRC_OUT | — | "ubuntu 24" (3 results) | [re-run] |
| 14:23:20 | RELAY   | relay7 | "ubuntu 24" → 49152 | — |

Click handlers:

* `peer_id` cell → opens `PeerBrowseWindow` (see §6)
* `info_hash` cell → opens the torrent-detail dialog / adds to
  downloads
* query cell → re-runs the search

Persistence: last 10k events written to a rotating
`distributed_search_log.jsonl` so they survive restart. Newest first,
older entries greyed out.

### 5.3 What "non-malicious behavior" means at UI time

In the initial release, every event is shown. Once `WoTValidator` is
in place (§7), events from untrusted peers are tagged
`[untrusted]` in red and folded by default. The user can expand them
manually.

---

## 6. Peer Browsing — "Click a Peer, See Their Stuff"  *(new section)*

### 6.1 PeerBrowseSearchPerformer

A new `SearchPerformer` implementation. Given a `(peer_id, query)`:

1. Fetch the peer's `IdentityRecord` from DHT (single `dhtGetItem`)
2. Verify the `ed25519_pub` matches the one in our `TrustStore`
   (or seed it as a `seen_but_unverified` peer)
3. Subscribe to the peer's `IndexAnnouncement` updates via mutable
   item `seq > last_known_seq`
4. Score each announcement by (name/tag match) × (trust score)
5. Return top N as `SearchResult`s with
   `source = "Peer:" + shortNodeId(peer_id)`

### 6.2 PeerBrowseWindow

A `JDialog` containing a `SearchMediator` instance with only the
peer-browse engine enabled. The user can:
* type a new query → re-fetches & re-scores from the same peer
* click any result → standard download flow
* "Add to my index" → stores the `IndexAnnouncement` in our local
  `peer_indexes` table for offline browsing (see §6.3)

### 6.3 Per-peer LRU index cache

```sql
CREATE TABLE peer_indexes (
    publisher_node_id TEXT NOT NULL,
    info_hash         TEXT NOT NULL,
    name              TEXT NOT NULL,
    tags              TEXT,
    size_bytes        INTEGER,
    raw_entry_bencode BLOB NOT NULL,
    fetched_at        INTEGER NOT NULL,
    expires_at        INTEGER NOT NULL,
    PRIMARY KEY (publisher_node_id, info_hash)
);
CREATE INDEX idx_peer_indexes_expires ON peer_indexes(expires_at);
```

Eviction: background sweep every 10 min deletes rows with
`expires_at < now`. LRU trim keeps last 10k rows per publisher
(soft cap, configurable).

---

## 7. Trust, Spam, and Malware Mitigation  *(new section)*

### 7.1 Layer 1 — Signature verification (now, trivial)

Every fetched `IndexAnnouncement`, `RelayRecord`, and
`IdentityRecord` MUST have its Ed25519 signature verified against
the publisher's `ed25519_pub` (in the record itself for identity;
cached in `TrustStore` for index/relay). Failures are logged to
`TRUST_REJECTED` events and the record is dropped.

`SignatureVerifier` is a stateless utility that wraps
`java.security.Signature("Ed25519")`.

### 7.2 Layer 2 — Web of Trust (Phase 4a commit 6)

`TrustStore`:
* Hardcoded list of 3-5 FrostWire release-signing Ed25519 pubkeys
  (the same keys we use to sign releases; recovery path: reinstall)
* LRU map of `(node_id → TrustEntry { depth, last_seen, trust_score }`
* `WoTValidator.canTrust(node_id, delegation_path)` does BFS
  through `TrustDelegation` records, bounded by `WOT_MAX_DEPTH = 3`

Default `trust_score`:
* Root key = 1.0
* Depth 1 = 0.7
* Depth 2 = 0.4
* Depth 3 = 0.2
* Depth 4+ = 0 (rejected unless `allow_untrusted` setting is on)

### 7.3 Layer 3 — Rate limits (now, trivial)

Per-publisher token bucket on the `RelayServer` side
(`max_qps` default 5, per design doc §4.4 / §5.8).

Per-searcher rate limit on the `RelayClient` side: at most 1
in-flight relayed query per (searcher_node_id, target_relay) pair.

### 7.4 Layer 4 — Spam heuristics (later, needs network scale)

* **Dedup by content**: same `(name, size, file_count, files_hash)`
  from N unconnected publishers in < T seconds → likely Sybil,
  demote trust
* **Hashcash cost scaling**: trusted peers pay 0 leading bits;
  untrusted pay 8+ bits; spammers self-select out
* **Suspicious pattern detection**: 100% new torrents from one peer
  in one hour = probable spam; auto-quarantine for 24h

### 7.5 Layer 5 — Malware (out of scope for indexing layer)

* Local AV scan on every torrent we add to our own
  `shared_torrents` (existing `LibraryUtils` integration); refuse to
  publish `IndexAnnouncement` for known-bad hashes
* Trust-signaled AV list: trusted peers can publish a signed
  `MalwareHashList` record; we merge it into a local
  `rejected_info_hashes` set and filter announcements before they
  hit the UI

### 7.6 Threat model summary

| Attack | Mitigation |
|--------|------------|
| Impersonation: attacker claims victim's node_id | Ed25519 sig on every record (§7.1) |
| Eclipse: attacker isolates victim from honest relays | WoT: relays must have valid trust chain from roots (§7.2) |
| Sybil: attacker floods relay registry | Hashcash on relay record, 4 leading zero bits (§3.2); cost scales with trust (§7.4) |
| DoS: attacker floods relay with queries | Per-source token bucket, `max_qps = 5` (§7.3) |
| Privacy: relay reads queries | E2E AES-GCM payload encryption; relay only sees `target_node_id` (§8.4) |
| Replay: attacker replays old challenge/response | 32-byte nonces + seq numbers on transport (§8.3) |
| Spam: attacker floods index with junk | Dedup by content + trust-scaled hashcash (§7.4) |
| Malware: attacker distributes malicious torrents | Local AV + trust-weighted AV list (§7.5) |

---

## 8. The Relay Wire Protocol  *(unchanged from original plan, kept for reference)*

### 8.1 Publishing

`publishIdentity(Session s)` and `publishRelay(Session s)` both call
`dht_put_item` with the canonical bytes and signature.

### 8.2 Discovery

```java
Optional<IdentityRecord> fetchIdentity(Session s, byte[] nodeId);
List<RelayRecord>       fetchRelays(Session s, int maxCount);
```

### 8.3 Challenge/Response (NAT traversal)

```
A → R : CHALLENGE_RESPONSE { nonce_A[32], sig_R=Ed25519(nonce_A || nonce_R) }
R → A : CHALLENGE_REPLY    { sig_A=Ed25519(x25519_pub_A || nonce_R) }
A → R : CHALLENGE_ACK      { sig_R=Ed25519(x25519_pub_R || nonce_A) }
```

After: both sides derive `frostwire-relay-transport-v1` key for
traffic (96-byte AES-GCM nonce).

### 8.4 Search via relay

Searcher computes `target_node_id` from query (or chooses a known
peer), encrypts a `RELAY_PAYLOAD` with `frostwire-search-v1` KDF,
sends to relay. Relay forwards the opaque blob; target decrypts,
sends encrypted results back. Relay only sees `target_node_id` and
the relay-hint, never the query or results.

---

## 9. Build Order  *(reconciled — was 10 commits, now 16)*

Each item is a single commit cluster (one logical change, possibly
split into 2-3 commits per the AGENTS.md granular-commits rule).

| # | Component | Tests required | Commit message |
|---|-----------|----------------|----------------|
| 1 | `LocalDhtCluster` (test JUnit ext) | 4 tests | *Done — `1df4edb66`* |
| 2 | `IdentityRecord` + unit tests + DHT integration | 8 + 1 | *Done — `7a0ca463`* |
| 3 | `LocalIndexTable` (SQLite) | unit: schema, CRUD, FTS5 match | `common] Add LocalIndexTable for shared torrents` |
| 4 | `SharedTorrentIndexer` (auto-magic on downloadAdded) | unit: idempotent insert, magnet→metadata transition | `common] Add SharedTorrentIndexer (auto-magic on downloadAdded)` |
| 5 | `LocalSharedTorrentSearchPerformer` | unit: FTS5 query, result mapping | `common] Add local search performer for shared torrents` |
| 6 | Wire `LocalSharedTorrentSearchPerformer` into `SearchEngine` | integration: search returns "Local" results | `desktop] Wire "Local" search engine into SearchEngine list` |
| 7 | `DistributedSearchEventBus` | unit: thread-safe pub/sub, EDT dispatch | `common] Add DistributedSearchEventBus` |
| 8 | `DistributedSearchLogPanel` (Swing UI) | unit: row model, click handlers | `desktop] Add DistributedSearchLogPanel` |
| 9 | `IndexAnnouncement` class | unit: serialize, parse, sign, verify | `common] Add IndexAnnouncement (DHT-published torrent metadata)` |
| 10 | `RelayAnnouncementPublisher` (background timer) | unit: re-publish interval, dedup | `common] Add RelayAnnouncementPublisher` |
| 11 | Hook `SharedTorrentIndexer` to publish `IndexAnnouncement` | integration: torrent add → DHT put | `common] Hook indexer to publish IndexAnnouncement` |
| 12 | `IndexDiscoveryService` (pull mode) | unit: keyword hash, dedup, scoring | `common] Add IndexDiscoveryService (pull mode)` |
| 13 | `DistributedSearchPerformer` (fans out to local + discovery) | integration: search hits both sources | `common] Add DistributedSearchPerformer` |
| 14 | `SignatureVerifier` | unit: Ed25519 sign/verify, cache | `common] Add SignatureVerifier for DHT records` |
| 15 | `PeerBrowseSearchPerformer` + `PeerBrowseWindow` | integration: click peer → browse results | `desktop] Add PeerBrowseSearchPerformer and window` |
| 16 | `TrustStore` + `WoTValidator` | unit: BFS, depth limits, expiry | `common] Add TrustStore and WoTValidator` |

After 16, the original relay-server commits (5.7-5.10 in the prior
doc) become commits 17-20:

| 17 | `RelayServer` (incoming challenge/response + forwarding) | integration: end-to-end NAT test |
| 18 | `RelayClient` (uses relay when direct uTP fails) | integration: hole-punch fail → relay |
| 19 | DoS protection + metrics | integration: `testRelayQpsLimit` |
| 20 | Documentation + `changelog.txt` for Phase 4 | — |

---

## 10. Configuration Constants  *(extended)*

```java
// All in com.frostwire.search.relay.RelayConstants

// === Identity & relay registry ===
int RELAY_REGISTRY_TTL_SEC            = 30 * 60;   // 30 min
int IDENTITY_REPUBLISH_INTERVAL_SEC   = 5 * 60;    // 5 min
int RELAY_REPUBLISH_INTERVAL_SEC      = 5 * 60;
int HASHCASH_LEADING_ZERO_BITS        = 4;
int DEFAULT_MAX_QPS                   = 5;
int WOT_MAX_DEPTH                     = 3;

// === Wire protocol ===
int CHALLENGE_NONCE_BYTES             = 32;
int TRANSPORT_NONCE_BYTES             = 12;        // AES-GCM 96-bit
int PAYLOAD_NONCE_BYTES               = 12;

// === DHT keys ===
String DHT_IDENTITY_PREFIX            = "frostwire-identity-v1";
String DHT_RELAY_PREFIX               = "frostwire-relay-nodes-v1";
String DHT_INDEX_PREFIX               = "frostwire-index-v1";
String DHT_SEARCH_HINT_PREFIX         = "frostwire-search-hint-v1";

// === KDF labels ===
String TRANSPORT_KDF_LABEL            = "frostwire-relay-transport-v1";
String PAYLOAD_KDF_LABEL              = "frostwire-search-v1";

// === Local index (NEW) ===
int    LOCAL_INDEX_MAX_RESULTS        = 50;
int    LOCAL_INDEX_PEER_LRU_PER_PEER  = 10_000;
int    PEER_INDEX_SWEEP_INTERVAL_SEC  = 600;       // 10 min
int    EVENT_LOG_MAX_ROWS             = 10_000;
String EVENT_LOG_PATH                 = "distributed_search_log.jsonl";
String IDENTITY_FILE                  = "identity.dat";
```

---

## 11. Out of Scope (Phase 4)

* Onion routing (Phase 5)
* Full-text search across file contents (separate feature)
* NAT hole-punch implementation — uses libtorrent's existing uTP
* Android UI for the event log (Android gets a simpler
  `PeerBrowseActivity` first; log panel is desktop-only)
* Mobile metrics — desktop has the full UI, mobile can read log
  via the existing `MCPNotificationBridge`

---

## 12. Review Checklist  *(reconciled)*

Before implementation of each new component:

* [ ] Reuse vs. new code: is there a libtorrent feature or
      `com.frostwire.util` helper that does this already?
* [ ] Off-EDT: any new code that touches `BTDownload` /
      `BTEngine` / DHT must run on a background executor
* [ ] i18n: every new user-facing string uses `I18n.tr()`
* [ ] Logging: `com.frostwire.util.Logger`, no `System.out`
* [ ] Null-safety: all new public APIs are null-safe
* [ ] Tests: at least one happy-path + one edge-case test per
      new public method
* [ ] Changelog: user-facing changes get a `changelog.txt` line

Security review points (cumulative, not per-commit):

* [ ] WoT root keys: which 3-5 FrostWire release-signing ed25519
      pubkeys? Where do we store them? (Open question — needs
      owner input.)
* [ ] Identity persistence: `~/.frostwire/identity.dat` mode 0600
      on POSIX; KeyStore-backed on Android
* [ ] `allow_untrusted` setting default: **off** (WoT required)
      for first release; opt-in for power users
* [ ] AV integration: do we block index-announce for
      locally-detected malware, or just refuse to download?
* [ ] Peer-browse: should untrusted peers' results be visually
      distinguished in the search results table?

---

## 13. End-to-End User Story (acceptance test)

> Alice starts FrostWire. She adds a magnet for `ubuntu-24.04.iso`.
> Within 5 seconds:
>   1. `SharedTorrentIndexer` inserts the row in
>      `shared_torrents`
>   2. `RelayAnnouncementPublisher` puts an `IndexAnnouncement`
>      in the DHT under `SHA-256("frostwire-index-v1:" + infohash)`
>   3. `DistributedSearchEventBus` fires `INDEX_ANNOUNCED`
>   4. `DistributedSearchLogPanel` shows a new row
>
> Bob searches for "ubuntu 24" 10 minutes later. `SearchMediator`
> fans out to Bitsearch, TPB, **and Distributed**. The Distributed
> engine runs `IndexDiscoveryService`, which fetches Alice's
> announcement from the DHT, verifies the Ed25519 sig, and
> returns the result alongside the web results. Bob sees
> `ubuntu-24.04.iso · 5.0 GB · Peer: A1B2C3D4` in the results
> table.
>
> Bob clicks the peer badge. `PeerBrowseWindow` opens. It
> subscribes to Alice's index and shows the full list of
> torrents she's sharing, ordered by recency. Bob can browse,
> filter, and download any of them — the standard download flow
> takes over once he clicks.
>
> Meanwhile, the event log shows in real time: Alice's index
> publish, Bob's query, the discovery fetch, the signature
> verify, the result merge.

---

*End of design doc. Next step: review, then Commit 3
(`LocalIndexTable` + `SharedTorrentIndexer`).*

