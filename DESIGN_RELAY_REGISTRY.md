# FrostWire Distributed Search & IceBridge Relay Network

> **Note (2026)**: This document is the original design + evolutionary record. The system has been implemented with a hybrid identity (direct TCP) + data (IceBridge rUDP) architecture, remote IceBridge support, rich configuration, multi-token auth, etc. See the "Current Implementation" and "Note on Future Documentation" sections near the end. A separate comprehensive `ICEBRIDGE.md` user/operator guide is still needed.

> Corrected design for the FrostWire distributed search network and privacy-preserving relay layer.
> This version replaces the earlier relay-registry/index-announcement design with primitives that
> actually exist in BitTorrent DHT (BEP 5, BEP 44, BEP 46) and makes a headless cloud **IceBridge**
> servent a first-class deployment target.
> 
> **IceBridge** = the purpose-agnostic relay/servent layer. **Distributed Search** = the first
> application that routes search messages through IceBridge.

---

## 0. Goal

Every FrostWire user can become part of a global, privacy-preserving search network:

1. Each node indexes the torrents it seeds or intentionally shares.
2. Each node can search its own local index instantly.
3. Each node can ask trusted peers and relays for matching results.
4. Well-connected nodes, including headless cloud **IceBridge** servents, help NAT-restricted users reach the mesh.
5. Every identity, manifest, query result, and trust record is signed with Ed25519.
6. Search payloads are end-to-end encrypted so IceBridge servents do not learn query text or result content.

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
| FrostWire Desktop    |       | Headless IceBridge   |       | FrostWire Desktop    |
| LocalIndex(SQLite)   |<----->| Servent              |<----->| LocalIndex(SQLite)   |
| Search UI            | HTTP | RAM caches only      | rUDP  | Search UI            |
| or stdio             | or   | forwards opaque blobs| E2E   | or stdio             |
+----------------------+       +----------------------+       +----------------------+
        |                              |                              |
        v                              v                              v
  BEP 46 mutable                 BEP 46 mutable                 BEP 46 mutable
  index manifest                 relay config                   index manifest
  (single-writer)                (single-writer)                (single-writer)
