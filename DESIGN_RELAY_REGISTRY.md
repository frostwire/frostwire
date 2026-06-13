# FrostWire Distributed Search & Relay Network

> Corrected design for the FrostWire distributed search network and privacy-preserving relay layer.
> This version replaces the earlier relay-registry/index-announcement design with primitives that
> actually exist in BitTorrent DHT (BEP 5, BEP 44, BEP 46) and makes a headless cloud `relayd`
> a first-class deployment target.

---

## 0. Goal

Every FrostWire user can become part of a global, privacy-preserving search network:

1. Each node indexes the torrents it seeds or intentionally shares.
2. Each node can search its own local index instantly.
3. Each node can ask trusted peers and relays for matching results.
4. Well-connected nodes, including headless cloud `relayd` nodes, help NAT-restricted users reach the mesh.
5. Every identity, manifest, query result, and trust record is signed with Ed25519.
6. Search payloads are end-to-end encrypted so relays do not learn query text or result content.

Non-goals for v1:

- Onion routing.
- Full-text search inside file contents.
- Android relay metrics UI.
- Global keyword DHT indexing. Keyword search is handled by the peer mesh, not by DHT records.

---

## 1. DHT Reality Checks

These rules drive the whole design.

### 1.1 BEP 5 is multi-writer rendezvous

`dhtAnnounce(infohash, port)` and `dhtGetPeers(infohash)` are the right primitives when many peers
need to appear under one shared topic. The topic is a 20-byte SHA-1 target.

Use this for:

- Discovering FrostWire peers.
- Discovering public relays.
- Discovering bootstrap-capable supernodes.

### 1.2 BEP 44 immutable items are content-addressed

`dhtPutItem(Entry)` returns a key derived from the bencoded value. The application cannot choose the
lookup key. This is useful for content-addressed blobs, not for registries.

Do not use immutable DHT items for anything that must be discovered from a well-known application key.

### 1.3 BEP 44/46 mutable items are single-writer

`dhtPutItem(pubkey, privkey, entry, salt)` is addressed by `SHA1(pubkey + salt)`. One publisher owns
one mutable record per salt. This is perfect for "my latest index manifest" and impossible for
"everyone writes to one relay registry".

Use this for:

- A node's latest signed identity pointer.
- A node's latest signed index manifest pointer.
- A relay's latest signed public configuration.

### 1.4 DHT targets are 20 bytes

Do not use `SHA-256(... )[:32]` for DHT lookup targets. Use SHA-1 for DHT targets and SHA-256 only
inside signed application records when a strong content hash is needed.

---

## 2. Architecture Overview

```
                         Mainline DHT (BEP 5 rendezvous)
                         topics: frostwire-peers-v1,
                                 frostwire-relays-v1
                                      |
                                      v
+----------------------+       +----------------------+       +----------------------+
| FrostWire Desktop    | <---> | Headless relayd      | <---> | FrostWire Desktop    |
| LocalIndex(SQLite)   | uTP   | RAM caches only      | uTP   | LocalIndex(SQLite)   |
| Search UI            | E2E   | forwards opaque blobs| E2E   | Search UI            |
+----------------------+       +----------------------+       +----------------------+
        |                              |                              |
        v                              v                              v
  BEP 46 mutable                 BEP 46 mutable                 BEP 46 mutable
  index manifest                 relay config                   index manifest
  (single-writer)                (single-writer)                (single-writer)
```

The system has three independent roles that can be composed:

| Role | Runs on desktop | Runs on relayd | Needs disk | Purpose |
|------|-----------------|----------------|------------|---------|
| Index role | yes | optional | SQLite or manifest cache | Build/search local torrent metadata. |
| Search role | yes | optional | small cache | Query peers and merge signed results. |
| Relay role | optional | yes | no | Forward encrypted query/result blobs for NAT-restricted nodes. |

Desktop will usually run all roles except heavy public relay. A cloud `relayd` usually runs relay role
and a small manifest cache only.

---

## 3. Module Layout

### 3.1 `common/`

Package: `com.frostwire.search.relay`

Contains code that can run on Desktop, Android, and headless relayd:

- Records: `IdentityRecord`, `RelayRecord`, `IndexManifest`, `SearchQuery`, `SearchResultEnvelope`,
  `TrustDelegation`.
