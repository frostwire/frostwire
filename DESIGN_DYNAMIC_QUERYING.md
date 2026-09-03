# Dynamic Querying for Distributed Search (Slice C)

> **Status (2026):** Originator-only change. No wire bump, no forwarder change.
> Operator guide: `ICEBRIDGE.md`. Registry design: `DESIGN_RELAY_REGISTRY.md`.
> Code: `common/.../relay/DistributedSearchPerformer.java`,
> `common/.../relay/DynamicQueryConfig.java`.
> Style rules: `skills/frostwire-engineer/SKILL.md`.

> **IceBridge** stays protocol-agnostic opaque-byte routing. Dynamic querying
> is an application choice made by the search **originator**; forwarders,
> roles, and TTL ceilings are untouched (sibling slices own those).

---

## 0. Problem

Every IceBridge distributed search pays full depth x full fanout
(`M=30`, `searchTtl=3`) even when the first few peers would have answered.
Popular queries burn mesh budget for hits that were already in hand;
only rare content needs the full flood.

Gnutella solved this with dynamic querying: start narrow/shallow, expand
only while results are thin.

---

## 1. Goal

The originator (`DistributedSearchPerformer`) probes a small peer subset
with `TTL=1` and a short timeout first, then expands to larger subsets
with larger TTLs **only** while the deduped hit count is below target.
Worst-case coverage equals today's single-shot search, so recall cannot
regress.

Non-goals:

- Changing forwarder behavior (`RelayRole`, `IncomingSearchRequestHandler`).
- Changing topology defaults or ceilings (`IceBridgeTopology`).
- Any wire-protocol change (no version bump on
  `RemoteSearchRequest` / `RemoteSearchResponse`).
- Keyspace-exclusive routing (ranking stays advisory, as today).

---

## 2. Phases

| Phase | First-hop contacts (cumulative) | Request TTL | Wait budget |
|-------|---------------------------------|-------------|-------------|
| 1 — probe | 4 | 1 | 3 s |
| 2 — expand | 12 | 2 | 6 s |
| 3 — full (legacy) | `maxPeers` (default 30) | topology `searchTtl` (default 3) | `peerTimeoutSec` (default 10 s) |

Constants live in `DynamicQueryConfig` (`PHASE1_MAX_PEERS`,
`PHASE1_TTL`, `PHASE1_TIMEOUT_SEC`, `PHASE2_*`). The phase-1/2 waits are
capped by the performer's `peerTimeoutSec`, so a test performer with a
1 s timeout still phases by fanout/TTL rather than by waiting longer
than the legacy path.

Peer ranking is computed **once** (trust-verified, SEARCH-capable first,
then keyspace XOR order — unchanged from today) and phases consume
**disjoint slices** of that ranking: `[0,4)`, `[4,12)`, `[12,maxPeers)`.
No peer is contacted twice.

---

## 3. Budgets

- **First-hop sends.** Slices are disjoint and jointly capped by
  `maxPeers`, so total sends across all phases is `<= maxPeers` — the
  same bound as one legacy search.
- **Depth.** Per-phase TTLs are clamped through
  `IceBridgeTopology.clampRemainingTtl(0, ...)` (soft-max honored) and
  never exceed the topology `searchTtl`. The worst-case hop flood of a
  fully-expanded query equals one legacy query.
- **Time.** Worst case is roughly the sum of the phase waits; the common
  case (early stop) is one short phase instead of one full timeout.

---

## 4. Stop conditions

After local results and after each phase, the originator stops when any
holds (checked in this order):

1. **Enough hits.** `dedupeByInfoHash(local + peers so far) >= desiredResults`
   (default 10, via `DynamicQueryConfig.withDesiredResults(n)`).
   Local hits count: a query the local index already answers sends zero
   peer requests.
2. **No peers left.** The ranked slice is empty (`end <= offset`).
3. **Phase cap reached.** At most `MAX_PHASES = 3` phases.
4. **Stopped.** The performer was `stop()`ped; pending phases are skipped
   and no `onResults` is delivered (same as legacy).

Every run — including phase-3 exhaustion — delivers exactly one
`onResults` with whatever was found (fail-closed, as today).

---

## 5. Backwards compatibility

- **Wire:** none. Each phase sends ordinary v2 `RemoteSearchRequest`
  frames using the existing `ttl`/`path` hop fields. Older peers see
  nothing new; there is deliberately **no wire version bump**.
- **Forwarders:** untouched. Sibling slices own `RelayRole.forward`,
  `IncomingSearchRequestHandler.forwardRequest`, and role gating.
- **Topology:** defaults and ceilings untouched.
- **API:** existing `DistributedSearchPerformer` constructors are
  unchanged and default to the legacy single-shot search (`null`
  config). Dynamic querying is opt-in via additive overloads taking a
  `DynamicQueryConfig`; new code is new methods/overloads only.
- **Recall:** the final phase contacts all remaining peers (up to
  `maxPeers`) with the full topology TTL — the same first-hop set and
  depth as today. A query that runs all three phases covers what the
  legacy search covered.

---

## 6. Tests

`desktop/src/test/java/com/frostwire/search/relay/DynamicQueryPerformerTest.java`
(JUnit 5, fake transport that counts `send`s and signs canned answers):

- (a) A query answerable in phase 1 (4 answers, target 2) sends exactly
  the phase-1 subset, all with `ttl=1` — phase 2 never fires.
- (b) A thin query (4 hits, target 10) expands: sends exceed the phase-1
  subset and a follow-up request carries `ttl=2`.
- (c) Total sends across all phases stay within budget (`<= maxPeers`)
  even when nobody answers.
- (d) Phase-3 exhaustion still delivers one `onResults` with the partial
  hits that did arrive.

---

## 7. Future (out of scope)

- Adaptive per-query targets (e.g. rarer keywords start wider).
- Remembering which peers answer quickly and probing them first.
- Re-querying early-phase peers at full TTL in the final phase (today
  they keep their probe TTL; later phases reach past them instead).
