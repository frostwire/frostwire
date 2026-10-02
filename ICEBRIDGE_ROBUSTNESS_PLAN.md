# Distributed search: failure history and robustness plan

Working-tree review, 2026-10-02. This is an engineering backlog, not a claim
that every proposed gate or diagnostic is implemented.

## What keeps going wrong

The recurring failures are mostly contracts broken **between** components.
Discovery, routing, indexing, signatures, metadata and BitTorrent can each pass
their own tests while the user still sees no result or no download. Increasing
fan-out or waiting longer can conceal that mismatch without fixing it.

| Evidence in history | Broken contract | Durable response |
| --- | --- | --- |
| `686bf8bc4`: rotate registry lookup; `c4db1381d`: carry real capabilities; `9f934673e`: include relay uplink | A populated directory did not mean a useful, reachable search route | Validate production role selection and registry convergence, including leaf holders |
| `903346af3`: admit announcements; `da98214a9`: holder routing; `1fb635650`: refresh digests | An indexed file did not imply that the relay learned its keywords | Test committed producer mutations through digest delivery and holder selection |
| `35e0ac932`: establish routes before first sync; `c9005546c`: immediate registration | Process health/startup was mistaken for application readiness | Observe actual socket binding, authenticated registration and first useful exchange |
| `d0bd09a21`: replace stale sessions; `e7608b113`: automatic client UDP binds | Identity was confused with an endpoint, or two identities claimed one NAT endpoint | Advertise actual owned ports; retain authenticated identity/path checks |
| `37c3a76f6`: page catalogs | A legal application response exceeded its enclosing relay datagram | Account for complete authenticated framing; test maximum encoded pages over multiple hops |
| `2ad066ad4`: catalog consent on mesh and DHT | Policy at one publisher did not cover every publication surface | Treat consent, seed state, privacy and withdrawal as end-to-end invariants |
| `6c9fa598e`: packaged metrics class | The development classpath differed from the installed artifact | Test real packaged artifacts with isolated class loaders |
| `4342464e9`: finish HTTP postprocessing before seeding | Download completion preceded the final bytes being published | Publish completion only after final-file ownership and postprocessing finish |
| Current hybrid-index regression | CREATED used v1 while handle callbacks published truncated v2 as another identity | Derive public identity from native metadata; remove only metadata-proven aliases after canonical commit |
| Current catalog-tap regression | A failed metadata/start operation removed its placeholder like a success | Retain observable ERROR; verify holder attribution, hash validation, actual start and byte progress |

The current focused regressions establish the last two contracts locally.
The installed phone/desktop journey below also exercises cloud-relayed metadata
and a completed same-LAN file transfer. External-network piece delivery remains
a separate requirement.
The observed multi-minute delay cannot be wholly attributed to one cache interval
without commit/send/receive/selection timestamps.

### Verification snapshot

- Desktop focused regression run: 40 tests passed. Final full isolated run:
  1,404 tests, zero failures, two skips; formatting, simulation isolation and
  `installDist` passed. The earlier two failures in unchanged live YouTube tests
  were transient remote bot/sign-in rejections, not bypassed or excluded.
- Android: all 220 unit tests passed, including locale parity, catalog row
  listeners, metadata rejection and real signed outbound request verification;
  compilation, formatting and `assemblePlus1Debug` succeeded.
- Restarted development desktop: MCP search for `general` returned exactly one
  canonical v1 row (`d6ce91299d15e8dac37591868de2d1fefabd3e16`); digest
  diagnostics reported an announcement to its available peer.
- API 36 emulator: APK installed and UI launched, but search interaction hit
  an ANR. Captured main-thread stack waited in `HardwareRenderer.syncAndDrawFrame`;
  this does not establish an application-I/O bug or a successful download.
  Android 12 ARM retry installed the APK but launch/UI commands stalled.
- Physical USB phone follow-up: installed the verified APK through the launcher,
  searched `Scarlett`, opened the desktop holder's catalog, and tapped its row.
  Logs show mesh metadata receipt, native download addition and completion;
  the transfer UI subsequently showed Seeding. Desktop upload bytes rose from
  1,488 to 4,141,785. The downloaded 4,132,412-byte file's SHA-256 matched the
  desktop file: `4cf49e65ef80f5cff0afe33eb94f3c052c6ccfd0f25d92a1b88bc5e2d5b88cb4`.
  No direct peer endpoint was manually injected.
- Created and seeded a harmless, unique 78-byte `freshnessoct2-verification.txt`
  fixture on the running desktop. Local search returned one canonical v1 row;
  phone Distributed search found the same filename. Removed the fixture transfer
  after verification. This establishes live discovery, not a measured freshness
  latency bound; the controlled producer-to-holder test supplies that evidence.
- Re-ran the unchanged Telluride suite after browser access was confirmed:
  all four tests passed, including actual metadata extraction and download,
  without credentials or test changes. The final full-suite run also passed.