- Crypto: `SignatureVerifier`, `KeyMaterial`, `SessionCipher`.
- Protocol: `PeerHandshake`, `RelayHandshake`, `PeerMesh`, `RelayClient`, `RelayServer` interfaces.
- Interfaces: `LocalIndex`, `ManifestStore`, `TrustStore`.
- Constants: `RelayConstants`.

No Swing, Android, or desktop database imports are allowed here.

### 3.2 `desktop/`

Desktop implementations and UI:

- `LocalIndexTable`: JDBC SQLite + FTS5 implementation of `LocalIndex`.
- `SharedTorrentIndexer`: `BTEngineListener` that populates the local index.
- `LocalSharedTorrentSearchPerformer`: local-only search.
- `DistributedSearchPerformer`: local + mesh search merger.
- `DistributedSearchEventBus`: in-process event stream.
- `DistributedSearchLogPanel` and `PeerBrowseWindow`: Swing UI.

### 3.3 `relayd/` (new module)

Headless cloud relay:

- Single JVM main class.
- Depends on `common/` and jlibtorrent.
- No Swing, no Android, no media library, no SQLite requirement.
- Configurable role flags: `--relay`, `--index-cache`, `--bootstrap`.
- RAM-bounded caches for peer identities, manifests, and recent route state.
- Designed for fast networking, decent CPU/memory, minimal disk.

### 3.4 `android/` (later)

Android can reuse common records and protocol. Android UI starts with peer browse and local search;
relay metrics UI is out of scope for v1.

---

## 4. Local Index

### 4.1 Current implementation

Done:

- `LocalSharedTorrent` in `common/`.
- `LocalIndexTable` in `desktop/`.
- JUnit coverage for schema, CRUD, FTS5 search, injection-safe query sanitation, required indexes,
  reopen persistence, and republish cutoff queries.

The local index stores one row per infohash:

```sql
CREATE TABLE shared_torrents (
    info_hash              TEXT PRIMARY KEY,
    name                   TEXT NOT NULL,
    size_bytes             INTEGER NOT NULL,
    file_count             INTEGER NOT NULL,
    files_json             TEXT NOT NULL,
    tags                   TEXT,
    publisher_node_id      TEXT NOT NULL,
    publisher_ed25519_pub  BLOB NOT NULL,
    publisher_utp_port     INTEGER,
    added_at               INTEGER NOT NULL,
    last_seen_at           INTEGER NOT NULL,
    last_published_at      INTEGER
);
CREATE INDEX idx_shared_torrents_added ON shared_torrents(added_at);
CREATE INDEX idx_shared_torrents_name ON shared_torrents(name);
CREATE INDEX idx_shared_torrents_last_published_at ON shared_torrents(last_published_at);
CREATE VIRTUAL TABLE shared_torrents_fts USING fts5(
    name, tags, content='shared_torrents', content_rowid='rowid'
);
```

### 4.2 Next implementation

Add `LocalIndex` in `common/` so desktop SQLite is one implementation, not the protocol boundary.

```java
public interface LocalIndex {
    void upsert(LocalSharedTorrent torrent);
    Optional<LocalSharedTorrent> get(String infoHashHex);
    List<LocalSharedTorrent> search(String query, int maxResults);
    List<String> needsRepublish(long nowSec, long thresholdSec);
    void markPublished(String infoHashHex, long timestampSec);
}
```

`LocalIndexTable` then implements this interface. `relayd` can use an in-memory or no-op implementation.

---

## 5. Identity

### 5.1 Identity fields

Each node has stable key material:

- Ed25519 keypair for signatures and identity.
- X25519 public key for ECDH transport sessions.
- Libtorrent node ID for DHT addressability.
- uTP listen port, or 0 when not listening.

Desktop identity file:

- `~/.frostwire/identity.dat`
- POSIX mode 0600.

Android identity file:

- App-private `identity.dat`.

### 5.2 Identity record rules

Fix before building more protocol code:

- Canonical bytes must be bencode, not hand-rolled JSON.
- `IdentityRecord` should own `toEntry()` and `fromEntry()`.
- A publishable identity must always have a valid signature. No zero-signature limbo state.
- Remove `computeDhtKey()`. Identity is exchanged after rendezvous connection or fetched as a BEP 46
  mutable item by known pubkey + salt, not by arbitrary 32-byte key.

---

