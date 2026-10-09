# IceBridge Operator Guide (stub)

> Status: stub from 2026-07 revision. Expand as ops experience grows.  
> Architecture source of truth: `DESIGN_RELAY_REGISTRY.md`.  
> Desktop plan / review: `desktop/ICEBRIDGE_REVISION_PLAN.md`.

## What IceBridge is

**IceBridge is an independent relay network** — not a FrostWire-only feature flag.

| IceBridge (network layer) | FrostWire Distributed Search (app layer) |
|---------------------------|------------------------------------------|
| Node identity, mesh rUDP, NAT/hole-punch, roles | Signed search request/response schemas |
| Control API (`/send`, `/poll`, …) | LocalIndex, karma, Search UI engines |
| DHT announce so **pure forwarders are discoverable** | Happens to *use* IceBridge as transport |
| Protocol-agnostic opaque payloads (IBP1 `protocolId`) | One of possibly many app protocols (SEARCH=1) |
| Multi-hop RELAY mesh (hop TTL between FORWARDER nodes) | Dual-envelope signed search requests (app layer) |

FrostWire desktop/Android are **clients and optional co-located nodes** of that network. A cloud `FORWARDER` must be fully useful **without** any FrostWire GUI process.

**DHT announce (MUST):** standalone `./gradlew icebridge` with `ICEBRIDGE_DHT=true` (default for FORWARDER/BOTH via env) embeds a minimal jlibtorrent session and runs `DhtAdvertiser` so pure cloud forwarders appear on `frostwire-relays-v1` (+ optional bootstrap topic). In-process desktop/Android IceBridge leaves DHT to BTEngine (`dhtEnabled` defaults false on builders).

### Hybrid planes

| Plane | Port (defaults) | Purpose |
|-------|-----------------|---------|
| Identity / bootstrap | none: the rUDP handshake below proves the Ed25519 key; the signed `IdentityRecord` (BEP 46) says role/capabilities | No TCP identity port any more |
| Mesh data | UDP **6889** (`ICEBRIDGE_RUDP_PORT`) | Authenticated rUDP + fragmentation |
| Control API | TCP **8081** bound to **127.0.0.1 only** | `/health`, `/register`, `/route`, `/send`, `/poll`, `/metrics` (SSH tunnel for remote ops) |

Desktop runs a **child** IceBridge process (or attaches to a **remote** standalone). Peer discovery asks the daemon to probe candidates over rUDP (`POST /probe`).

### Content-aware search routing (app layer, `protocolId=8` `INDEX_DIGEST`)

Blind fan-out to `M` random peers does not scale: with a large directory a single holder is
reached only by luck. Instead:

- Each node with a non-empty `LocalIndex` announces a bounded keyword **Bloom filter**
  (`IndexDigest`, `protocolId=8`) to its directory peers via `PeerRegistrySync`. No false
  negatives for indexed tokens, so a real holder is never missed; a false positive only costs one
  extra peer queried.
- Forwarders (`IncomingSearchRequestHandler.forwardRequest`) and requesters
  (`DistributedSearchPerformer.selectPeers`) select **digest matches first**, then fill the
  remaining budget with live peers for exploration. This keeps fan-out bounded while reliably
  reaching holders.
- Tokens are Unicode-normalized (diacritics stripped, lower-cased), so `Inglés` matches `ingles`.

### How a node finds other nodes (discovery)

`PeerDiscovery` runs a pass every 30s. Candidates come from three sources, all of which must pass the
rUDP identity-discovery handshake (`POST /probe`, an identity-free HELLO that proves the endpoint holds its Ed25519 key) before a peer is registered. A candidate is an `ip:udpPort`; the DHT announces the **rUDP** port:

| Source | Candidates | Notes |
|--------|------------|-------|
| Host cache + built-in seeds (`HostCachePeerDiscoverySource`) | every cached server, plus `RelayConstants.seedHosts()` (`virginia1.frostwire.com:6889`; override with `-Dfrostwire.icebridge.seeds=host:port,...`, empty disables) | **preferred**; retried within seconds |
| LAN multicast beacon (`LanPeerBeacon`, UDP 6890, group 239.255.70.88; on Android `AndroidLanBeacon` holds a wifi `MulticastLock` and only runs with the embedded node) | private IPv4 senders and their rUDP port | **preferred**; needed because the DHT only knows our public address and routers refuse to connect a machine to its own public address |
| DHT (`DhtPeerDiscoverySource`) | `frostwire-bootstrap-v1` (preferred), then `frostwire-relays-v1`, then `frostwire-peers-v1` | the relay/peer topics hold every desktop and phone, mostly unreachable |

A pass probes preferred candidates first, the rest shuffled, up to 64, **in parallel** (8 at a time, 3s
connect cap, 15s pass budget). Endpoints that fail back off (30s doubling to 10 min; preferred ones 5s
doubling to 1 min) so successive passes cover the whole list instead of re-probing the same dead ones.
Only the parent process announces on the DHT; the desktop `icebridge.jar` child runs with `--no-dht`.