## Required invariants

1. **Content identity:** index row, signed response, magnet, selected hash,
   metadata verification and transfer lookup refer to the same torrent. For
   hybrid metadata the current public identity is v1; the legacy 20-byte v2
   prefix is an alias. Full v2 support needs an explicit versioned contract.
2. **Authenticated routing:** an address locates a candidate peer; it does not
   prove key possession. NAT collisions and migration never justify weakening
   the handshake or signature checks.
3. **Freshness:** a committed mutation schedules bounded, coalesced propagation.
   A mutation during snapshot/send requests a follow-up. Empty state withdraws
   previous holdership. Periodic repair supplements this mechanism.
4. **Lifetime ownership:** stale generations cannot publish, detach a replacement,
   access disposed stores, start canceled transfers or revive a stopped child.
5. **Delivery:** queue admission, HTTP success, authenticated receipt, validated
   response, metadata acceptance, native transfer start and piece progress are
   distinct milestones. UI success corresponds to the appropriate milestone.
6. **Bounded work:** account for wire overhead, aggregate bytes, queues, workers,
   per-peer quotas and global budgets. Replies retain a serviceable lane under
   request saturation. One absolute deadline covers every stage of an operation.
7. **Sharing policy:** ordinary search, metadata and public catalogs have explicit
   policy boundaries. Opt-out invalidates caches and both publication surfaces.
8. **Artifact parity:** source/unit evidence identifies the tested revision;
   deployed evidence identifies actual desktop, APK and relay artifacts.

## Ordered implementation backlog

### P1 — Make boundary failures diagnosable with one operation trace

- Carry the existing request nonce through query selection, route establishment,
  transport admission/receipt, verification, holder response and UI delivery.
- Add stage/reason counters and bounded diagnostic events for failures. Record
  content identity kind, stack generation and elapsed monotonic time; never log
  credentials, private keys or private catalog contents.
- For freshness, correlate index commit, snapshot build, successful announcement,
  relay receipt and holder selection. Separate coalescing latency from transport
  retry and downstream cache latency.
- Acceptance: a failed query/download can be localized to one boundary without
  enabling unrestricted logging or reproducing it repeatedly.

### P1 — Test state transitions across production boundaries

- Keep real SQLite/FTS, native hybrid metadata, signing, serializers and routing
  in relevant integration tests; replace only slow/unavailable external seams.
- Exercise startup, seed creation, callback reindex, opt-out, pause, removal,
  identity replacement, child death, stale replies and cancellation in varied
  orders. Assert safety **and** eventual progress within the documented budget.
- Inject queue rejection, blocked send, relay restart, duplicate/reordered chunks,
  transient metadata loss and failed canonical writes/deletes.
- Prove each regression fails with the defective behavior restored. Source-text
  checks may supplement behavior but cannot establish delivery or download success.

### P2 — Consolidate identity and mutation contracts

- Introduce an explicit content identity value only when migrating all affected
  producers, persistence, wire readers and consumers together. Avoid scattered
  hash-length inference and undocumented changes to legacy wrapper getters.
- Make mutation notification a shared index/service contract if further producers
  appear; inventory every successful upsert/delete and its lifecycle owner first.
- Preserve a conservative metadata-gap policy and retry failed alias cleanup;
  absence of metadata is not proof that a previously indexed live share vanished.
- Acceptance: all producers converge on one metadata-authoritative hybrid row,
  and every committed mutation reaches the routing summary without manual refresh.

### P2 — Gate releases on the installed user journey

- Validate desktop packaged startup and Android compilation/resource parity.
- With exact artifact revisions recorded, seed a unique file, search from a second
  production client, browse its holder, tap a row, validate metadata, observe native
  transfer start and actual piece-byte progress. Include two clients behind one NAT.
- Add a remote-holder, multi-forwarder case and mixed-version clean rejection.
- Keep fixture home/temp isolation reproducible; do not exclude assertions or
  relax timeouts/security to make release tests green.
- Report separately what was proven by unit/integration tests, emulator UI,
  same-LAN transfers and external-network transfers.

## UI responsiveness

See [desktop/EDT_STALL_TODO.md](desktop/EDT_STALL_TODO.md) for the traced,
prioritized Swing backlog and acceptance criteria. Static blocking paths are
not measured freeze durations. Keep the strict watchdog active; capture a
runtime stack before choosing a fix. On emulators, distinguish application work
from Android renderer waits using the ANR thread dump.

## Exit criteria

A regression is closed when the defective behavior fails its reproducer, the
corrected path passes relevant shared/platform suites, and the promised installed
journey has its own evidence. Remaining NAT reachability, full-v2 wire identity,
deployment or UI-stall limitations stay named and testable rather than hidden
behind a general “distributed search works” claim.