## 6. Peer and Relay Discovery

### 6.1 Topics

Use SHA-1 topic hashes:

| Topic | Purpose |
|-------|---------|
| `SHA1("frostwire-peers-v1")` | General FrostWire peers willing to answer search. |
| `SHA1("frostwire-relays-v1")` | Public relays willing to forward encrypted blobs. |
| `SHA1("frostwire-bootstrap-v1")` | Stable foundation/headless bootstrap nodes. |

### 6.2 Announce

Nodes periodically call:

```java
session.dhtAnnounce(topicHash, utpPort, flags);
```

Desktop nodes announce peer topic by default. Public relay role announces relay topic. Foundation nodes may
announce bootstrap topic.

### 6.3 Connect and authenticate

After `dhtGetPeers(topicHash)`, the node connects to candidates over uTP and performs:

1. Exchange `IdentityRecord`.
2. Verify Ed25519 self-signature.
3. Challenge/response with 32-byte nonces.
4. Derive transport key with X25519 + KDF label `frostwire-relay-transport-v1`.
5. Start encrypted framed messages.

Bad identities are dropped before any search payload is accepted.

---

## 7. Index Manifests

Instead of one DHT record per torrent, each publisher exposes one latest manifest.

### 7.1 BEP 46 mutable item

Address:

- key: publisher Ed25519 public key
- salt: `frostwire-index-v1`

Value:

```json
{
  "v": 1,
  "publisher_node_id": "20-byte hex",
  "publisher_ed25519_pub": "base64url-no-padding",
  "created_at": 1749400000,
  "last_seen": 1749403600,
  "seq": 42,
  "count": 150,
  "manifest_kind": "inline|torrent|merkle",
  "manifest_sha256": "hex",
  "manifest_ref": "inline blob, magnet URI, or chunk root",
  "sig": "base64url-no-padding"
}
```

v1 should start with inline manifests capped by byte size. If a node shares a large catalog, switch to a
small torrent containing the manifest and publish the magnet/infohash as `manifest_ref`.

### 7.2 Manifest content

The manifest is a signed list of compact rows:

```json
{
  "v": 1,
  "publisher_ed25519_pub": "base64url-no-padding",
  "rows": [
    {
      "info_hash": "hex",
      "name": "ubuntu-24.04.iso",
      "size_bytes": 5347732480,
      "file_count": 1,
      "tags": "linux iso ubuntu",
      "last_seen": 1749403600
    }
  ],
  "sig": "base64url-no-padding"
}
```

Rows are scored by text match and trust score. Exact torrent availability still uses BitTorrent's normal
`get_peers(info_hash)`.

---

## 8. Search Flow

### 8.1 Local search

`LocalSharedTorrentSearchPerformer` queries `LocalIndex.search(query, maxResults)` and maps rows to
`SearchResult` with source `Local`.

### 8.2 Mesh search

`DistributedSearchPerformer` runs local search and mesh search in parallel:

1. Normalize the query.
2. Search local FTS5.
3. Send encrypted `SearchQuery` to connected trusted peers and selected relays.
4. Peers search their local index and return signed `SearchResultEnvelope` messages.
5. Merge by infohash, score by local text score, trust score, seed confidence, recency.
6. Return results to the standard Search UI.

### 8.3 Relay search

The relay forwards opaque encrypted frames:

- It sees source connection, target peer or route hint, payload size, and timing.
- It does not see query text or result contents.
- It enforces per-source and per-target rate limits.

The relay can also act as a stable peer directory cache, but cached identities/manifests must be signed
and independently verifiable by clients.

### 8.4 Hop limit

v1 uses hop limit 2:

- hop 0: local node
- hop 1: directly connected peers and relays
- hop 2: peers behind those relays

No onion routing in v1.

---

## 9. Trust and Reputation

### 9.1 Layer 1: signatures

Every identity, manifest, and result envelope is Ed25519-signed. Invalid signatures are rejected and
logged as `TRUST_REJECTED`.

### 9.2 Layer 2: local trust store

v1 trust store fields:

```java
TrustEntry {
    byte[] ed25519Pub;
    String nodeIdHex;
    int depth;
    double trustScore;
    long firstSeen;
    long lastSeen;
    long lastGoodResultAt;
    long rejectedCount;
}
```

Default scoring:

- FrostWire release-signing root: 1.0
- Explicit user trust: 0.9
- Depth 1 delegation: 0.7
- Depth 2 delegation: 0.4
- Depth 3 delegation: 0.2
- Unknown: accepted only if `allow_untrusted_distributed_search` is enabled, scored <= 0.05

### 9.3 Layer 3: Web of Trust delegation

Delegation record:

```json
{
  "v": 1,
  "subject_ed25519_pub": "base64url-no-padding",
  "issuer_ed25519_pub": "base64url-no-padding",
  "delegation_depth": 1,
  "expiry": 1780936000,
  "sig": "base64url-no-padding"
}
```

`WoTValidator` performs bounded BFS from FrostWire roots, max depth 3. Expired or invalid delegations are
ignored.

### 9.4 Layer 4: rate limits and spam heuristics

v1:

- Per-source token bucket.
- Max in-flight relayed query per `(searcher, relay, target)`.
- Result caps per query and per peer.

Post-v1:

- Trust-scaled rate limits.
- Dedup-by-content spam demotion.
- Signed malware hash lists from trusted peers.

---

## 10. Headless `relayd`

### 10.1 Purpose

`relayd` is a cloud-friendly relay and bootstrap node. It should run on a small VM with fast networking,
decent CPU/RAM, and little disk.

### 10.2 Requirements

- No Swing.
- No Android.
- No media library.
- No SQLite requirement for pure relay mode.
- One config file or environment-variable config.
- RAM-bounded LRU caches.
- jlibtorrent DHT/uTP only.
- Graceful shutdown and key persistence.

### 10.3 Minimal config

```
FROSTWIRE_RELAY_ROLE=relay
FROSTWIRE_RELAY_PORT=49152
FROSTWIRE_RELAY_MAX_QPS=5
FROSTWIRE_RELAY_MAX_PEERS=500
FROSTWIRE_RELAY_CACHE_MB=256
FROSTWIRE_RELAY_IDENTITY_FILE=/var/lib/frostwire-relayd/identity.dat
```

### 10.4 Metrics

Expose logs first. Add HTTP metrics later if needed:

- connected peers
- relayed queries per minute
- rejected queries
- signature failures
- average relay latency
- cache hit rate

---

## 11. Event Log UI

Desktop event types:

```java
IDENTITY_VERIFIED
PEER_CONNECTED
PEER_REJECTED
LOCAL_INDEX_UPDATED
MANIFEST_PUBLISHED
MANIFEST_FETCHED
SEARCH_OUTGOING
SEARCH_INCOMING
SEARCH_RELAYED
SEARCH_RESULTS_MERGED
TRUST_VERIFIED
TRUST_REJECTED
PEER_BROWSE_STARTED
```

`DistributedSearchLogPanel` shows newest-first events and lets users click a peer to open
`PeerBrowseWindow`.

---

## 12. Revised Build Order

The tables below are the historical target order. Section 12.1 records where the current implementation
actually stands, and section 12.2 defines the stabilization work that now gates the next feature step.

Already done:

| # | Component | Status |
|---|-----------|--------|
| 1 | `LocalDhtCluster` test harness | Done, needs cleanup |
| 2 | `IdentityRecord` | Done, needs protocol fix |
| 3 | `LocalSharedTorrent` + `LocalIndexTable` | Done |
| 4 | `skills/frostwire-engineer/SKILL.md` | Done |

Foundation fixes before new features:

| # | Component | Tests required | Commit message |
|---|-----------|----------------|----------------|
| F1 | `IdentityRecord` bencode canonical form, no `computeDhtKey`, no unsigned limbo | serialize/parse/sign/verify | `[common] Fix IdentityRecord for BEP44-compatible signed bencode` |
| F2 | `LocalDhtCluster` and DHT tests cleanup | deterministic DHT put/get, no sleeps/println | `[desktop] Clean up LocalDhtCluster and DHT tests` |
| F3 | `RelayConstants` + `LocalIndex` interface | compile + interface tests | `[common] Add relay constants and LocalIndex interface` |

Feature build order:

