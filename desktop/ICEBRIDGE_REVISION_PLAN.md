# IceBridge / Distributed Relay Network — Revision Review & Execution Plan

**Date:** 2026-07-08  
**Reviewer:** Grok (orchestrator), MentisDB chain `frostwire`, agent reuse `gubatron` for durable writes  
**Inputs:** `DESIGN_RELAY_REGISTRY.md`, MentisDB lessons (#769–#847), improved `skills/frostwire-code-reviewer/SKILL.md`, source audit of `common/.../search/relay/**`, desktop `Initializer` / settings / launcher, Android `AndroidRelayStack`

---

## 1. What we built (current architecture)

### Hybrid planes (intentional evolution from pure design)

```
┌──────────────────────────────────────────────────────────────────┐
│  Discovery (BEP 5)                                               │
│  frostwire-peers-v1 / relays-v1 / bootstrap-v1                   │
└───────────────────────────┬──────────────────────────────────────┘
                            │ host:port
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  Identity plane (direct TCP, default 6888)                       │
│  IncomingRelayServer + DirectTcpPeerAuthenticator                │
│  → verified Ed25519 pub + IdentityRecord v2 (rudp_port, role)    │
└───────────────────────────┬──────────────────────────────────────┘
                            │ PeerDirectory.upsertVerified
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│  Data plane: IceBridge rUDP mesh (+ HTTP control API)            │
│  Local child process OR remote standalone                        │
│  IceBridgeSearchTransport poller → fans out payloads             │
└───────────────┬─────────────────────────────┬────────────────────┘
                │                             │
                ▼                             ▼
   DistributedSearchPerformer      IncomingSearchRequestHandler
   (signed search out)             (RelaySearchService + optional forward)
                │                             │
                └──────────► Search UI (DISTRIBUTED engine) ◄──────────┘
```

### Major delivered components

| Area | Status | Key types |
|------|--------|-----------|
| Local index | Done (desktop JDBC + Android SQLite/FTS5) | `LocalIndex`, `LocalIndexTable`, `AndroidLocalIndex` |
| Identity | Done (PoW Ed25519 + X25519 + nodeId) | `IdentityKeys`, `IdentityRecord` v2, BEP 46 publisher |
| DHT advertise / discover | Done | `DhtAdvertiser`, `DhtPeerDiscoverySource`, self-skip |
| Direct peer TCP search | Done (stabilization S1–S8) | `IncomingRelayServer`, `OutgoingRelayClient`, `RelayRole` |
| IceBridge servent | Done | `IceBridgeServer`, rUDP stack, control API, multi-token auth |
| Desktop wiring | Done | `Initializer.startRelayStack`, settings pane, process launcher, remote client |
| Distributed search performer | Done over IceBridge transport | `DistributedSearchPerformer`, `SearchPayloadCodec`, `SearchResponseVerifier` |
| Android in-process stack | Code complete, device untested | `AndroidRelayStack` |
| Karma | Core done | chains, energy, BFS trust, persistence load |
| E2E payload encryption (X25519) | **Not done** (keys present only) | reserved KDF label in constants |
| `ICEBRIDGE.md` operator guide | **Not done** | design doc §15 |

### Stabilization pass (design §12.2)

S1–S8 are **done** in code + tests: path canonicalization, response verification, authenticated discovery, startup ticks, karma load, verifier hardening, honest “direct peer-search” naming for TCP plane.

---

## 2. Code review findings (improved frostwire-code-reviewer)

Severity legend: **BLOCK** / **HIGH** / **MEDIUM** / **LOW** / **INFO**

### BLOCK

| ID | Finding | Evidence | Fix direction |
|----|---------|----------|---------------|
| B1 | **Multi-hop re-sign is cryptographically inconsistent** | `IncomingSearchRequestHandler.forwardRequest` / `RelayRole.forward` call `withNextHop` (zeros sig) then re-sign with **forwarder** key while `requesterPub` stays original. `RelaySearchService.verifySignature` verifies against **requesterPub** → forwarded hops always fail. `DistributedSearchPerformer` still sets **`ttl=1`**, so every search attempts useless/broken multi-hop after local answer. Design S2 recommended v1 no multi-hop. | **Default path: set ttl=0** in performer; disable/no-op IceBridge multi-hop until dual-envelope protocol; add regression test “forwarded request rejected / not sent”. Proper multi-hop is a separate design task. |

### HIGH

| ID | Finding | Evidence | Fix direction |
|----|---------|----------|---------------|
| H1 | **`java.nio.file` in `common/`** | `IdentityKeys`, `IceBridgeTokens`, `IceBridgeHostCache` import `java.nio.file.Files` — forbidden for Android-shared common. | Inject `File`/`InputStream` APIs or move file I/O to desktop/android adapters; keep pure crypto in common. |
| H2 | **Desktop catalog browse unwired** | `Initializer` constructs `IncomingSearchRequestHandler(transport, service, directory, identity)` **without** `LocalIndex`. Android passes index. | Pass `localIndex` on desktop ctor (5th arg) if feature is desired; else document as Android-only. |
| H3 | **Standalone IceBridge still lacks embedded DHT announce** | MentisDB #831 remaining: standalone forwarder not announced on relay/bootstrap topics without SessionManager. Desktop may only find its own prior announce. | Optional minimal jlibtorrent DHT announcer in IceBridge, **or** documented bootstrap host list + host cache seeds. |
| H4 | **Identity settings show “(not initialized)” with no Generate UI** | `IdentitySettingsPaneItem` — restore/import/export exist; no “Initialize Identity” + progress for PoW `generate(20)`. | Background generate, progress bar, save under `libtorrent/identity.dat`, re-wire engine, re-announce/reindex. |
| H5 | **Co-located port conflict still footgun** | Desktop IncomingRelayServer + standalone both default 6888; child rUDP 6889 vs standalone. Settings exist but defaults collide. | Better defaults for local dual-run; detect bind failure and surface in UI/logs; document ICEBRIDGE_RUDP_PORT=6890 pattern. |

### MEDIUM

| ID | Finding | Evidence | Fix direction |
|----|---------|----------|---------------|
| M1 | Rate limit keys differ | Handler rate-limits IceBridge `sourcePub`; service rate-limits `requesterPub`. Spoofable separation. | Prefer single authority: verify sig first, rate-limit on verified requesterPub only. |
| M2 | Host-cache seed uses **unverified** placeholders | `Initializer.startPeerDiscovery` upserts cache entries as placeholders (not queryable for search — OK) but can still drive failed TCP auth spam. | Prefer markSuccess only after verify; or seed only as discovery hints without directory upsert. |
| M3 | Design doc claims `RelayRole.forward` throws UOE | Code still implements multi-hop re-sign. | Update DESIGN §12.2 S2 status + section 8 hop policy to match reality after B1 fix. |
| M4 | No payload E2E crypto | X25519 in identity; `IceBridgeConstants` KDF reserved. Mesh hops can read signed keyword plaintext. | Future: encrypt opaque envelopes for relay path (human + design decision). |
| M5 | Aggressive 30s DHT/discovery | Battery/load on desktop OK; Android doze risk. | Android: longer intervals / WorkManager for advertise (device test). |
| M6 | Event log UI / peer browse | Design build steps 15–17 incomplete. | Feature work after transport hardening. |

### LOW / INFO

| ID | Finding |
|----|---------|
| L1 | `IceBridgeServer.main` uses `System.out` — acceptable for headless CLI UX; keep library paths on `Logger`. |
| L2 | Strong test suite (~40+ relay test classes): MultiInstanceRelay, DistributedSearchPerformer, PeerDiscovery, rUDP-related, ControlServer, launcher. |
| L3 | Self-skip, listener-before-send, fragmentation, verified-only search, OkHttp client, process IO redirect — **done well**. |
| L4 | Changelog discipline (#817) still required on every batch. |

### Verification snapshot (this session)

- Skill updated: `skills/frostwire-code-reviewer/SKILL.md` (+ IceBridge section, top-20 list, MentisDB rules).
- Full `./gradlew test` **not** re-run in this session (plan-only). Sub-agents must verify before claiming green.

---

## 3. Plan: Autonomous vs human-held

### A. Autonomous (orchestrator + sub-agents, no human needed)

Sub-agents **must**:

1. Use MentisDB chain **`frostwire`**, agent_id **`gubatron`** (existing identity — never create new agents).
2. Bootstrap: `list_chains` → `bootstrap` → `list_agents` → `recent_context` → search-before-write.
3. Follow `frostwire-engineer` + improved `frostwire-code-reviewer`.
4. Granular commits only if/when user later asks to commit; **default: no commit**.
5. English only (#827).
6. Evidence before claims: compile + scoped tests.

| Wave | Task ID | Work | Parallel? | Acceptance |
|------|---------|------|-----------|------------|
| **W1** | **A1** | **Kill broken multi-hop (B1)** — `DistributedSearchPerformer.buildSignedRequest` set `ttl=0`; disable or hard-no-op `IncomingSearchRequestHandler.forwardRequest` / document RelayRole.forward as unused for v1; tests prove no forward on ttl=0 and that re-signed forward would fail verify. | Solo first (blocks correctness) | Tests green; no multi-hop side effects |
| **W1** | **A2** | **Wire desktop catalog browse (H2)** — pass `localIndex` into `IncomingSearchRequestHandler` in `Initializer`. | After A1 or parallel if careful | Desktop can answer catalog browse when index non-empty; unit test |
| **W2** | **A3** | **Design doc truth (M3)** — update `DESIGN_RELAY_REGISTRY.md` §12.2 S2, hop policy, open questions to reflect hybrid + ttl=0 v1 + remaining work. | Parallel with A4 | Doc matches code |
| **W2** | **A4** | **Skill/changelog touch** — if code changes user-facing: both changelogs when common/. Reviewer skill already updated. | Parallel | Changelog entries accurate |
| **W2** | **A5** | **Rate-limit consistency (M1)** — rate-limit only after signature verify on requesterPub; remove double-count or sourcePub-only gate where it weakens security. | Parallel with A3 | Tests for rate limit after bad-sig reject |
| **W2** | **A6** | **Host-cache seeding (M2)** — stop upserting unverified cache entries into PeerDirectory (or mark as non-auth spam-limited); keep table for UI. | Parallel | Discovery still works; less auth spam |
| **W3** | **A7** | **common/ Files abstraction (H1)** — replace `java.nio.file.Files` usage in IdentityKeys / tokens / host cache with `File` + streams or platform adapters. | Careful, may touch Android | `common` has no `java.nio.file` imports; desktop+android compile |
| **W3** | **A8** | **Regression pack** — ensure tests for self-skip, listener-before-send, fragment reassembly, ttl=0, ProcessBuilder redirect still pass; add any missing. | After W1–W2 | `./gradlew test --tests 'com.frostwire.search.relay.*'` green |
| **W3** | **A9** | **ICEBRIDGE.md stub** — operator guide from design §15 (ports, local vs remote, tokens, self-discovery pitfalls, EC2). Pure docs. | Parallel anytime | `docs/ICEBRIDGE.md` or repo root |

### B. Human-held (needs you)

| Task ID | Why human | What you do | What agent prepares |
|---------|-----------|-------------|---------------------|
| **H-UI** | UX copy + when to force restart | Approve Identity “Initialize” button flow, seed phrase warnings, restart policy | Implement after approval: progress bar, off-EDT `generate(20)`, path `libtorrent/identity.dat` |
| **H-NET** | Real network / EC2 / home IP | Two-machine or desktop+Android same WiFi mesh smoke; open firewall UDP/TCP | Agent: checklist + log expectations; you run apps |
| **H-AND** | Physical device + battery | Install APK, toggle distributed search, confirm no ANR/doze death | Agent: ensure unit tests; you device-test `AndroidRelayStack` |
| **H-E2E-CRYPTO** | Product/privacy decision | Decide whether mesh must be E2E encrypted before public alpha | Agent: design sketch only after decision |
| **H-BOOTSTRAP** | Trust / ops | Curated public relay list vs pure DHT; which EC2 hosts | Agent can implement host-cache seeds once you provide hosts/tokens |
| **H-COMMIT-PUSH** | Explicit policy | You ask to commit/push | Agents leave clean working tree + message drafts |
| **H-DHT-STANDALONE** | Scope | Whether standalone IceBridge embeds jlibtorrent DHT (binary size, complexity) | Agent can prototype behind flag after yes |

### C. Explicitly deferred (not in autonomous waves)

- Full WoT UI / DistributedSearchLogPanel / PeerBrowseSearchPerformer (design steps 15–17)
- X25519 session encryption
- Public alpha marketing / privacy claims until B1 + E2E crypto decision

---

## 4. Orchestration model

```
                    ┌─────────────────────┐
                    │  Orchestrator (you) │
                    │  Grok + MentisDB    │
                    └──────────┬──────────┘
           W1 ─────────────────┼─────────────────
                               │
                         [A1 ttl=0 / no multi-hop]
                               │
           W2 ─────┬───────────┼───────────┬──────────
                   │           │           │
                  A2          A3+A4       A5+A6
              catalog       docs/skill   rate/cache
                   │           │           │
           W3 ─────┴───────────┼───────────┴──────────
                               │
                     A7 Files + A8 tests + A9 docs
                               │
                    Checkpoint → MentisDB TaskComplete
```

### Sub-agent prompt template (copy for each task)

```
You are agent_id gubatron on MentisDB chain frostwire.
Mandatory: mentisdb_list_chains → bootstrap(frostwire) → list_agents → recent_context
→ ranked_search related lessons before writing code or memory.
Read skills: frostwire-engineer, frostwire-code-reviewer (updated IceBridge section),
DESIGN_RELAY_REGISTRY.md, ICEBRIDGE_REVISION_PLAN.md task <ID>.
Use MentisDB aggressively: LessonLearned on non-obvious fixes; Checkpoint before exit.
Do NOT create new agent IDs. Do NOT commit unless the user explicitly asked.
English only. Verify with compile + scoped tests before claiming done.
Task: <paste task>
```

### Parallelization rules

- **Never** parallelize two agents editing the same file set (Initializer, IncomingSearchRequestHandler, RemoteSearchRequest, PeerDiscovery).
- A1 owns performer + handler forward path first.
- A7 (Files abstraction) after A2 if both touch Initializer/identity paths.
- Docs (A3, A9) fully parallel with code.

---

## 5. Recommended first execution order (when you say “go”)

1. **A1** (BLOCK multi-hop) — highest correctness ROI, small diff.  
2. Parallel **A2 + A5 + A6**.  
3. Parallel **A3 + A9** docs.  
4. **A7** Files cleanup.  
5. **A8** full relay test suite.  
6. Present H-UI mock + H-NET smoke checklist for you.

---

## 6. Success criteria (revision “done enough” for next product step)

- [ ] No multi-hop with inconsistent signatures (ttl=0 or dual-envelope).
- [ ] Verified-only peers queried; self-skip remains.
- [ ] Desktop + Android compile; relay unit tests green.
- [ ] common/ free of forbidden JDK APIs used at runtime on Android.
- [ ] Design doc + ICEBRIDGE.md match hybrid reality.
- [ ] MentisDB has TaskComplete + lessons for this revision wave.
- [ ] Human path clear for identity UX, device, and crypto policy.

---

*This plan is the handoff artifact. Implementation starts only when you approve autonomous waves (or “go A1”).*

---

## 7. Autonomous wave execution log (2026-07-08)

| Task | Status | Notes |
|------|--------|-------|
| A1 ttl=0 + multi-hop off | **Done** | `DistributedSearchPerformer` ttl=0; `MULTI_HOP_FORWARDING_ENABLED=false`; regression tests |
| A2 catalog browse wire | **Done** | Desktop `Initializer` passes `localIndex` |
| A5 rate-limit requesterPub | **Done** | Search: `RelaySearchService` only; catalog: verify then rate-limit requesterPub |
| A6 host-cache seed | **Done** | Removed unverified PeerDirectory upsert |
| A3 design doc | **Done** | §12.2 S2 + §14 dual-envelope |
| A7 Files abstraction | **Done** | IdentityKeys, IceBridgeTokens, IceBridgeHostCache use `java.io` |
| A8 relay tests | **Done** | `./gradlew test --tests 'com.frostwire.search.relay.*'` BUILD SUCCESSFUL |
| A9 ICEBRIDGE.md | **Done** | Repo root stub |
| Human tasks | Pending | H-UI, H-NET, H-AND, H-E2E-CRYPTO, H-BOOTSTRAP, H-COMMIT, H-DHT-STANDALONE |
