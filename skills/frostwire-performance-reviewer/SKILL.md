---
name: frostwire-performance-reviewer
description: Evidence-first performance review for FrostWire Java code across common, desktop, and Android. Maps Jeff Dean and Sanjay Ghemawat's Abseil Performance Hints to JVM/HotSpot, Android ART, Swing EDT, JNI/libtorrent, network, database, allocation, and IceBridge distributed-search workloads. Use for profiling, benchmarks, hot-path review, release audits, or performance regressions.
tags:
  - java
  - android
  - desktop
  - performance
  - profiling
  - jmh
  - jfr
  - async-profiler
  - art
  - abseil
triggers:
  - performance review
  - profile Java
  - benchmark FrostWire
  - JMH
  - JFR
  - async-profiler
  - Android performance
  - EDT performance
  - allocation profile
  - GC pause
  - IceBridge throughput
  - distributed search latency
---

# FrostWire Performance Reviewer

Disciplined performance review for FrostWire's `common/`, `desktop/`, and
`android/` Java code. This skill translates the general methods in [Jeff Dean
and Sanjay Ghemawat's Performance Hints](https://abseil.io/fast/hints.html)
to a managed-runtime application with JNI, Swing, Android, SQLite, Netty,
OkHttp, libtorrent, and IceBridge.

The central rule is **evidence before optimization**. A performance finding
must identify the workload, hot path, measurement or estimate, mechanism, and
one concrete fix. Do not turn a style preference into a performance claim.

## 1. Scope And Boundaries

This skill covers single-process and end-to-end application performance:

- CPU time, wall-clock latency, throughput, tail latency, and startup time
- allocation rate, garbage collection, heap/RSS, and cache-friendly layout
- locks, queues, thread pools, JNI crossings, Binder calls, and EDT stalls
- search/indexing, IceBridge transport, torrent metadata, and transfer paths
- desktop distribution startup and Android battery/background constraints

It does not justify adding torrent piece-data relay to IceBridge. IceBridge is
the control and metadata plane; libtorrent owns the BitTorrent data plane.

## 2. Core Principles

### Think About Performance Early

The full Knuth quote applies:

> We should forget about small efficiencies, say about 97% of the time: premature
> optimization is the root of all evil. Yet we should not pass up our
> opportunities in that critical 3%.

Use the faster alternative when it does not materially hurt clarity,
correctness, or maintainability. Library and shared `common/` code deserves
more care because every caller inherits its costs.

Do not optimize cold startup code at the expense of correctness merely because
it looks inefficient. Do optimize code that runs once per search result, mesh
frame, transfer update, database row, or UI frame.

### Estimate Before Coding

For competing designs:

1. Estimate operations: allocations, bytes copied, JNI calls, database queries,
   lock acquisitions, network round trips, wakeups, and full scans.
2. Multiply by rough costs and identify what can overlap under concurrency.
3. Reject alternatives that cannot win before implementing them.
4. Measure the remaining non-obvious tradeoff with a representative workload.

Useful orders of magnitude, not promises:

| Operation | Approximate scale |
|---|---:|
| L1 cache reference | sub-nanosecond to a few ns |
| Main-memory reference | tens of ns |
| uncontended lock | tens of ns, implementation-dependent |
| object allocation | tens of ns to much more under contention/GC |
| JNI or Binder crossing | microseconds to milliseconds |
| SQLite point query | microseconds to milliseconds |
| same-datacenter network RTT | tens to hundreds of microseconds |
| SSD read or filesystem metadata | microseconds to milliseconds |
| internet RTT | milliseconds to hundreds of ms |

Measure the actual device, JVM, network, and storage. Java JIT warmup,
compiler deoptimization, GC, CPU frequency scaling, and Android thermal
throttling make single-run measurements unreliable.

## 3. Module Rules

### `common/`

- Compiles for both desktop and Android; Android/JDK compatibility is the limit.
- Keep APIs narrow and avoid desktop-only or Android-only dependencies.
- Treat JNI calls into libtorrent as potentially blocking unless proven
  otherwise.
- Never move a native call onto an EDT or Android main thread to simplify code.
- Prefer immutable or defensively copied values at protocol boundaries, but
  measure large copies and reuse buffers only where ownership is clear.
- Do not use `java.nio.file`, Swing/AWT, JDBC, or desktop-only APIs in shared
  code.

### `desktop/`

- Never block the Swing EDT with JNI, filesystem, database, network, parsing,
  or large allocation work.
- Treat `TorrentHandle.status()`, `peerInfo()`, tracker calls, and libtorrent
  queries as off-EDT operations.
- Profile the packaged distribution as well as `./gradlew run`; launcher,
  classpath, JIT, and resource layout can change startup behavior.
- Keep JMX/JDWP and profiling listeners opt-in and local-only; never expose
  unauthenticated debug endpoints in a release distribution.

### `android/`

- The main thread must remain responsive: no JNI, disk, SQLite, network,
  Binder, JSON parsing, torrent status refresh, or unbounded list work there.
- Optimize for frame time, battery, memory pressure, background limits, and
  thermal throttling, not only peak throughput.
- Avoid per-frame and per-transfer allocations; watch ART young-generation GC
  and RecyclerView binding churn.
- Keep long-running relay/index work independent of incidental FGS lifecycle
  transitions where platform rules permit; explicit stop/opt-out must end owned
  participation. Do not create redundant service, scheduler, or handler instances.
- Validate on a physical device. Emulator CPU, storage, and network behavior
  are not release evidence for Android performance.

## 4. Measurement Workflow

Follow this order. Do not change code after seeing only a suspicious line.

1. Define the user-visible metric: startup, search completion, first result,
   p95 search latency, metadata fetch time, transfer throughput, frame time,
   memory, battery, or crash-free operation.
2. Define the workload and environment: device/JVM, dataset size, peer count,
   network shape, warm/cold state, concurrency, and duration.
3. Establish a baseline and capture at least several repetitions.
4. Profile CPU and allocations; inspect lock, GC, I/O, and network evidence.
5. Make one focused change.
6. Repeat the same workload and compare median and tail behavior.
7. Keep the change only if the gain is real and complexity/regression risk is
   justified.

Always record:

- commit, Java/Android version, device or host, and build type
- warmup duration and measurement duration
- operation count and input size
- throughput, p50, p95, p99, max, allocation rate, GC pauses, and RSS/heap
- whether the result is statistically meaningful or only directional

### JVM Desktop Tools

Prefer tools in this order:

- JMH for isolated Java microbenchmarks; include warmup, forks, and
  `Blackhole`/returned values to prevent dead-code elimination.
- Java Flight Recorder (`jcmd`, `jfr`) for low-overhead CPU, allocation, lock,
  thread, class-loading, and GC evidence.
- async-profiler for CPU, wall-clock, allocation, lock, and native/JNI flame
  graphs; include native frames when libtorrent or Netty is involved.
- GC logs and heap histograms for memory pressure; use a heap dump only when
  its pause and storage cost are acceptable.
- `jstack`, `jcmd Thread.print`, and thread dumps for EDT or lock stalls.

Example:

```bash
./gradlew run -Pdebug
jcmd <pid> JFR.start name=icebridge settings=profile duration=60s filename=icebridge.jfr
```

Do not infer production behavior from a debug-only JFR profile without noting
JIT, assertions, logging, and classpath differences.

### Android Tools

- Android Studio CPU, Memory, and Energy profilers for interactive diagnosis.
- Perfetto/System Trace for main-thread frames, Binder, scheduling, wakeups,
  and service lifecycle.
- `simpleperf` for native/JNI and CPU sampling on physical devices.
- Android Studio heap dumps and `dumpsys meminfo` for retained objects and RSS.
- Macrobenchmark or a repeatable instrumentation harness for startup/frame
  metrics when the project supports it.
- `adb logcat`, StrictMode, and ANR traces as evidence of thread violations,
  not as substitutes for a profile.

Never silence StrictMode or permit disk/network work on the main thread to make
a benchmark green. Move the work, cache it, or redesign the call boundary.

## 5. Distributed Search And IceBridge Profiling

Distributed search has several different costs. Measure them separately:

1. local index query time and rows returned
2. request encoding/signing and response verification
3. control API `/send`, `/poll`, and JSON/base64 overhead
4. rUDP handshake, retransmission, fragmentation, and queue delay
5. relay hop count, fanout, and duplicate suppression
6. holder metadata lookup and chunk assembly
7. UI result conversion, sorting, and rendering

Use the existing tests before inventing a new load generator:

```bash
cd desktop
./gradlew test --tests '*IceBridgeStressTest*'
./gradlew test --tests '*IceBridgeNetworkSimulationTest*'
```

The stress test supports:

```bash
ICEBRIDGE_STRESS_MESSAGES=50000 \
ICEBRIDGE_STRESS_CLIENTS=64 \
ICEBRIDGE_STRESS_THREADS=64 \
./gradlew test --tests '*IceBridgeStressTest*'
```

For a profile, run one workload at a time, capture JFR/async-profiler data,
and compare against a baseline. Do not run competing Gradle test invocations:
they can race over shared test-result files and invalidate the measurement.

Interpret transport results carefully:

- queue overflow is a delivery/backpressure problem, not a CPU optimization
- retransmissions may indicate authentication/session or NAT behavior, not
  slow code
- p95/p99 matter more than mean latency for interactive search
- a loopback test proves protocol behavior, not WAN, CGNAT, DHT, or Android
  radio behavior
- a high relay throughput number is meaningless if messages are dropped

## 6. Performance Lenses

Review in this order. Higher-leverage structural changes come before micro-opts.

### Lens 1: Algorithmic Complexity

- Find nested scans, repeated full index traversals, accidental O(N^2), and
  repeated sorting where a hash lookup or precomputed order suffices.
- Avoid re-parsing or re-verifying the same search/metadata payload only when
  verified reuse preserves the authenticated domain, peer, version, and replay
  policy; never skip required packet authentication.
- Build indexes and lookup maps in bulk where input is already known.
- Use bounded fanout, TTL, queue, fragment, and peer limits; unbounded work is
  a correctness and availability problem as well as a performance problem.
- Check hot paths for duplicate `containsKey` then `put`; use one lookup or
  `computeIfAbsent` where semantics permit.

### Lens 2: Java Memory Representation

- Prefer primitive fields and arrays in large hot collections; boxing in
  `HashMap<Integer, ...>` or streams can create substantial allocation pressure.
- Use `ArrayList` for compact sequential storage; use `ArrayDeque` for queues.
- Choose `HashMap` for equality lookup and `TreeMap` only when ordering is
  required. Consider a sorted compact list for small read-mostly sets.
- Avoid `LinkedList` in hot paths; each node adds allocation and pointer
  chasing.
- Keep hot fields together conceptually and cold diagnostics out of hot objects.
- Use immutable `byte[]` ownership rules and defensive copies at boundaries;
  do not copy the same protocol payload at every layer.
- Do not use object pools, custom allocators, or off-heap storage without a
  profile proving that GC/allocation is the bottleneck.

### Lens 3: Allocation And GC

- Identify top allocation sites with JFR/async-profiler, not visual inspection.
- Pre-size collections when the size is known and bounded; use `ensureCapacity`
  or a constructor capacity rather than repeated growth.
- Avoid temporary `String`, `byte[]`, Base64, JSON, regex, and stream objects
  inside per-frame/per-result loops.
- Reuse buffers only with explicit ownership and no cross-thread aliasing.
- Prefer `StringBuilder` for a single constructed string; do not use it as a
  shared mutable global.
- Avoid `Optional`, lambdas, streams, and boxing in proven ultra-hot loops only
  when measurement shows a meaningful cost; readability wins elsewhere.
- A lower allocation count is not automatically faster if it increases lock
  contention or retains large buffers.

### Lens 4: Avoid Unnecessary Work

- Fast-path empty, invalid, duplicate, cached, and already-authenticated cases
  only within their validated scope; a session is not packet authentication,
  and freshness is not replay deduplication.
- Cache immutable metadata, parsed identities, compiled patterns, and expensive
  fingerprints at the correct lifecycle scope.
- Do not refresh UI rows when state/content did not change.
- Do not call `TorrentHandle.status()` repeatedly from render code; refresh
  asynchronously and render a bounded, documented cache.
- Do not wake Android handlers, pollers, or services when no work is pending.
- Keep INFO logging, string formatting, and stack traces off high-volume wire
  paths unless explicitly enabled or sampled.

### Lens 5: JIT And Code Shape

- Trust HotSpot/ART before adding manual inlining, `final` tricks, or unsafe
  code; verify with a profile.
- Keep hot methods small and stable enough to inline, but isolate rare error
  and diagnostic paths.
- Avoid megamorphic call sites in proven hot loops when a simple concrete path
  is available without breaking the API.
- Do not benchmark a cold JVM and call it steady state; include warmup and
  check for deoptimization or class-loading effects.
- Do not assume desktop HotSpot behavior applies to Android ART.

### Lens 6: Concurrency And Synchronization

- Keep locks short; never hold a cross-component lock over network, disk, JNI,
  or callbacks. Necessary database transactions/connection serialization belong
  on an owned DB worker; bound their scope without breaking atomicity.
- Prefer bounded queues and explicit backpressure to unbounded executor work.
- Batch queue drains and database/index updates when semantics allow.
- Use atomics for simple counters/flags; use locks for compound invariants.
- Avoid one global lock for peer registries, transfer state, and UI state.
- Bound thread pools and scheduled tasks; every thread has stack, scheduling,
  and wakeup cost.
- On Android, account for process lifecycle, Doze, FGS limits, and radio wakeups.
- Verify that cancellation, retries, and backpressure preserve delivery and do
  not merely improve a throughput number by dropping work.

### Lens 7: Serialization And JNI

- Measure JSON/base64 and bencode separately from network time.
- Avoid encode/decode cycles when a typed object can cross an internal boundary.
- Batch protocol work where it does not enlarge failure or memory scope.
- Keep wire-size limits explicit; fragmentation and reassembly have CPU and
  memory costs.
- Treat every JNI crossing as a boundary worth batching, but never batch in a
  way that blocks the EDT/main thread or retains unbounded native objects.
- Prefer `byte[]`/direct buffers only when the consumer and ownership model make
  copies measurably avoidable.

### Lens 8: I/O And Network

- A network round trip or disk seek usually dominates a few Java instructions.
- Avoid serial network calls when independent calls can safely overlap.
- Reuse HTTP connections, but bound idle resources and respect cancellation.
- Separate connect, TLS, server, queue, and application latency in metrics.
- Never increase concurrency merely to hide latency without measuring server,
  memory, rate-limit, and tail effects.
- For IceBridge, distinguish accepted/queued, delivered, acknowledged, and
  application-processed. They are not the same metric.

## 7. Platform-Specific Smells

### Android Red Flags

- `th.status()`, `peerInfo()`, tracker/JNI calls in `onBindViewHolder`, render,
  `onCreate`, or any main-thread callback
- `File.exists`, SQLite, DataStore, or large directory scans on main thread
- per-row `new Handler`, repeated `notifyDataSetChanged`, or unbounded adapter
  rebuilds
- creating executors/threads for each transfer or search
- retaining `Activity`, `View`, or service contexts from long-lived workers
- retry loops that wake the device or radio without a bound
- `StrictMode.allowThreadDiskReads/Writes` used to conceal application work

### Desktop Red Flags

- `TorrentHandle.status`, JNI, SQLite/JDBC, filesystem, or HTTP from Swing EDT
- sorting or rendering the entire transfer/index set for every repaint
- JMX/JDWP or profiler ports enabled in release launchers
- unmanaged child processes, duplicate IceBridge daemons, or infinite retry
  loops that create orphan work
- unbounded test forks or verbose test logging that cause false performance and
  memory failures

### Common Red Flags

- a shared queue that silently evicts data while reporting success
- ACKing before ownership acceptance at the documented transport layer, or
  treating a transport ACK as durable/application completion
- repeated full scans caused by missing indexes or stale-cache policy
- logging full payloads, URLs, identities, or stack traces on hot paths
- "optimization" that weakens authentication, delivery guarantees, or cleanup

## 8. Benchmark Design

Use JMH for Java microbenchmarks. A valid benchmark should have:

- representative inputs, including empty, small, typical, maximum, and malformed
  values where relevant
- warmup iterations and multiple measurement forks
- consumed results or `Blackhole` to prevent elimination
- no network, filesystem, Android UI, or JNI unless the benchmark explicitly
  measures that boundary
- separate throughput and latency modes
- allocation measurement when allocation is part of the hypothesis

For distributed-search simulations:

- use the same random seed for compared configurations
- report graph size, peers, holders, fanout, TTL, queue limits, and trials
- report hit rate, rare-hit rate, mean/p95/p99 messages, hops, and bytes
- distinguish simulated from real rUDP/Netty behavior

For Android:

- test release-like builds where possible
- repeat on physical devices and record battery/thermal state
- measure cold start separately from warm start
- measure frame deadline misses, not just average CPU

Never accept a benchmark that passes by dropping requests, disabling
authentication, bypassing the UI, or replacing production serialization with a
different format.

## 9. Review Severity

### Critical

- pathological complexity or unbounded memory on a production hot path
- blocking EDT/main-thread work capable of ANR/freeze
- queue/relay behavior that silently drops accepted work
- cross-component lock held over network, disk, JNI, or a callback; not necessary
  database transaction/connection serialization isolated on an owned DB worker
- a release artifact exposing debug/profiling listeners

### Important

- measured hot-path allocation, lock, serialization, JNI, or I/O waste
- missing batching or cache causing repeated expensive boundary crossings
- p95/p99 regression or throughput loss under representative load
- startup/background behavior that materially drains battery or delays service

### Minor

- cold-path allocation or formatting cleanup
- speculative container/inlining changes without a profile
- test-only speedups that do not change production behavior

Every Important or Critical finding must include measurement evidence or a
back-of-the-envelope estimate and a validation command. A static suspicion is
not enough.

## 10. Review Output

Use this format:

```text
## Performance review: <scope>

### Summary
- Workload/environment:
- Profile/benchmark evidence:
- Hot paths:
- Top opportunities:

### Findings
#### [Critical|Important|Minor] <title>
- Where: path:line
- Evidence: measured result or estimate
- Mechanism: algorithm/allocation/GC/lock/I/O/JNI/EDT/etc.
- Impact: expected user/system effect
- Fix: one minimal concrete change
- Validate: exact benchmark/profile/test command

### Accepted costs / non-findings
- ...

### Follow-up benchmark
- ...
```

Findings come first and are ordered by severity. Do not recommend a broad
rewrite when one measured boundary or data structure is responsible.

## 11. Release Gate

Before claiming performance readiness:

- affected desktop and Android compile/test gates are green
- `spotlessCheck` is green for changed Java files
- IceBridge stress and network simulation results are recorded, not merely
  asserted
- no EDT/main-thread JNI or I/O findings remain unexplained
- release launchers contain no unauthenticated JMX/JDWP/debug listeners
- heap/GC behavior is acceptable under representative transfer/search load
- Android physical-device checks cover startup, background/resume, and transfer
  list rendering
- any known limitation is user-visible and documented, not hidden by timeout
  or silent fallback

Do not call the code "world class" from a green unit suite alone. Performance
confidence requires a reproducible workload, a profile, and a stated residual
risk list.

## 12. Shared-Brain And Distributed Resource Checks

These additional checks retain lessons from MentisDB review #1048 and later
shared-brain work. They supplement the lenses and release gate above; historical
findings and passing test counts are not claims about the current source.

### Review Coordination And Safety

- Use `frostwire-engineer` for implementation contracts and
  `frostwire-code-reviewer` for correctness/security review.
- Use MentisDB chain `frostwire`, existing identity `gubatron`; search related
  findings, workload evidence, API contracts, and ownership before editing.
  Recheck historical assumptions against current source and dependencies.
- One owner per file. Publish API units, threading, queue ownership,
  delivery/cancellation semantics, and wire contracts before changing them.
  Only the coordinator runs Gradle in a shared tree, including benchmarks,
  tests, formatting, and builds; workers report requested commands.
- Search before milestone `Summary` checkpoints. Record baseline, changes,
  evidence, blockers, remaining gates, and refs before handoff/compaction.
  No commits, pushes, or history changes without explicit permission; preserve
  unrelated edits.
- Use defensive local regression fixtures and bounded normal-workload
  measurements. No autonomous offensive workflows, exploit reproduction, or
  live attacks. Do not invent a load generator aimed at deployed peers.
- Never trade away authentication, replay protection, visibility, delivery,
  cancellation, or cleanup for throughput. Do not put credentials in argv,
  logs, profiles, or published fixtures.
- Check actual dependencies and API availability separately from Java 17
  syntax (which includes sealed types). BouncyCastle support requires dependency
  evidence. `Files` exists on Android API 26, but shared `java.nio.file` remains
  disallowed by repository policy. `safeInvokeLater` posts TO EDT, not a worker.

### Admission, Sender Work, And Receiver Backpressure

- Bound count, bytes, lifetime, and cardinality before allocation, parsing,
  crypto, or scheduling. Include ingress sockets, sessions/aliases, limiter
  keys, caches, fragments, queues, retries, and subscriptions. Per-target caps
  do not bound target cardinality or global bytes. Cap streams while reading,
  not after `body.bytes()` or equivalent materialization.
- Bound sender pending/held bytes, in-flight windows, retry work, and signing
  work as well as receiver queues and reassembly. Include aggregated hub traffic
  and known-peer retries. Use cheap global/ingress budgets before crypto and
  authenticated requester quotas afterward; one-sided caps are not backpressure.
- Count attempted, rejected, accepted, delivered, ACKed, processed, expired,
  and retried work separately. Reconcile accepted work with completion, pending
  ownership, or an explicit terminal outcome. HTTP success and transport ACK
  are not application completion; dropped accepted work invalidates a gain.
- Identify authoritative queues and optional mirrors. An undrained mirror must
  not gate the active consumer. Measure identity-only consumers over more
  lifetime messages than queue capacity. Never silently evict accepted work;
  verify actual poll ownership-transfer semantics and report missing lease/ACK
  reliability rather than assuming it exists or bypassing it.
- Verify duplicate ACK recovery without redelivery and real multi-fragment
  completion backpressure. A one-fragment fixture does not exercise retention
  of an already accepted prefix while final delivery waits for capacity.
- Keep DNS, DB/JNI providers, large signing loops, and synchronous sends off
  event loops and the sole poller. Bound worker queues, coalesce refreshes, and
  measure queue age and rejection, not only worker CPU. Do not create a new
  worker on every timer tick.
- Match TTL/fanout models to actual transmitted layers and receiver behavior.
  Do not preserve useless TTL-zero forwarding for an old test. Include request
  and reply paths, roles, overlap, loss, retransmits, and bytes in estimates.

### Deadlines, Completion, And Data Correctness

- Inspect the actual dependency for timeout units and blocking semantics;
  reviewed jlibtorrent 2.0.12.9 mutable-item waits used seconds. Use one monotonic
  absolute deadline including queueing, discovery, serial sends, retries, and
  reads. Inactivity timeouts do not cap total operation time.
- Track request/holder streams independently: duplicate/conflicting indices,
  cumulative rows/bytes, gaps, and contiguous final completion. One holder final
  must not discard other holders. Cancellation must wake waits, cancel sends,
  and prevent late UI/download work. Native/provider work that ignores
  interruption can still occupy bounded workers after the caller deadline;
  measure and report that limitation rather than claiming full cancellation.
- Signed metadata is not automatically correctly addressed metadata. Check the
  actual requested torrent infohash independently of the signer and payload
  digest, including the relevant v1/v2/hybrid identity.
- Keep visibility consistent across search, browse, publication, metadata, and
  cache paths. Cache reuse must not bypass current authorization.
- Check FTS cleanup by indexed row identity: rowid replacement/deletion must
  not leave ghosts. Batch transactions, limit distinct torrents before file
  joins, bound summary rows, and preserve Unicode search correctness.
- Bound ranking and UI batches; generation-check asynchronous sorting and
  refreshes. Explicit stop/opt-out must not be undone by incidental service or
  network recovery. Repeated identity changes must release old listeners and
  stores, not just replace their references.

### Capacity Evidence And Deployment

- Record artifact identity, dependency versions, topology, roles, catalog size,
  network assumptions, sample size, and variance alongside the baseline metrics.
  Separate startup, recovery, and steady state; compare identical workloads.
- Inspect current stress parameters and assertions before interpreting test
  names or the 50,000-message workload above. Compare simulations with common
  random numbers and repeated seeds. Check saturation: full-network coverage
  cannot distinguish recall tradeoffs. Simulation is not production rUDP.
- Measure normal slow consumers/providers, bounded queue saturation and
  recovery, cancellation, reconnect, and repeated start/stop. Check a steady
  plateau for heap/RSS/direct/native memory, thread/FD counts, sockets, limiter
  keys, aliases, listeners, databases, and cache entries. A short stable heap
  sample does not prove bounded lifetime.
- Report offered load AND completed work, tail latency, error/drop/retry
  counts, CPU/GC, egress bytes per completed operation, and recovery time.
  Load below a limiting hop's QPS does not establish behavior above it.
- MentisDB #1048's 762 passing relay tests were review evidence, not EC2
  capacity proof; its multi-hop stress path ran about 12 messages/second below
  a 20-QPS limiter. Reinspect current tests rather than freezing those numbers
  as a permanent baseline.
- Local/loopback results do not prove WAN, CGNAT, DHT, Android radio/thermal,
  or EC2 behavior. Mark those gates missing unless separately measured in an
  explicitly authorized isolated environment. This skill grants no authorization
  to target live infrastructure.
- Preserve root-owned executable/configuration and separate service-writable
  state; never source service-writable env files in an elevated installer.
  Confirm effective CLI/env limits and artifact identity. Size service memory
  and task budgets with native/direct headroom; raising caps is not optimization
  evidence.
- Track findings as open/fixed/verified/blocked and record baseline/change
  comparisons and missing measurements. The coordinator records stress
  accounting, resource plateaus, UI responsiveness, and relevant device/native
  gates alongside the full release gate above. A completed review or isolated
  test run is not full-source integration verification or capacity certification.

## Source Attribution

Principles and review structure are adapted from:

> Jeff Dean and Sanjay Ghemawat, Performance Hints, Abseil, 2025,
> https://abseil.io/fast/hints.html

Java, HotSpot, ART, Android, Swing, JNI, libtorrent, and IceBridge mappings are
FrostWire-specific guidance.