| # | Component | Tests required | Commit message |
|---|-----------|----------------|----------------|
| 5 | BEP 5 rendezvous discovery | local DHT announce/get_peers | `[common] Add FrostWire peer rendezvous discovery` |
| 6 | `SharedTorrentIndexer` | idempotent insert, magnet -> metadata update | `[desktop] Add SharedTorrentIndexer for local shared torrents` |
| 7 | `LocalSharedTorrentSearchPerformer` | query mapping to SearchResult | `[desktop] Add local shared-torrent search performer` |
| 8 | Wire `Local` engine into SearchEngine | integration search returns Local results | `[desktop] Wire Local search engine` |
| 9 | `IndexManifest` + manifest publisher | sign/verify/fetch latest manifest | `[common] Add signed index manifests` |
| 10 | `PeerMesh` query/result messages | encrypted round trip, hop limit | `[common] Add encrypted peer mesh search protocol` |
| 11 | `RelayServer` + `RelayClient` | relay opaque encrypted payload | `[common] Add encrypted relay server and client` |
| 12 | `relayd` module | starts, announces, relays in local cluster | `[all] Add headless frostwire relayd` |
| 13 | `DistributedSearchPerformer` | local + mesh merge | `[desktop] Add distributed search performer` |
| 14 | `DistributedSearchEventBus` | thread-safe pub/sub | `[common] Add distributed search event bus` |
| 15 | `DistributedSearchLogPanel` | row model, click actions | `[desktop] Add distributed search log panel` |
| 16 | `PeerBrowseSearchPerformer` + window | browse known peer manifest | `[desktop] Add peer browse search window` |
| 17 | `SignatureVerifier` everywhere | invalid sig rejects | `[common] Add signature verification for relay records` |
| 18 | `TrustStore` + `WoTValidator` | BFS depth/expiry | `[common] Add trust store and WoT validator` |
| 19 | DoS protection + metrics | qps limit, in-flight caps | `[common] Add relay rate limits and metrics` |
| 20 | Documentation + changelog | docs only | `[all] Document distributed search and relay phase 4` |

### 12.1 Current Implementation Checkpoint

The current codebase is ahead of the original build order in some areas and still behind it in others.
Before adding the user-facing distributed engine, treat the current relay stack as a **direct TCP peer-search
prototype**, not as the privacy-preserving encrypted relay described above.

Implemented and useful:

- `LocalIndex`, `LocalIndexTable`, `SharedTorrentIndexer`, and `LocalSharedTorrentSearchPerformer`.
- `SearchEngine.LOCAL` wiring, including optional karma-weighted local result ordering.
- PoW-capable `IdentityKeys`, signed bencoded `IdentityRecord`, and BEP 46 identity publication.
- BEP 5 topic helpers for peers, relays, and bootstrap nodes.
- `PeerDiscovery`, `PeerDirectory`, and scheduled peer discovery scaffolding.
- Signed `RemoteSearchRequest` / `RemoteSearchResponse` records, framed wire codec, plain TCP client/server.
- Per-peer karma-chain scaffolding and DHT publisher/fetcher interfaces.
- Localhost multi-instance relay tests and in-process DHT smoke tests.

Not implemented yet:

- `DistributedSearchPerformer`, `DISTRIBUTED_ID`, and `DISTRIBUTED_SEARCH_ENABLED`.
- Authenticated discovery that turns a DHT endpoint into a verified Ed25519 identity before querying.
- Response verification in the outgoing client boundary.
- Encrypted `PeerMesh`, `SearchQuery`, `SearchResultEnvelope`, `SessionCipher`, or transport handshake.
- Opaque relay forwarding; current relay requests expose query keywords to the target node.
- `relayd` as a headless deployable module.
- Public DHT two-machine advertise -> discover -> query verification.

### 12.2 Stabilization Pass Before Distributed Search

Do this pass before implementing build step 13 (`DistributedSearchPerformer`). The goal is not to finish the
full privacy-preserving relay. The goal is to make the current direct peer-search substrate correct, honest,
and safe enough to build on.

