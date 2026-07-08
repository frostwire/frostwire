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
| Protocol-agnostic opaque payloads | One of possibly many app protocols |

FrostWire desktop/Android are **clients and optional co-located nodes** of that network. A cloud `FORWARDER` must be fully useful **without** any FrostWire GUI process.

**DHT announce (MUST):** standalone `./gradlew icebridge` with `ICEBRIDGE_DHT=true` (default for FORWARDER/BOTH via env) embeds a minimal jlibtorrent session and runs `DhtAdvertiser` so pure cloud forwarders appear on `frostwire-relays-v1` (+ optional bootstrap topic). In-process desktop/Android IceBridge leaves DHT to BTEngine (`dhtEnabled` defaults false on builders).

### Hybrid planes

| Plane | Port (defaults) | Purpose |
|-------|-----------------|---------|
| Identity / bootstrap | TCP **6888** (`ICEBRIDGE_RELAY_PORT` / settings relay listen) | Direct TCP identity handshake; real Ed25519 pub + `IdentityRecord` v2 |
| Mesh data | UDP **6889** (`ICEBRIDGE_RUDP_PORT`) | Authenticated rUDP + fragmentation |
| Control API | TCP **8080** localhost (or auto) | `/health`, `/register`, `/route`, `/send`, `/poll`, `/metrics` |

Desktop may run **both** an in-app `IncomingRelayServer` (identity TCP) and a **child** IceBridge process (or attach to a **remote** standalone).

## Roles

- `CLIENT` — join mesh, do not advertise as forwarder (unless auto-elected when connectable).
- `FORWARDER` — help NAT’d peers; announce under relay DHT topic when DHT is available.
- `BOTH` — typical desktop child default.

## Desktop usage

Settings → IceBridge / Search engines:

- Enable IceBridge + Distributed search.
- **Local child**: launches `build/libs/icebridge.jar` (dev) or `icebridge.jar` under user settings (prod).
- **Remote**: set remote URL + bearer token (no local subprocess).

Ports are configurable. **Do not** run two processes binding the same TCP 6888 / UDP 6889 without changing one side.

Logs print a structured config dump at startup (`[set]` for tokens, never the secret).

Identity file (shared with desktop relay stack):

```text
~/.frostwire/libtorrent/identity.dat
```

## Standalone / cloud relay

```bash
cd desktop
cp .env.example .env   # optional
./gradlew icebridge
```

Useful env vars:

| Variable | Meaning |
|----------|---------|
| `ICEBRIDGE_HOST` | Bind host (cloud: `0.0.0.0`) |
| `ICEBRIDGE_RUDP_PORT` | UDP mesh |
| `ICEBRIDGE_RELAY_PORT` | TCP identity handshake |
| `ICEBRIDGE_CONTROL_HTTP_PORT` | Control HTTP |
| `ICEBRIDGE_ROLE` | `FORWARDER` recommended for cloud |
| `ICEBRIDGE_IDENTITY_FILE` | Identity path |
| `ICEBRIDGE_AUTH_TOKENS_FILE` | Bearer tokens file |
| `ICEBRIDGE_BOOTSTRAP` | Also announce `frostwire-bootstrap-v1` (default true with env) |
| `ICEBRIDGE_DHT` | Embed DHT SessionManager + announcer (default true for FORWARDER/BOTH via env) |

Generate a control token (prints **once** to stdout):

```bash
java -jar build/libs/icebridge.jar --generate-token --auth-tokens-file icebridge-tokens.txt
```

Firewall: open **UDP rUDP** (and TCP identity if remote peers handshake you). Control HTTP should stay **localhost** or firewalled.

### Co-located desktop + standalone

- Point desktop at remote IceBridge **or** change standalone `ICEBRIDGE_RUDP_PORT` / `ICEBRIDGE_RELAY_PORT`.
- Recommended dual-run pattern (desktop keeps defaults 6888/6889):
  ```bash
  ICEBRIDGE_ROLE=FORWARDER ICEBRIDGE_RELAY_PORT=7000 ICEBRIDGE_RUDP_PORT=7001 \
    ICEBRIDGE_CONTROL_HTTP_PORT=18080 ICEBRIDGE_DHT=true ./gradlew icebridge
  ```
- Self-discovery is skipped via own Ed25519 pub + loopback filters; public-IP hairpin can still look like “self”.

### EC2 / pure-forwarder DHT smoke (manual)

1. On the relay host: build jar, generate token, run FORWARDER with `ICEBRIDGE_DHT=true` (default via env).
2. Open UDP rUDP + TCP identity in security group; keep control HTTP localhost-only (SSH tunnel if needed).
3. On desktop: enable IceBridge remote (URL + token) **or** leave local child off and rely on DHT discovery of the cloud FORWARDER.
4. Confirm desktop logs discovery of the EC2 public endpoint and that PeerDirectory has a **verified** peer (identity TCP success) — not only host-cache placeholders.
5. Optional: `dhtGetPeers` / discovery tools should list the forwarder under `frostwire-relays-v1` after ~1 min.

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
4. Bind conflicts on 6888/6889?
5. PeerDirectory has **verified** peers (not placeholders)?
6. Logs: config dump, “Relay stack ready”, discovery ticks.

## Android

In-process IceBridge (no subprocess). Wired via `AndroidRelayStack`. Real-device validation is still a human QA item.

## Related docs

- `DESIGN_RELAY_REGISTRY.md` — design + evolutionary record  
- `desktop/ICEBRIDGE_REVISION_PLAN.md` — 2026-07 review findings and task waves  
- Skills: `skills/frostwire-engineer`, `skills/frostwire-code-reviewer` (§12 IceBridge)
