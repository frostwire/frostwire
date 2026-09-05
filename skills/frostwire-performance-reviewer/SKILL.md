---
name: frostwire-performance-reviewer
description: Evidence-first performance review for FrostWire Java on HotSpot and Android ART, covering Swing, JNI/libtorrent, SQLite, Netty, OkHttp, and IceBridge. Measure delivered work, tail latency, and bounded resource lifetimes without weakening correctness.
tags:
  - java
  - android
  - desktop
  - performance
  - profiling
  - abseil
triggers:
  - performance review
  - benchmark FrostWire
  - JMH
  - JFR
  - async-profiler
  - IceBridge throughput
  - distributed search latency
---

# FrostWire Performance Reviewer

Evidence before optimization. Use `frostwire-engineer` for implementation contracts and `frostwire-code-reviewer` for correctness/security review. IceBridge is the search/control/metadata plane; libtorrent owns torrent piece data. A completed review is not capacity certification.

## 1. Shared-Brain And Safety Rules

- Use MentisDB chain `frostwire`, existing identity `gubatron`; search related findings, workload evidence, API contracts, and ownership before editing. Recheck historical assumptions against current source and dependencies.
- One owner per file. Publish API units, threading, queue ownership, delivery/cancellation semantics, and wire contracts before changing them. Only the coordinator runs Gradle in a shared tree, including benchmarks, tests, formatting, and builds; workers report requested commands.
- Search before milestone `Summary` checkpoints. Record baseline, changes, evidence, blockers, remaining gates, and refs before handoff/compaction. No commits, pushes, or history changes without explicit permission; preserve unrelated edits.
- Use defensive local regression fixtures and bounded normal-workload measurements. No autonomous offensive workflows, exploit reproduction, or live attacks. Do not invent a load generator aimed at deployed peers.
- Never trade away authentication, replay protection, visibility, delivery, cancellation, or cleanup for throughput. A session is not packet authentication; freshness is not replay deduplication; signed content is not automatically safe or correctly addressed metadata.

## 2. Measurement Workflow

1. Define the user metric: startup/first result, completed search or metadata fetch, p95/p99 latency, frame deadlines, transfer throughput, memory, battery, or thermal behavior.
2. Record revision/artifact, JVM/Android/dependency versions, host/device, build type, topology, roles, catalog size, concurrency, input sizes, network assumptions, warm/cold state, and duration.
3. Estimate operations first: scans, allocations, bytes copied, JNI/Binder calls, locks, database queries, round trips, and wakeups. Label estimates; do not turn suspicious code into a measured claim.
4. Capture repeated baselines with CPU/wall, allocation/GC, lock, I/O, and network evidence appropriate to the hypothesis. Include warmup and steady state; separate startup and recovery.
5. Make one focused change, repeat the identical workload/seeds, and compare delivered throughput, p50/p95/p99/max, errors, allocation rate, GC pauses, and heap/RSS/direct/native memory.
6. Keep only gains that justify complexity and preserve correctness. Report variance/sample size and limitations; a directional result is not a capacity guarantee.

## 3. Platform Evidence

- `common/` must run on both desktop and Android. Check actual dependencies and API availability separately from Java 17 syntax (which includes sealed types). BouncyCastle support requires dependency evidence. `Files` exists on Android API 26, but shared `java.nio.file` remains disallowed by repository policy; no Swing/AWT/JDBC/desktop-only APIs there.
- Desktop: profile packaged builds as well as development launches. Use JMH with warmups/forks and consumed results for isolated Java; JFR/`jcmd`, async-profiler CPU/wall/allocation/lock/native frames, GC logs, and thread dumps for application behavior. Heap dumps have pause/storage costs.
- Android: use physical-device release-like runs, Perfetto/System Trace, Android Studio profilers, `simpleperf`, `dumpsys meminfo`, StrictMode, and ANR traces. Record thermal/battery state, radio/network, cold/warm startup, frame misses, background/resume, and explicit stop.
- JNI, filesystem/DB, network, heavy parsing/ranking, and large allocations stay off EDT/main. `safeInvokeLater` posts TO EDT, not to a worker. Do not silence StrictMode or move blocking work onto UI to make measurements pass.
- Inspect the actual dependency for timeout units and blocking semantics; reviewed jlibtorrent 2.0.12.9 mutable-item waits used seconds. Measure one monotonic absolute deadline including queueing, discovery, serial sends, retries, and reads; inactivity timeouts do not cap total operation time.
- Keep profiler/debug listeners opt-in and local-only; never expose release JMX/JDWP for convenience. Do not put credentials in argv, logs, profiles, or published fixtures.

## 4. Distributed Work Accounting

Measure index queries, encode/sign/verify, control `/send` and `/poll`, rUDP handshake/retry/fragmentation, relay fanout, holder assembly, and UI conversion/rendering separately. Trace one logical operation across layers.

- Count attempted, rejected, accepted, delivered, ACKed, processed, expired, and retried work separately. Successful completed work per second is the useful throughput; dropped accepted work invalidates an apparent gain. HTTP success and transport ACK are not application completion.
- Reconcile accepted work with completion, pending ownership, or explicit terminal outcome. Measure duplicate ACK recovery without redelivery, real multi-fragment completion backpressure, and identity-only consumers over more lifetime messages than queue capacity.
- Identify authoritative queues and optional mirrors. An undrained mirror must not gate the active consumer; per-target caps do not bound target cardinality or global bytes. Never silently evict accepted work or bypass reliable poll lease/ACK semantics.
- Track request/holder streams independently: duplicate/conflicting indices, cumulative rows/bytes, gaps, and contiguous final completion. One holder final must not discard other holders. Cancellation must wake waits, cancel sends, and prevent late UI/download work.
- Bound cardinality/count/bytes/lifetime before allocation, parsing, crypto, or scheduling, including ingress sockets, sessions/aliases, limiters, caches, fragments, queues, retries, and subscriptions. Cap streams while reading, not after `body.bytes()` or equivalent materialization.
- Use cheap global/ingress budgets before crypto and authenticated requester quotas afterward; include aggregated hub traffic and known-peer retries. Optimize verified reuse only within the same authenticated domain/peer/version and replay policy, never by skipping required verification.
- Keep DNS, DB/JNI providers, large signing loops, and synchronous sends off event loops and the sole poller. Bound worker queues, coalesce refreshes, and measure queue age and rejection, not only worker CPU.
- Match TTL/fanout models to actual transmitted layers and receiver behavior. Do not preserve useless TTL-zero forwarding for an old test. Include request/reply paths, roles, overlap, loss, retransmits, and bytes in estimates.