| # | Component | Work | Tests required | Commit message |
|---|-----------|------|----------------|----------------|
| S1 | Request canonicalization | ~~Fix non-empty `RemoteSearchRequest.path` canonical bytes. `pathLengthBytes()` must account for the path count plus each length-prefixed 32-byte hop, or canonicalization should switch to `ByteArrayOutputStream` to avoid pre-computed sizes.~~ **Done.** `pathLengthBytes()` is now an instance method that returns `4 + path.length * (4 + 32)`. | ~~Signed request with one and multiple path entries verifies; malformed hop lengths reject; existing empty-path signatures still verify.~~ `RemoteSearchRequestTest.canonicalBytesWithNonEmptyPathAreStableAndVerifiable` covers this. | `[common] Fix relay request path canonicalization` |
| S2 | Forwarding semantics | ~~Decide v1 policy: either remove/disable multi-hop forwarding for the direct TCP protocol, or introduce a proper forwarded envelope signed by the forwarding peer without pretending the original requester signed mutated bytes. Recommended v1: direct peer search only, no forwarding.~~ **Done.** `RelayRole.forward()` now throws `UnsupportedOperationException`. | ~~`RelayRole.forward` tests updated to reject unsupported forwarding, or new envelope tests prove requester and forwarder signatures independently verify.~~ `RelayRoleTest.forwardIsUnsupportedInDirectPeerSearchV1` covers this. | `[common] Clarify relay forwarding semantics` |
| S3 | Response verification | ~~Add client-side verification of `RemoteSearchResponse`: expected responder Ed25519 pubkey, matching nonce, timestamp skew, and signature over canonical bytes. `OutgoingRelayClient` should either take the expected responder pubkey or expose a verified send method.~~ **Done.** `OutgoingRelayClient.send(host, port, request, expectedResponderPub)` verifies nonce, timestamp skew, and signature. | ~~Valid response accepted; wrong nonce rejected; stale response rejected; bad signature rejected; response signed by another peer rejected.~~ `RelayWireTest.verifiedSendAcceptsValidResponse`, `verifiedSendRejectsWrongResponderPub`, `verifyResponseRejectsWrongNonce`, `verifyResponseRejectsStaleTimestamp`, `verifyResponseRejectsBadSignature` cover this. | `[common] Verify relay responses at client boundary` |
| S4 | Authenticated peer discovery | ~~After BEP 5 endpoint discovery, connect to the endpoint and learn a real `IdentityRecord` before adding it as queryable. Placeholder `SHA-256(host:port)` entries may remain only as temporary candidates and must not be used for trust or distributed search.~~ **Done.** Added `PeerAuthenticator`, `DirectTcpPeerAuthenticator`, TCP identity handshake in `IncomingRelayServer`/`OutgoingRelayClient`, `PeerDirectory.upsertVerified`, and wired it in `Initializer`. | ~~Fake discovery source plus fake identity fetch/exchange upgrades placeholder to real pubkey; invalid identity is dropped; duplicate endpoint does not create duplicate trusted peers.~~ `PeerDiscoveryTest.discoverWithAuthenticatorRegistersVerifiedPeers`, `discoverWithAuthenticatorDropsUnauthenticatedPeers`, `discoverWithAuthenticatorIsIdempotentForVerifiedPeers`; `RelayWireTest.identityHandshakeReturnsConfiguredRecord`, `directTcpAuthenticatorAcceptsValidServerIdentity`, `directTcpAuthenticatorRejectsMissingServerIdentity` cover this. | `[common] Authenticate peers discovered through DHT` |
| S5 | Startup scheduling | ~~Make advertiser, discovery, and karma scheduler run one initial tick soon after startup instead of waiting 5-30 minutes. Preserve throttling inside publishers.~~ **Done.** `DhtAdvertiser`, `PeerDiscoveryScheduler`, and `KarmaChainCommitScheduler` now schedule with initial delay 0. | ~~Scheduler tests show first tick occurs immediately or through explicit `startAndTick`; repeated starts remain idempotent; publisher throttle still prevents excessive DHT puts.~~ Existing scheduler tests pass. | `[desktop] Run initial relay scheduler ticks at startup` |
| S6 | Karma persistence continuity | ~~Implement `KarmaChainTable.loadChain(ownerPub)` and use it in `KarmaChainWriter`. Stop recreating the local chain from genesis on every app start. Avoid `INSERT OR REPLACE` for append-only rows unless replacing the identical row is proven safe.~~ **Done.** `KarmaChainTable.loadChain` reconstructs entries from stored columns; `KarmaChainWriter` loads the chain on construction. | ~~Append, close, reopen, load, append next entry with continuing seq/head; mismatched owner rows ignored or rejected; duplicate seq with different hash rejects.~~ `KarmaChainTableTest.loadChainRestoresPersistedEntries`, `loadChainReturnsEmptyChainForMissingOwner`, `loadChainReturnsFreshChainWhenStoredEntriesAreInvalid` cover this. | `[desktop] Load persisted karma chain on startup` |
| S7 | Karma verifier hardening | ~~Strengthen `KarmaChain.verify` and its tests. Verification must reject broken hash links, wrong sequence order, mixed owners, endorsement before epoch commitment, endorsement in an uncommitted epoch if that remains policy, and over-budget chains.~~ **Done.** Added `KarmaChain.load` and tests that construct genuinely invalid chains via `KarmaChainEntry.create*` and `fromStoredFields`. | ~~Negative tests must actually construct invalid signed entries via `KarmaChainEntry.reconstruct` or test-only builders, not no-op assertions.~~ `KarmaChainTest.verifyRejectsNonGenesisFirstEntry`, `verifyRejectsBrokenHashLink`, `verifyRejectsWrongSequenceNumber`, `verifyRejectsOutOfOrderBlockHeights`, `verifyRejectsOverBudgetEndorsements`, `verifyRejectsEndorsementBeforeEpochCommitment`, `verifyRejectsOutOfEpochEndorsementBlock`, `verifyRejectsMixedEndorserPubs`, `verifyRejectsBadSignature` cover this. | `[common] Harden karma chain verification` |
| S8 | Public truth in UI/docs | ~~Until encrypted mesh exists, name the current network path as direct peer search in logs/docs/UI. Do not claim privacy-preserving relay for plaintext TCP requests.~~ **Done.** `Initializer` logs/comments now say "direct peer-search server". This design doc section updated. | ~~Documentation assertions updated; any user-facing strings use `I18n.tr`; no references imply relays cannot see query text unless encrypted path is active.~~ Documentation and `Initializer` log messages updated. | `[all] Document direct peer search stabilization scope` |