### Peer liveness and pruning

`PeerDirectory` tracks last contact and consecutive delivery failures per peer
(`CONTACT_TTL_MS`, `AFFIRM_TTL_MS`, `MAX_FAILURES`):

- Any authenticated inbound frame or verified response calls `markContact` and clears the streak.
- Failed sends/forwards call `markFailure`; a peer is dropped at `MAX_FAILURES`.
- `evictUnreachable` (run from the transport maintenance timer) prunes peers that are silent past
  the contact TTL, or never heard from and no longer affirmed by discovery.
- `PeerRegistrySync` ignores stale `/lookup` rows (`MAX_LOOKUP_AGE_MS`) so dead routes are not
  re-imported.

Net effect: searches are never routed to unreachable nodes, and unreachable nodes are removed from
the local peer list instead of being retried forever.


## Roles

- `CLIENT` — join mesh, do not advertise as forwarder (unless auto-elected when connectable).
- `FORWARDER` — help NAT’d peers; announce under relay DHT topic when DHT is available.
- `BOTH` — typical desktop child default.

## Desktop usage

Settings → IceBridge / Search engines:

- Enable IceBridge + Distributed search.
- **Local child**: launches `build/libs/icebridge.jar` (dev) or `icebridge.jar` under user settings (prod).
- **Remote**: set remote URL + bearer token (no local subprocess).

Ports are configurable. **Do not** run two processes binding the same UDP 6889 without changing one side.

Logs print a structured config dump at startup (`[set]` for tokens, never the secret).

Identity file (shared with desktop relay stack):

```text
~/.frostwire/libtorrent/identity.dat
```

## Standalone / cloud relay

```bash
cd desktop
cp .env.example .env   # optional
./scripts/icebridge-run-local.sh              # FORWARDER + DHT, java -jar
./scripts/icebridge-run-local.sh --colo       # ports 7000/7001 next to desktop
./scripts/icebridge-run-local.sh --background
# or: ./gradlew icebridge
```

Useful env vars:

| Variable | Meaning |
|----------|---------|
| `ICEBRIDGE_HOST` | Bind host (cloud: `0.0.0.0`) |
| `ICEBRIDGE_RUDP_PORT` | UDP mesh |
| `ICEBRIDGE_RELAY_PORT` | ignored by peer verification; non-zero selects standalone forwarder mode |
| `ICEBRIDGE_CONTROL_HTTP_PORT` | Control HTTP (default **8081**, **127.0.0.1 only**) |
| `ICEBRIDGE_ROLE` | `FORWARDER` recommended for cloud |
| `ICEBRIDGE_IDENTITY_FILE` | Identity path |
| `ICEBRIDGE_AUTH_TOKENS_FILE` | Bearer tokens file |
| `ICEBRIDGE_BOOTSTRAP` | Also announce `frostwire-bootstrap-v1` (default true with env) |
| `ICEBRIDGE_DHT` | Embed DHT SessionManager + announcer (default true for FORWARDER/BOTH via env) |

Generate a control token (prints **once** to stdout):

```bash
java -jar build/libs/icebridge.jar --generate-token --auth-tokens-file icebridge-tokens.txt
```

Firewall: open **UDP rUDP**. No TCP port is needed by peers. Control HTTP should stay **localhost** or firewalled.

### Co-located desktop + standalone

- Point desktop at remote IceBridge **or** change standalone `ICEBRIDGE_RUDP_PORT` / `ICEBRIDGE_RELAY_PORT`.
- Recommended dual-run pattern (desktop keeps defaults 6888/6889):
  ```bash
  ICEBRIDGE_ROLE=FORWARDER ICEBRIDGE_RELAY_PORT=7000 ICEBRIDGE_RUDP_PORT=7001 \
    ICEBRIDGE_CONTROL_HTTP_PORT=18081 ICEBRIDGE_DHT=true ./gradlew icebridge
  # or: ./scripts/icebridge-run-local.sh --colo
  ```
- Self-discovery is skipped via own Ed25519 pub + loopback filters; public-IP hairpin can still look like “self”.

### EC2 / pure-forwarder DHT smoke

**Critical:** the fat JAR must include **Linux** jlibtorrent natives (`lib/x86_64/*.so`).
`./gradlew icebridgeJar` packs multi-arch natives (Linux x86_64/arm64 + macOS) so a Mac-built
`icebridge.jar` works on EC2. Verify before upload:

```bash
# Prefer unzip (no JDK jar tool required); jar tf also works
unzip -Z1 desktop/build/libs/icebridge.jar | grep -E 'lib/x86_64/.*\.so'
```

Do not deploy a jar missing the Linux x86_64 native (it will fail on EC2);
arm64 only matters for Graviton hosts.

#### Build and install from the EC2 checkout

The installer keeps the build and privileged installation separate. From the
repository checkout, build as `ubuntu` (never with `sudo`), then run the
printed install command:

```bash
cd ~/frostwire/desktop
./scripts/icebridge-systemd-install.sh --build
# Then copy/paste both printed commands. The first stages the JAR root-owned.
```

#### Manual steps (same outcome)

1. **Build** on laptop or EC2: `cd desktop && ./gradlew icebridgeJar`
2. **Upload** `build/libs/icebridge.jar` to e.g. `/opt/icebridge/` on the instance (Amazon Linux 2/2023 or Ubuntu; JDK 17+).
3. **Install unit** with `scripts/icebridge-systemd-install.sh` (writes `icebridge.env` once, generates token once, systemd `icebridge.service`, `ICEBRIDGE_DHT=true`). Re-run preserves env unless `FORCE_ENV=1`.
4. **Security group** (inbound):
   | Proto | Port | Source | Why |
   |-------|------|--------|-----|
      | UDP | 6889 | `0.0.0.0/0` | rUDP mesh |
   | TCP | 22 | your IP | SSH |
   | ~~Control HTTP~~ | — | **do not open** | Control binds **127.0.0.1 only**; use SSH `-L` |
   Outbound: allow UDP to public DHT bootstrap (or all outbound UDP).
5. **Health**: `ssh -L 18081:127.0.0.1:8081 host` then `curl http://127.0.0.1:18081/health` → `{"ok":true,...}`
6. **Token**: read once from host `icebridge-token.once` (or regenerate with `java -jar icebridge.jar --generate-token`).
7. **Desktop smoke paths**:
   - **A. Remote control plane:** control is **loopback-only**. Tunnel first (`ssh -L 18081:127.0.0.1:8081 host`), then Settings → IceBridge enable + USE_REMOTE + `http://127.0.0.1:18081` + bearer token. **Never** `http://PUBLIC_IP:8081`.
   - **B. Pure DHT (the real M-DHT test):** desktop local child OK; do **not** seed host-cache with the EC2 IP. Wait ≥60s. PeerDiscovery should find EC2 via `frostwire-relays-v1`, the rUDP identity handshake succeeds → **verified** peer (not placeholder). Logs: DHT tick / identity auth success for public IP.
8. **Pass criteria:** desktop PeerDirectory has verified entry whose host is the EC2 public IP (or DNS); optional distributed search to a second peer through the mesh.

#### Logs to expect on the relay

- `IceBridge DHT session started`
- `IceBridge DHT announcer started: peerTopic=false bootstrapTopic=true announcePort=6889` (FORWARDER)
- `DhtAdvertiser started, interval=60s peerTopic=false bootstrapTopic=true`
- No `Failed to load jlibtorrent` / UnsatisfiedLinkError

#### Common failures

| Symptom | Cause | Fix |
|---------|--------|-----|
| UnsatisfiedLinkError / no DHT | Mac-only natives in jar | Rebuild with multi-arch `icebridgeJar`; verify `.so` present |
| Desktop never verifies peer | Security group blocks UDP 6889 | Open the rUDP port (`ss -lunp \| grep 6889` on the host) |
| Auth spam / self | Co-located ports / host-cache | Use pure DHT path; host-cache is UI only |
| Control 401 | Wrong/missing token | `X-IceBridge-Token` from tokens file |
| DHT up but no peers find relay | Outbound UDP blocked | Open egress; wait 1–2 announce intervals |

## Security model

- **Search**: Ed25519 signed request/response; timestamp skew; rate limits on **requesterPub** after verify.
- **v1 multi-hop**: **disabled** (`ttl=0`). Forwarder re-sign is incompatible with requesterPub verification.
- **Control API**: `X-IceBridge-Token` on all routes except `/health`.
- **rUDP**: HELLO auth, app-level fragmentation, session caps, hole-punch requires auth.

## Discovery topics (BEP 5)

- `frostwire-peers-v1`
- `frostwire-relays-v1`
- `frostwire-bootstrap-v1`

Identity manifests: BEP 46 salt `frostwire-identity-v1`.

## Debugging checklist

1. `ICEBRIDGE_ENABLED` + distributed search on?
2. Identity loaded (`libtorrent/identity.dat`)?
3. Child healthy (`/health`) or remote reachable with token?
4. Bind conflicts on 6889?
5. PeerDirectory has **verified** peers (not placeholders)?
6. Logs: config dump, “Relay stack ready”, discovery ticks.

## Android

In-process IceBridge (no subprocess). Wired via `AndroidRelayStack`. Real-device validation is still a human QA item.

## Related docs

- `DESIGN_RELAY_REGISTRY.md` — design + evolutionary record  
- `ICEBRIDGE_ARCHITECTURE_REVIEW.md` — north-star layering review + implementation status matrix  
- `desktop/ICEBRIDGE_REVISION_PLAN.md` — 2026-07 review findings and task waves  
- Skills: `skills/frostwire-engineer`, `skills/frostwire-code-reviewer` (§12 IceBridge)