## 5. High-Leverage Performance Lenses

| Lens | Inspect and validate |
|---|---|
| Algorithm | Repeated full scans/sorts/serialization, O(N^2) ingest, duplicate parsing, and query plans before instruction tweaks |
| Storage | FTS cleanup by indexed row identity, transaction batching, distinct-torrent limits before file joins, bounded summary rows, Unicode search correctness |
| Allocation | Profile boxing, temporary strings/arrays/JSON/Base64, pointer-heavy containers, retained buffers; pre-size only from bounded sizes |
| Representation | Prefer compact sequential structures when semantics fit; preserve defensive copies or explicit ownership; no pooling/off-heap without evidence |
| Concurrency | Bounded queues/windows, short locks, DB worker isolation, no cross-component lock over I/O/JNI/callbacks, no new worker each timer tick |
| Runtime | Warmup, JIT/ART differences, deoptimization, GC, native/direct memory; do not assume HotSpot results transfer to ART |
| I/O | Reuse HTTP clients, batch safe boundaries, separate connect/TLS/server/queue latency, pace retries; concurrency cannot hide unlimited retention |
| UI/lifecycle | Async cached native status, bounded ranking and UI batches, generation-checked sort/refresh, cancellation, stop/start resource plateau |

Keep correctness visible while profiling: rowid replacement/deletion must not leave FTS ghosts; visibility applies across search/browse/publication/metadata/cache; metadata's actual requested infohash must be checked independently of signer. Explicit stop/opt-out must end owned participation, not keep a service alive unconditionally. Repeated identity changes must not retain old listeners or stores.

## 6. Workloads And Capacity Claims

- Start with existing tests. Coordinator commands from `desktop/`: `./gradlew test --tests '*IceBridgeStressTest*'` and `./gradlew test --tests '*IceBridgeNetworkSimulationTest*'`. Inspect current workload parameters and assertions before interpreting their names; run one measurement at a time.
- JMH inputs cover empty/small/typical/max cases, warmups, forks, and `Blackhole`/returned results. Include JNI/network/UI only if explicitly measured; do not silently replace production serialization or authentication with cheaper fakes.
- Compare simulations with common random numbers and repeated seeds. Record graph size, holders, topology, TTL/fanout, trials, hit/rare-hit rate, hops, bytes, and queue behavior. Check saturation: full-network coverage cannot distinguish recall tradeoffs. Simulation is not production rUDP execution.
- Measure normal slow consumers/providers, bounded queue saturation and recovery, cancellation, reconnect, and repeated start/stop. Check a steady plateau for heap/RSS/direct/native memory, thread/FD counts, sockets, limiter keys, aliases, listeners, databases, and cache entries. A short stable heap sample does not prove bounded lifetime.
- Report offered load AND completed work, tail latency, error/drop/retry counts, CPU/GC, egress bytes per completed operation, and recovery time. Load below a limiting hop's QPS does not establish behavior above it.
- MentisDB #1048's 762 passing relay tests were review evidence, not EC2 capacity proof; its multi-hop stress path ran about 12 messages/second below a 20-QPS limiter. Reinspect current tests rather than freezing those numbers as a permanent baseline.
- Local/loopback results do not prove WAN, CGNAT, DHT, Android radio/thermal, or EC2 behavior. Mark those gates missing unless separately measured in an explicitly authorized isolated environment. This skill grants no authorization to target live infrastructure.
- Deployment review must preserve root-owned executable/configuration and separate service state; never source service-writable env files. Confirm effective CLI/env limits and artifact identity. Size service memory/task budgets with native/direct headroom; raising caps is not optimization evidence.

## 7. Findings And Release Gate

| Severity | Meaning |
|---|---|
| Critical | Reachable unbounded resource growth, serious UI stall, silent accepted-work loss, lifecycle leak, or release debug exposure |
| Important | Measured/estimated hot-path waste or material tail-latency, startup, battery, or throughput regression |
| Minor | Cold-path cleanup or speculative micro-optimization without meaningful evidence |

Findings first, ordered by severity. Each needs `path:line`, workload, measured evidence or explicit estimate, mechanism, impact, one minimal fix, and exact defensive test/profile command. Then list accepted costs, baseline/change comparison, and missing measurements. No broad rewrite based solely on style.

Before readiness claims, the coordinator records affected desktop/Android compile/tests, changed-Java Spotless, representative profiles, stress accounting, resource plateaus, UI responsiveness, and relevant device/native gates. Track findings as open/fixed/verified/blocked; do not claim all fixed or EC2-ready from green unit tests or a completed review.

## Source Attribution

Adapted from Jeff Dean and Sanjay Ghemawat, [Performance Hints](https://abseil.io/fast/hints.html), Abseil. JVM/ART, Swing, JNI/libtorrent, and IceBridge guidance is FrostWire-specific; review #1048 informs defensive checks, not current capacity claims.
