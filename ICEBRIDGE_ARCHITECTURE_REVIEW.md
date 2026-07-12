# IceBridge Architecture Review Notes

> **Origin:** External architecture review (2026-07), captured from design discussion and
> folded into the FrostWire repo as a durable north-star for layering.
> **Related:** `DESIGN_RELAY_REGISTRY.md` §0 (MUST layering), `ICEBRIDGE.md`,
> `desktop/ICEBRIDGE_REVISION_PLAN.md` §D.

## Overall Impression

The current direction is strong. Separating IceBridge into its own process and treating it as
independent infrastructure instead of embedding networking logic inside FrostWire is a solid
architectural decision.

The layering appears to naturally separate:

```
FrostWire
    │
    ▼
Search Protocol
    │
    ▼
IceBridge Overlay
    │
    ▼
UDP / DHT / Transport
```

The main recommendation is to preserve this separation as the project evolves.

---

# Architectural Recommendations

## 1. IceBridge should be a generic messaging fabric

Avoid thinking of IceBridge as "the distributed search network."

Instead, think of it as a generic overlay capable of transporting arbitrary protocols.

Search becomes merely the first protocol implemented on top.

Future protocols could include:

- Distributed Search
- Chat
- Metadata Exchange
- Peer Discovery
- Pub/Sub
- Distributed AI inference
- Node Telemetry
- File synchronization

The transport layer should never understand the meaning of the messages.

---

## 2. Maintain strict separation between transport and application logic

IceBridge should know only:

- peers
- sessions
- routing
- authentication
- transport
- message delivery

It should **not** know about:

- torrents
- search
- files
- metadata
- FrostWire-specific concepts

Prefer APIs such as:

```java
sendMessage(protocolId, payload)
```

instead of

```java
sendTorrentSearch(...)
```

Application protocols should exist entirely above IceBridge.

---

## 3. Nodes should advertise capabilities

Instead of assigning fixed roles like:

- Relay
- Client

consider capability advertisement.

Example capabilities:

- RELAY
- SEARCH
- INDEX
- STORE
- DHT
- TORRENT
- AI

Nodes can gain or lose capabilities dynamically without changing protocol semantics.

This also allows Android devices or residential peers to become indexes later.

---

## 4. Stream search results

Instead of:

```
SEARCH
↓
large response
```

consider

```
SEARCH
RESULT
RESULT
RESULT
END
```

Advantages:

- lower latency
- early UI updates
- cancellation support
- avoids large responses
- better scalability

---

## 5. Think beyond distributed search

The current architecture naturally lends itself to becoming a general-purpose overlay network.

Search is only one possible protocol.

Future possibilities include:

- decentralized chat
- distributed indexes
- AI compute requests
- torrent metadata distribution
- software updates
- distributed monitoring

Keeping IceBridge generic today enables these tomorrow.

---

## 6. Separate the control plane from the data plane

The package structure suggests this direction already.

Ideally:

**Control Plane**

- authentication
- capability exchange
- peer discovery
- session negotiation
- routing updates
- health
- metrics

**Data Plane**

- opaque protocol messages
- routing
- transport
- delivery

Keeping these independent simplifies protocol evolution.

---

## 7. Route by responsibility in the future

Current search propagation is perfectly reasonable during early development.

Long term, consider routing requests toward nodes responsible for portions of a distributed index.

Conceptually:

```
hash(keyword)
        ↓
responsible nodes
        ↓
local search
        ↓
stream results
```

This eventually eliminates flooding while remaining compatible with the same transport layer.

---

## 8. IceBridge as infrastructure

Architecturally, IceBridge resembles infrastructure projects like:

- libtorrent (networking separated from UI)
- libp2p
- QUIC transport layers
- overlay routing frameworks

The goal should be that another application—not FrostWire—could adopt IceBridge without modifications.

---

# Potential Review Order

If performing a deeper architecture review:

1. `IceBridgeServer`
2. `IceBridgeDhtSession`
3. `peer/`
4. `control/`
5. UDP transport
6. Search protocol
7. FrostWire integration

This order follows the dependency graph from infrastructure upward.

---

# Key Architectural Principle

The most important conclusion from this discussion:

> IceBridge should evolve into a reusable overlay networking platform that FrostWire happens to
> use—not a networking component built specifically for FrostWire.

If this boundary remains clean, new protocols can be added indefinitely without modifying the
networking layer.

---

# Overall Assessment

**Strengths:**

- Excellent separation into an independent process
- Clear infrastructure/application boundary
- Protocol-agnostic mindset
- Good package organization
- Future-friendly architecture

**Primary recommendation:**

Protect the abstraction boundary aggressively. Every time a FrostWire-specific concept tries to
leak into IceBridge, it is worth stopping and asking whether it belongs one layer higher.

Doing so will maximize long-term extensibility and allow IceBridge to become a reusable
networking substrate rather than a single-purpose distributed search engine.

---

# Implementation status (repo snapshot)

Status of each recommendation against the FrostWire tree (update when work lands):

| # | Recommendation | Status | Notes |
|---|----------------|--------|-------|
| 1 | Generic messaging fabric | **Done** | Opaque `/send` + `/poll`; all mesh payloads IBP1-framed with `protocolId`; search is Protocol #1 |
| 2 | Transport ≠ application | **Done (behavioral)** | No search schema in icebridge core. `send(target, protocolId, payload)`; residual package names (`search.relay`, `*SearchTransport`) only |
| 3 | Capability advertisement | **Done** | `NodeCapabilities` bitflags; IdentityRecord **v3** `caps`; PeerDirectory filter `topByTrustVerified(limit, caps)`; roles still derived for DHT |
| 4 | Stream search results | **Done** | `RemoteSearchResponse` chunk + `final`; handler streams large sets; performer accumulates until final |
| 5 | Beyond search | **Aligned** | Non-SEARCH protocols framed with IBP1; handlers filter by protocolId |
| 6 | Control vs data plane | **Aligned** | Control HTTP (`control/`) vs rUDP mesh (`udp/`); identity TCP is bootstrap/control-adjacent |
| 7 | Route by responsibility | **Foundation** | `KeyspaceRouter` XOR-ranks peers by SHA-1(keywords); multi-hop mesh RELAY with hop TTL between FORWARDER nodes; full exclusive keyspace ownership still future |
| 8 | Reusable infrastructure | **In progress** | Standalone JAR + DHT announce; still branded `frostwire-*` topics / package path |

## Guardrails (MUST when changing IceBridge)

1. Do not add torrent, LocalIndex, SearchEngine, or karma types to `icebridge/` packages.
2. Prefer `send(opaque)` / future `send(protocolId, opaque)` over any search-named mesh API.
3. New app protocols demux **above** IceBridge (or via `protocolId` only).
4. Streaming, E2E payload crypto, multi-hop dual-envelope, and inverted-index routing are **app or protocol** concerns—not IceBridge core.
5. When reviewing PRs that touch IceBridge, use the review order above (infra first).