```

FrostWire (desktop or Android) primarily routes distributed search and peer communication through an **IceBridge** instance.

**Current architecture (as implemented):**

- **Identity / bootstrap plane**: Direct TCP (on the "relay listen port") using `IncomingRelayServer` + `DirectTcpPeerAuthenticator` + `OutgoingRelayClient`. Used to exchange and verify `IdentityRecord` (BEP 46) after DHT discovery. This gives each node a verified Ed25519 pubkey + rUDP endpoint for a peer.
- **Data / relay plane**: Opaque payloads routed over the **IceBridge rUDP mesh** (`IceBridgeSearchTransport` implementing `DistributedSearchTransport`). Desktop talks to a local or remote IceBridge via its HTTP control API (`/send`, `/poll`, `/route`, `/lookup` etc.). IceBridge handles reliable delivery, NAT traversal, and forwarding over rUDP.
- Desktop **never** sends search payloads directly to remote peers over the identity TCP path in the final flow; it goes through the IceBridge transport abstraction.
- A local IceBridge can be forked as a child process (default) or a remote/standalone IceBridge can be used (useful for cloud relays or testing).

| Role              | Desktop                  | IceBridge (local child or remote) | Needs disk          | Purpose |
|-------------------|--------------------------|-----------------------------------|---------------------|---------|
| Index role        | Yes (LocalIndexTable)    | Optional (cache)                  | SQLite / RAM        | Local torrent metadata |
| Search role       | Yes (`DistributedSearchPerformer`) | Routes opaque payloads        | Small               | Mesh query + result merge |
| Relay / Forwarder | Can (via local IceBridge)| Primary                           | RAM only (cloud)    | rUDP mesh + forwarding for NATed nodes |
| Identity server   | Yes (`IncomingRelayServer` on relay port) | Also (standalone)            | No                  | TCP bootstrap for verified pubkeys + rUDP addrs |

Desktop normally forks a **local IceBridge** child. Users can also point at a remote IceBridge (e.g. one launched with `./gradlew icebridge` on a VPS). Android supports in-process IceBridge.

---

## 3. Module Layout

### 3.1 `common/`

Package: `com.frostwire.search.relay` (core relay + transport abstractions) and `com.frostwire.search.relay.icebridge` (servent + control).

Shared across Desktop, Android, and standalone IceBridge:

- **IceBridge core**: `IceBridgeServer`, `IceBridgeConfig`, `IceBridgeTokens` (multiple bearer tokens for control API, hot-reloadable from file), `IceBridgeClient` (HTTP client to control API).
- **Control API** (`control/`): `ControlServer` (Netty, bound to 127.0.0.1), `ControlHandler` (endpoints: /register, /route, /lookup, /send, /poll, /metrics, /health). Auth via `X-IceBridge-Token`.
- **rUDP mesh**: `RudpServer`, `RudpSessionManager`, `RudpSession`, fragmentation, hole-punch support.
- **Peer/relay management**: `PeerRegistry`, `PeerDirectory`, `PeerRegistrySync`.
- **Discovery + identity**: `DhtPeerDiscoverySource`, `PeerDiscovery` (with `DirectTcpPeerAuthenticator` for bootstrap), `IdentityKeys`, `IdentityRecord` (includes `rudpPort` + `role` since v2), `DhtAdvertiser`, `IdentityRecordPublisher`.
- **Search transport abstraction**: `DistributedSearchTransport`, `IceBridgeSearchTransport` (polls IceBridge + sends opaque payloads), `IncomingSearchRequestHandler`, `DistributedSearchPerformer`.
- **Wire + records**: `RelayWireCodec`, `RemoteSearchRequest`/`RemoteSearchResponse` (signed), `RelayConstants`.
- **Constants / helpers**: `RelayConstants` (topics, default ports).

The IceBridge fat JAR (built by `desktop/build.gradle` `icebridgeJar` task) contains only what's needed for a headless servent (no Swing, no SQLite for pure-relay use). Main class: `com.frostwire.search.relay.icebridge.IceBridgeServer`.

Desktop also starts its own `IncomingRelayServer` (direct TCP identity handshake listener) on the configured relay listen port in addition to talking to IceBridge for data.

### 3.2 `desktop/`

- `LocalIndexTable` + `SharedTorrentIndexer` + local search performer (unchanged).
- `SearchEnginesSettings`: `ICEBRIDGE_ENABLED`, `ICEBRIDGE_USE_REMOTE`, `ICEBRIDGE_REMOTE_URL`, `ICEBRIDGE_REMOTE_AUTH_TOKEN`, `ICEBRIDGE_RUDP_PORT`, `ICEBRIDGE_RELAY_LISTEN_PORT`, `ICEBRIDGE_ROLE`, etc.
- `IceBridgeSettingsPaneItem`: full options panel (enable, local vs remote radio, bind host, rUDP port, relay listen port (identity), role, remote URL + token).
- `IceBridgeProcessLauncher`: builds command line for child `icebridge.jar` (passes --rudp-port, --relay-port, --control-http-port, --role, --host, --auth-token, --identity-file). Shares the same Ed25519 identity.
- `Initializer.startRelayStack` / `startIceBridgeSearch` / `startPeerDiscovery`: wires everything, supports remote IceBridge client, starts desktop's `IncomingRelayServer`, DHT advertiser, discovery scheduler (aggressive 30s + immediate tick), `PeerRegistrySync`.
- `IceBridgeSearchTransport` (via `DistributedSearchTransport`): the bridge used by `DistributedSearchPerformer`.
- `IceBridgeClient` (OkHttp) used for both local child and remote.
- UI elements updated to label results appropriately (Local / Distributed / Peer).

### 3.3 `icebridge` deployment artifact

Standalone fat JAR built by `icebridgeJar` task (main = `IceBridgeServer`).

Run with:

```bash
java -jar icebridge.jar
# or
./gradlew icebridge
```

Config sources (all supported):
- `.env` in cwd (loaded by `loadDotEnv()`, turned into system props).
- `ICEBRIDGE_*` environment variables.
- Command-line flags (`--rudp-port`, `--relay-port`, `--control-http-port`, `--role`, `--host`, `--identity-file`, `--auth-tokens-file`, `--generate-token`, ...).
- For remote use from desktop: the control URL + one bearer token.

**Auth tokens** (control API):
- Single `--auth-token` still supported (legacy / local child).
- Preferred: `ICEBRIDGE_AUTH_TOKENS_FILE` (default `icebridge-tokens.txt`). One token per line.
- `--generate-token` command: prints exactly one new secure token to stdout (for admin to hand to a user), appends it to the tokens file with timestamp comment. Tokens are hot-reloaded on every auth check — no restart needed.
- Header: `X-IceBridge-Token`.
- `/health` is unauthenticated.

The child launched by desktop receives its token on the command line and uses it for its local control API.

### 3.4 Android

Reuses large parts of `common/`:
- In-process `IceBridgeServer` (no subprocess).
- `AndroidLocalIndex` (SQLite + FTS5).
- `AndroidRelayStack`, `AndroidKarmaChainStore`, etc.
- Same discovery, identity, and transport abstractions.
- `IncomingRelayServer` (identity TCP) and rUDP also run in-process.

See `EngineForegroundService` wiring.

---

## 3.5 IceBridge + Hybrid Relay Reality (Current)

See the detailed description added in section 3.5 above (the hybrid identity TCP + IceBridge rUDP model, ports, auth tokens, generate command, desktop + Android integration, and discovery flow).

Key evolution points from the original design:
- A pragmatic **direct TCP identity/bootstrap plane** was added (and stabilized) alongside the rUDP data plane. This makes initial pubkey + endpoint verification reliable even before/without a full mesh.
- Desktop maintains both its own `IncomingRelayServer` (for identity handshakes) **and** a (child or remote) IceBridge instance for rUDP routing.
- Full remote IceBridge support + rich configuration UI were added.
- Auth for the control plane moved to a multi-token file with a safe one-time ` --generate-token` command and hot reload.
- Discovery includes strong self-skip (own pub + local interfaces) to handle co-located desktop + standalone cases.

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

`LocalIndexTable` then implements this interface. A headless IceBridge servent can use an in-memory or no-op implementation.

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

After `dhtGetPeers(topicHash)`, servents connect to candidates over **rUDP** and perform:

1. NAT traversal / hole punching where needed, falling back to a known forwarder.
2. Exchange `IdentityRecord`.
3. Verify Ed25519 self-signature.
4. Challenge/response with 32-byte nonces.
5. Derive transport key with X25519 + KDF label `frostwire-relay-transport-v1`.
6. Start encrypted framed messages.

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

IceBridge forwards opaque encrypted frames:

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

## 10. Headless IceBridge

### 10.1 Purpose

IceBridge is a cloud-friendly relay and bootstrap servent. It runs as a standalone process on a
small VM with fast networking, decent CPU/RAM, and little disk. The same code can also be embedded
inside FrostWire for development or special deployments.

### 10.2 Requirements

- No Swing.
- No Android.
- No media library.
- No SQLite requirement for pure relay mode.
- One config file or environment-variable config.
- RAM-bounded LRU caches (near-zero disk I/O).
- rUDP mesh transport.
- HTTP or stdio control interface for local FrostWire integration.
- Graceful shutdown and key persistence.

### 10.3 Minimal config

```
ICEBRIDGE_ROLE=relay
ICEBRIDGE_RUDP_PORT=49152
ICEBRIDGE_CONTROL_HTTP_PORT=8080
ICEBRIDGE_CONTROL_STDIO=false
ICEBRIDGE_MAX_QPS=5
ICEBRIDGE_MAX_PEERS=500
ICEBRIDGE_CACHE_MB=256
ICEBRIDGE_IDENTITY_FILE=/var/lib/icebridge/identity.dat
```

### 10.4 Metrics

Expose logs first. Add HTTP metrics later if needed:

- connected peers
- relayed messages per minute
- rejected messages
- signature failures
- average relay latency
- cache hit rate
- rUDP hole-punch success/failure

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
| 12 | IceBridge servent + CLI | starts, announces, relays in local cluster over rUDP | `[all] Add headless IceBridge relay servent` |
| 13 | IceBridge HTTP/stdio control API | FrostWire talks to local IceBridge daemon | `[common] Add IceBridge local control API` |
| 14 | `DistributedSearchPerformer` over IceBridge | local + mesh merge via local daemon | `[desktop] Add distributed search performer` |
| 15 | `DistributedSearchEventBus` | thread-safe pub/sub | `[common] Add distributed search event bus` |
| 16 | `DistributedSearchLogPanel` | row model, click actions | `[desktop] Add distributed search log panel` |
| 17 | `PeerBrowseSearchPerformer` + window | browse known peer manifest | `[desktop] Add peer browse search window` |
| 18 | `SignatureVerifier` everywhere | invalid sig rejects | `[common] Add signature verification for relay records` |
| 19 | `TrustStore` + `WoTValidator` | BFS depth/expiry | `[common] Add trust store and WoT validator` |
| 20 | DoS protection + metrics | qps limit, in-flight caps | `[common] Add relay rate limits and metrics` |
| 21 | Documentation + changelog | docs only | `[all] Document distributed search and IceBridge phase 4` |

### 12.1 Current Implementation Checkpoint (mid-2026)

**Major components delivered** (beyond the original stabilization list):

- Full IceBridge servent (`IceBridgeServer` + Netty control API + rUDP mesh).
- Local child process launch + **remote/standalone IceBridge** support (with auth token).
- Comprehensive configuration: `SearchEnginesSettings`, full Swing options pane (`IceBridgeSettingsPaneItem`), env var overrides, `.env` support for standalone.
- Separate configurable ports: rUDP port vs relay listen port (identity TCP handshake).
- `DistributedSearchTransport` + `IceBridgeSearchTransport` + `DistributedSearchPerformer` wired through IceBridge (opaque payloads).
- `PeerRegistrySync`, `IncomingSearchRequestHandler`.
- `DhtPeerDiscoverySource` (aggressive relays-first), self-skip using own Ed25519 pub + local interface matching.
- `IdentityRecord` v2 (rudpPort + role), BEP 46 publication + direct TCP bootstrap.
- Multiple bearer tokens for control API + `--generate-token` (prints once, hot-reloadable file).
- Android in-process port (AndroidRelayStack, AndroidLocalIndex, etc.).
- Many robustness fixes (self-discovery, port conflicts, scheduler timing, etc.).

**Hybrid nature** (the biggest evolution from the pure original doc):
The system uses a direct TCP identity/auth plane (for reliable verified pubkeys from DHT contacts) + the IceBridge rUDP plane (for actual search payload routing and forwarding). This was a pragmatic stabilization + bootstrap choice.

Search payloads over IceBridge are opaque to the relay layer but are signed and processed by the search application code.

The original "everything opaque through local IceBridge only" vision is largely realized for the data path; the identity plane is an additional direct mechanism.

### 12.2 Stabilization Pass Before Distributed Search

Do this pass before implementing build step 13 (`DistributedSearchPerformer`). The goal is not to finish the
full privacy-preserving relay. The goal is to make the current direct peer-search substrate correct, honest,
and safe enough to build on.

| # | Component | Work | Tests required | Commit message |
|---|-----------|------|----------------|----------------|
| S1 | Request canonicalization | ~~Fix non-empty `RemoteSearchRequest.path` canonical bytes. `pathLengthBytes()` must account for the path count plus each length-prefixed 32-byte hop, or canonicalization should switch to `ByteArrayOutputStream` to avoid pre-computed sizes.~~ **Done.** `pathLengthBytes()` is now an instance method that returns `4 + path.length * (4 + 32)`. | ~~Signed request with one and multiple path entries verifies; malformed hop lengths reject; existing empty-path signatures still verify.~~ `RemoteSearchRequestTest.canonicalBytesWithNonEmptyPathAreStableAndVerifiable` covers this. | `[common] Fix relay request path canonicalization` |
| S2 | Forwarding semantics | ~~Decide v1 policy: either remove/disable multi-hop forwarding for the direct TCP protocol, or introduce a proper forwarded envelope signed by the forwarding peer without pretending the original requester signed mutated bytes. Recommended v1: direct peer search only, no forwarding.~~ **Done (2026-07 revision).** `DistributedSearchPerformer` sets **`ttl=0`**. `IncomingSearchRequestHandler.MULTI_HOP_FORWARDING_ENABLED=false` (re-sign with forwarder key while keeping original `requesterPub` fails `RelaySearchService` verify). `RelayRole.forward()` API remains for a future dual-envelope protocol but is unused on the IceBridge path. Regression: `IncomingSearchRequestHandlerTest.reSignedForwardWouldFailRelaySearchServiceVerify`, `multiHopForwardingDisabledEvenWhenTtlPositive`. | Dual-envelope multi-hop is **open future work** (see §14). | `[common] Disable multi-hop until dual-envelope` |
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

Most of the original stabilization + IceBridge + distributed performer work is complete (with the hybrid architecture noted above). The system is in active use and refinement.

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
5. Bob's desktop starts its local IceBridge servent.
6. Bob's desktop searches local FTS5 and asks the local IceBridge to send signed `SearchQuery` messages to authenticated peers and forwarders.
7. Alice receives the query through her IceBridge servent, searches her local index, returns a signed result envelope through IceBridge.
8. Bob verifies Alice's signature and trust score, merges the result into normal Search UI.
9. If Bob is behind symmetric NAT, IceBridge finds a forwarder and relays the encrypted query/response.
10. Bob can click Alice's peer badge to browse Alice's signed manifest.

---

## 14. Open Questions / Future Work

- **Dual-envelope multi-hop** — original requester sig over immutable query fields + separate forwarder hop signatures; re-enable `ttl>0` only after this.
- Full end-to-end encryption for search payloads over the rUDP mesh (X25519 keys are present in `IdentityKeys` but not yet used for all paths).
- Richer trust / WoT UI and defaults.
- When / whether desktop nodes should advertise as forwarders.
- Metrics, observability, and operations tooling for cloud IceBridge instances.
- Android device experience and battery impact of the in-process relay stack.
- Stable public relay discovery / curated bootstrap lists.
- Standalone IceBridge optional DHT announce (jlibtorrent SessionManager) so pure forwarders appear on `frostwire-relays-v1` without a desktop peer.
- Desktop Identity Settings “Initialize Identity” button (PoW generate with progress).

---

## 15. Note on Future Documentation

A dedicated `ICEBRIDGE.md` (or `docs/icebridge/`) is planned. It should cover:

- Capabilities (rUDP mesh, control API endpoints, NAT traversal, roles).
- Usage on desktop (settings pane, local child vs remote, what the ports mean).
- Usage on Android.
- Running a standalone cloud relay (`./gradlew icebridge`, .env, --generate-token, tokens file, ports, role=FORWARDER, firewall).
- Security model (identity, bearer tokens for control, signed search messages).
- Debugging, logs, and common pitfalls (self-discovery, bind host, port conflicts).
- How the identity TCP plane and rUDP data plane interact.

This DESIGN document captures the historical intent and the major evolutionary steps taken.

---

*Status (2026)*: The IceBridge servent, control API, rUDP mesh, desktop integration (local + remote), Android support, configuration UI, discovery, and distributed search performer over the transport have all been implemented and iterated on (including multiple-token auth, self-skip, port configurability, and stabilization of the direct identity bootstrap path).

Next documentation / cleanup steps include a comprehensive `ICEBRIDGE.md` (see note at top of this file).