Acceptance criteria for the stabilization pass:

- A direct TCP peer-search request can be signed, sent, answered, and verified end-to-end.
- A client never accepts an unsigned, wrong-signer, stale, or wrong-nonce response.
- Discovered endpoints are not considered trusted/queryable until tied to a verified `IdentityRecord`.
- The local karma chain survives app restarts without resetting sequence numbers or overwriting prior entries.
- Tests prove the meaningful negative cases instead of only proving valid paths.
- Documentation clearly distinguishes direct peer search from the future encrypted opaque relay.

All stabilization tasks are complete. The next step is **build step 13** — the user-facing distributed search performer — implemented in a deliberately narrow form:

1. `DistributedSearchPerformer` searches local FTS5 and directly authenticated peers.
2. It queries `PeerDirectory.topByTrustVerified(n)` so placeholder/unverified peers are skipped.
3. It sends signed direct requests with `ttl=0` / forwarding disabled.
4. It verifies each response with the expected responder pubkey before merging.
5. It labels results as `Distributed` or `Peer`, not `Relay`, until encrypted relay mode exists.
6. It fails closed: unreachable peers, invalid responses, and rate-limited peers simply contribute no results.

---

## 13. Acceptance Story

Alice starts FrostWire and adds a magnet for `ubuntu-24.04.iso`.

1. `SharedTorrentIndexer` inserts it into `LocalIndexTable`.
2. `ManifestPublisher` updates Alice's BEP 46 signed manifest.
3. Alice announces herself under `frostwire-peers-v1`.
4. Bob searches for `ubuntu 24`.
5. Bob's desktop searches local FTS5 and sends signed direct peer-search queries to authenticated peers.
6. Alice receives the query, searches her local index, returns a signed result envelope.
7. Bob verifies Alice's signature and trust score, merges the result into normal Search UI.
8. If Bob is behind symmetric NAT, direct queries may fail; NAT traversal / encrypted relay is future work.
9. Bob can click Alice's peer badge to browse Alice's signed manifest.

---

## 14. Open Questions

- Which Ed25519 public keys are FrostWire trust roots?
- Should unknown peers be hidden by default or shown under an "Untrusted" fold?
- Should desktop run relay role by default only when the node has a public/stable port?
- How large should inline manifests be before switching to torrent-backed manifests?
- Should `relayd` expose HTTP metrics in v1 or keep logs-only until operations need more?

---

*Next step: complete the stabilization pass in section 12.2 before adding the user-facing distributed search performer.*
