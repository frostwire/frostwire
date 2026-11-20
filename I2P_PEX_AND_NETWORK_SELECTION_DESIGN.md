# I2P PeX and per-torrent network selection: design

Tickets: [#1301](https://github.com/frostwire/frostwire/issues/1301) (I2P PeX support) and
[#1304](https://github.com/frostwire/frostwire/issues/1304) (choose the network per torrent: I2P
only, clearnet only, or mixed).

Status: research plus the first behavior-neutral slice (phase 1a, see section 6). Everything in
sections 1 and 2 was checked by reading the sources named in it, not recalled.

Sources read:

- libtorrent checkout under `frostwire-jlibtorrent/libtorrent` (`LIBTORRENT_VERSION "2.1.2.0"` in
  its `version.hpp`; FrostWire pins jlibtorrent `2.1.2.1`, `desktop/build.gradle:331`, so the
  checkout may be a patch level behind what ships: re-check the two files marked (*) below against
  the shipped build before relying on them).
- jlibtorrent Java and SWIG sources under `frostwire-jlibtorrent/src/main/java/com/frostwire/jlibtorrent`.
- FrostWire: `common/.../bittorrent/BTEngine.java`, `BTContext.java`, desktop
  `ConnectionSettings`, `Initializer`, `I2PPaneItem`.

## 1. What libtorrent 2.1 offers for I2P

### 1.1 I2P PeX already exists natively

`src/i2p_pex.cpp` (*) implements a second PEX extension, **`i2p_pex`**, separate from `ut_pex`.

- Handshake: advertises `"i2p_pex"` in the BEP 10 `m` dictionary with local extension index 9.
- Message: bencoded dictionary with `added` (concatenated 32-byte SHA-256 hashes of peer I2P
  destinations), `added.f` (one flag byte per peer, always 0) and `dropped` (32-byte hashes). The
  receiver ignores `dropped` and `added.f`.
- Sender: once a minute per torrent, at most 50 new peers per message (`max_peer_entries`). Only
  peers that are I2P sockets (`is_i2p(socket)`), are connected, are past the handshake, and are
  outgoing or told us their listen port.
- Receiver: per added hash calls `torrent::add_i2p_peer`, which resolves `<base32>.b32.i2p` through
  the SAM session and adds an `i2p_peer`. Disconnects on a message over 500 KiB or more than one
  message per 60 s.
- **Gate:** `create_i2p_pex_plugin` returns nothing unless `torrent.is_i2p()` is true and the
  torrent is not private. It is registered by default: `src/session_params.cpp:32` adds it to
  `default_plugins()` under `TORRENT_USE_I2P`, which is on by default
  (`include/libtorrent/config.hpp:544`).
- Not found in the checkout: a limit on how many peers one received message may add (this is what
  our upstream PR 9164 is about). Today a peer can push up to 500 KiB / 32 B (about 16k) hashes
  per message, each costing a SAM name lookup.

`ut_pex` stays off for I2P torrents unless `allow_i2p_mixed` is true (`src/ut_pex.cpp:607`), and
then only carries IP peers.

So #1301 needs no new native code for FrostWire to *receive and send* I2P PeX, provided the
session is created with `SessionParams(SettingsPack)` or `SessionParams(session_params)`, which
keep the default plugins (`SessionParams.java:103-140`; `BTEngine.defaultParams()` and
`loadSettings()` both do). What it needs is:

1. the torrent must be flagged I2P (section 1.2),
2. the SAM bridge must be up (already configured by `BTEngine`, `BTEngine.java:308-329`),
3. visibility: nothing in the Java API tells us how many I2P peers a torrent has (section 3).

### 1.2 What decides "this is an I2P torrent"

`aux::torrent::m_i2p` (`src/torrent.cpp:262,328,980,990`) is true when any of:

- `add_torrent_params.flags` has `torrent_flags::i2p_torrent` (bit 24, `torrent_flags.hpp:295`);
- the `.torrent` has a tracker whose URL host ends in `.i2p` (`torrent_info::is_i2p`);
- `torrent_handle::set_flags(flags, mask)` set it at runtime (`torrent::set_flags`,
  `src/torrent.cpp:990-992`; the flag is read back by `torrent::flags()`, line 980).

Note that the second rule also sets the flag from a `.torrent`'s own tracker list: setting the
flag **off** with `set_flags` also clears it for such a torrent.

### 1.3 What an I2P torrent does differently

All of these are conditioned on `is_i2p() && !allow_i2p_mixed`, where `allow_i2p_mixed` is a
**session-wide** setting (`settings_pack::allow_i2p_mixed`, `settings_pack.hpp:571`):

| Behavior | Where |
|---|---|
| No clearnet peer is ever added to the peer list (fires `peer_blocked_alert` reason `i2p_mixed`) | `torrent.cpp:11952` |
| Incoming clearnet connection to the torrent is refused | `peer_connection.cpp:1373-1381` |
| Not announced to the DHT | `torrent.cpp:770` |
| DHT replies ignored | `torrent.cpp:3119` |
| Not announced on LSD, LSD peers ignored | `torrent.cpp:2981`, `session_impl.cpp:5894` |
| Only `.i2p` trackers are used (`announce_entry::i2p`) | `torrent.cpp:3151-3160` (`i2p_compatible_tracker`) |
| `ut_pex` not created | `ut_pex.cpp:607` |
| I2P peers are connected through the SAM bridge, with a 4x connect timeout | `torrent.cpp:8120-8181`, `peer_connection.cpp:232` |

With `allow_i2p_mixed` true, every one of those restrictions is lifted for **every** I2P torrent in
the session.

### 1.4 Gaps found in libtorrent itself

- **G1. Mixed mode is session-wide, not per torrent.** "I2P only" and "mixed, with I2P PeX" are
  both `i2p_torrent` torrents; they differ only in a global setting. A session cannot hold an
  I2P-only torrent and a mixed one at once. Needs upstream: a per-torrent mixed flag (or
  `allow_i2p_mixed` honored from per-torrent flags).
- **G2. Web seeds and `url_seeds` are not gated.** `connect_to_url_seed` and the web seed code have
  no `is_i2p()` check, so an I2P-flagged torrent whose `.torrent` or add parameters carry
  `url-list` or `httpseeds` still contacts them over clearnet. The app can work around it (remove
  them, section 4) but a fail-closed guarantee belongs upstream.
- **G3. A non-I2P torrent gets no I2P PeX** (the plugin is not created, 1.1) and never exchanges
  I2P peers with an I2P-capable swarm. So "clearnet plus I2P" for one torrent is only reachable
  through G1's mixed mode.
- **G4. No limit on peers added by one received `i2p_pex` message** (our PR 9164).
- **G5. `ip_filter` applies per torrent only by the `apply_ip_filter` flag**, against one
  session-wide filter. It cannot express "this torrent: I2P only" by itself (but see 4.2).

## 2. What jlibtorrent 2.1.2.1 exposes

Verified in `frostwire-jlibtorrent/src/main/java/com/frostwire/jlibtorrent`.

| Need | Exposed? | Where |
|---|---|---|
| SAM host/port, tunnel quantity/length/variance, `allow_i2p_mixed` | Yes, raw SWIG only | `swig/settings_pack.java` (`string_types.i2p_hostname`, `int_types.i2p_port`, `i2p_inbound_quantity`, ..., `bool_types.allow_i2p_mixed`). The friendly `SettingsPack.java` has **no** I2P getters/setters; FrostWire already uses the SWIG enums (`BTEngine.java:308-329`) |
| `i2p_torrent` torrent flag | Yes, but not in the Java `TorrentFlags` class | `swig/libtorrent.java:128` `libtorrent.getI2p_torrent()`; `TorrentFlags.java` lists only the other flags, so FrostWire can call `libtorrent.getI2p_torrent()` today and a `TorrentFlags.I2P_TORRENT` constant is a one-line jlibtorrent follow-up |
| Set/unset flags on a live torrent | Yes | `TorrentHandle.setFlags(flags)`, `setFlags(flags, mask)`, `unsetFlags` (`TorrentHandle.java:495-503`) |
| `disable_dht`, `disable_lsd`, `disable_pex` per torrent | Yes | `TorrentFlags.DISABLE_DHT/LSD/PEX` |
| `apply_ip_filter` per torrent, session `ip_filter` | Yes | `TorrentFlags.APPLY_IP_FILTER`, `swig/session_handle.set_ip_filter`, `swig/ip_filter.java` |
| Is this torrent file an I2P torrent | Yes | `TorrentInfo.isI2p()` (`TorrentInfo.java:523`) |
| Add I2P peer by destination hash | Yes | `swig/torrent_handle.connect_peer(sha256_hash)` (`torrent_handle.java:374-379`) |
| Trackers / url seeds per torrent | Yes | `TorrentHandle.trackers()`, `replaceTrackers`, `addTracker`, `urlSeeds()`, `removeUrlSeed` |
| Peer is on I2P | Flag only | `swig/peer_info.i2p_socket` (`peer_info.java:475`). **`peer_info::i2p_destination()` is not bound.** The Java `PeerInfo` wrapper has no I2P accessor |
| Blocked-by-mixed-mode events | Yes | `alerts/PeerBlockedAlert.Reason.I2P_MIXED` |
| SAM errors | Yes | `alerts/I2pAlert` (`no_i2p_router`) |
| `listen_failed_alert` for I2P | Yes | `SocketType` includes I2P |
| I2P PeX plugin | Automatic | default plugin, no binding needed; no counters or alerts for PeX are bound except `peer_log_alert` messages `I2P_PEX*` (`alert.cpp:2295`) |
| Per-torrent mixed flag, per-torrent peer-network filter | **No** | does not exist in libtorrent (G1) |
| `peer_class_type_filter::i2p_socket` | Bound, but session-wide | `swig/peer_class_type_filter.java:83`; maps socket types to peer classes for the whole session, not per torrent |

Conclusion for #1301: nothing is missing in the binding to *turn I2P PeX on* for an I2P torrent.
A `TorrentFlags.I2P_TORRENT` constant and `peer_info.i2p_destination()` (to show a peer's `.b32.i2p`
in Transfer Details) are small jlibtorrent additions; the rest is policy (G1 to G3).

## 3. FrostWire today

- Engine: `BTEngine.java:308-329` sets the SAM host, port, `allow_i2p_mixed` and tunnel options from
  `BTContext` when I2P is enabled, else clears `i2p_hostname`. `BTContext.i2p*` fields come from
  `ConnectionSettings.I2P_*` in `Initializer.java:1196-1204`. The options pane is `I2PPaneItem`
  ("Allow mixed mode (I2P + regular peers)" maps to the global `allow_i2p_mixed`).
- Nothing sets `torrent_flags::i2p_torrent`: a torrent is I2P only if its `.torrent` has an `.i2p`
  tracker. Magnets and normal torrents never get I2P peers or I2P PeX.
- The Peers table (`TransferDetailPeersDataLine.java:64,221`) has the I2P bit commented out.
- Existing sidecars use `BTEngine.canonicalInfoHash` (v1 when present, else v2) for `.torrent` and
  `.resume` names (`BTEngine.java:900-946`).

## 4. Per-torrent network choice: design

### 4.1 Semantics

`TorrentNetworkMode` (`common/.../bittorrent/TorrentNetworkMode.java`, immutable enum):

| Mode | Peers | Trackers | DHT/LSD/PEX | Web seeds |
|---|---|---|---|---|
| `MIXED` (default, today's behavior) | clearnet and I2P | all | as configured | yes |
| `I2P_ONLY` | I2P only | `.i2p` only | none (only `i2p_pex`) | none |
| `CLEARNET_ONLY` | clearnet only | non-`.i2p` | as configured | yes |

Default is `MIXED` for a torrent without a stored choice and for any unknown stored value, so
existing torrents and downgrades behave exactly as before.

### 4.2 Enforcement in the engine (phase 1b, needs the engine change)

Engine-side facts used by every mode: the key is `canonicalInfoHash`; the mode is applied when the
handle is added (`BTEngine.download(...)` and session restore) and re-applied when the user changes
it (`TorrentHandle.setFlags/unsetFlags`).

**I2P_ONLY** must fail closed:

1. Require I2P to be enabled and the SAM bridge configured. If not, do not start the torrent:
   leave it paused with an error state the UI shows ("I2P is not enabled"). Never silently fall
   back to clearnet.
2. Set `i2p_torrent` (`libtorrent.getI2p_torrent()`) on the add parameters and again with
   `setFlags` after add.
3. Set `disable_dht` and `disable_lsd` as belt and braces (libtorrent already skips both while
   `is_i2p && !allow_i2p_mixed`, 1.3).
4. Remove every URL seed (`TorrentHandle.removeUrlSeed` for each of `urlSeeds()`, and strip
   `url_seeds` from the add parameters) because of G2. Re-check after metadata arrives for magnets.
5. Replace trackers with the `.i2p` subset (`replaceTrackers`); clearnet trackers are already
   skipped by libtorrent (`i2p_compatible_tracker`) but must not be shown as active.
6. **Session `allow_i2p_mixed` must be false for the guarantee to hold (G1).** Today it is a user
   option. Rule: while any torrent is `I2P_ONLY`, the engine forces `allow_i2p_mixed=false`
   regardless of the option. Consequence: an `I2P_TORRENT`-flagged torrent can then never be mixed.
   That is acceptable because of the mapping below, until upstream offers a per-torrent flag.
7. Belt and braces for a bug or a race: `apply_ip_filter` stays on for I2P-only torrents and the
   engine adds a session filter blocking all IPv4 and IPv6 only for torrents that keep the flag,
   with `MIXED` and `CLEARNET_ONLY` torrents cleared with `unsetFlags(APPLY_IP_FILTER)`. Open
   question 3: this changes how the user's real IP filter works, so it is not done silently.
8. Test with the `peer_blocked_alert` reason `I2P_MIXED` and a counter on any non-I2P peer seen in
   `peer_info` for the torrent (must stay 0).

**CLEARNET_ONLY**: do not set `i2p_torrent`; and since non-I2P torrents may still accept I2P peers
(`torrent_flags.hpp:288`), refuse them: the engine drops I2P peers for the torrent when it sees
`peer_info.i2p_socket`, or blocks them through the same mechanism. Strictly this needs upstream
help for a hard guarantee (see G5); until then it is best effort and documented as such.

**MIXED**:
- Without I2P enabled: identical to today.
- With I2P enabled: leaves the flag unset, as today. Getting I2P PeX *and* clearnet for one torrent
  needs G1 upstream (per-torrent mixed flag). Until then "mixed with I2P PeX" is only available
  when the user turns on the global "Allow mixed mode" and sets the torrent flag, and I2P-only
  torrents are then not guaranteed (the engine must refuse that combination, see open question 1).

### 4.3 Data model

Persist in our own store, one text file in `ctx.homeDir` (default name `torrent-network-policy.txt`),
`<canonical hash>=<MODE>`, only non-default entries, keyed by `canonicalInfoHash`. The class is
`TorrentNetworkPolicyStore` (phase 1a, in this branch).

Why not libtorrent's resume data: the resume data stores the *flags*, including `i2p_torrent`, but
`MIXED` versus `CLEARNET_ONLY` cannot be told apart from it, and a flag cannot say "the user chose
this" versus "the `.torrent` has an `.i2p` tracker". Our own store holds the user's intent; the
engine derives flags from it. Same reason `.resume` is not extended: older builds would drop
unknown keys.

Rules:

- One key function for every sidecar: `BTEngine.canonicalInfoHash` (v1 when present, else v2).
  `TorrentNetworkPolicyStore.canonicalKey(v1Hex, v2Hex)` spells the same rule on hex strings so it
  is testable without native types; phase 1b makes `BTEngine.canonicalInfoHash` delegate to it so
  there is one implementation. Hybrid torrents therefore keep their choice across restarts.
- Unknown value, bad line, bad hash: ignored, torrent is `MIXED`; never fails startup.
- `get`/`set`/`remove` touch memory only (callable on the EDT); `load()` and `flushIfDirty()` do
  I/O on engine threads. Writes are temp file plus rename.
- Removing a torrent removes its entry.

### 4.4 UI (phase 2)

- Add-torrent dialog (`TorrentFetcherDownload`/the add dialog): a "Network" combo, "Mixed
  (default)", "I2P only", "Clearnet only"; I2P entries disabled with a tooltip when I2P is not
  enabled in Options > I2P.
- Transfer Details: a "Network" row with the same combo; changing it applies through the engine
  off the EDT and shows progress/errors.
- Peers table: re-enable the I2P column using `peer_info.i2p_socket`.
- Options > I2P: relabel "Allow mixed mode" so it says what it still means once per-torrent modes
  exist.
- All strings through `I18n.tr`; model changes through the store, never from a Swing handler.

### 4.5 Test plan

- Unit (this branch): mode parsing, store round trip, unknown values, caps, concurrency,
  canonical key for v1, v2 and hybrid; Jazzer harness for the parser.
- Engine (phase 1b): fake `TorrentHandle` seams for "I2P_ONLY with I2P disabled stays paused",
  "flags applied at add and on change", "url seeds removed", "`allow_i2p_mixed` forced false".
- Native integration (phase 3, jlibtorrent test session with two local sessions and an in-process
  fake SAM, or the existing libtorrent simulator tests): an I2P-only torrent never connects to a
  loopback clearnet peer, tracker or web seed; a MIXED torrent still does.
- UI: tests with `java.lang.reflect.Proxy` fakes, asserting no I/O on the EDT.

## 5. I2P PeX (ticket 1301): what we do

1. Phase 3a: with `I2P_ONLY` torrents setting `i2p_torrent`, libtorrent's own `i2p_pex` is active
   for them. No binding work. Verify with `peer_log_alert` (`I2P_PEX_FULL`, `I2P_PEX_DIFF`) and the
   SAM session of a second client.
2. Phase 3b: bind `peer_info.i2p_destination()` and add `TorrentFlags.I2P_TORRENT` to jlibtorrent;
   show I2P peers' `.b32.i2p` addresses in the Peers table.
3. Upstream: PR 9164 (limit peers added per received `i2p_pex` message), G1 (per-torrent mixed
   flag), G2 (web seeds on I2P torrents), G5.

## 6. Phases

- [x] **1a** Behavior-neutral foundation: `TorrentNetworkMode`, `TorrentNetworkPolicyStore`
  (persistence, canonical-key helper), tests, fuzz harness. No behavior change.
- [ ] **1b** Engine: apply flags at add/restore/change, `allow_i2p_mixed` rule, fail-closed checks,
  web seed removal, `BTEngine.canonicalInfoHash` delegating to the shared key function.
- [ ] **2** UI: add-torrent dialog, Transfer Details, Peers table I2P column, Options wording.
- [ ] **3** I2P PeX: verify the native extension end to end, bind `i2p_destination()`, show peers,
  follow the upstream changes (9164, per-torrent mixed flag).

## 7. Open questions for the maintainer

1. Until libtorrent has a per-torrent mixed flag, should an enabled global "Allow mixed mode" win
   (I2P-only is then best effort) or should `I2P_ONLY` win (the global option is overridden while
   any torrent is `I2P_ONLY`)? Recommendation: `I2P_ONLY` wins, and the Options pane says so.
2. Should `I2P_ONLY` with I2P disabled refuse to start (recommended, fail closed) or ask to enable
   I2P?
3. May the engine use the session IP filter as a second barrier (4.2 step 7), given the user's own
   IP filter shares it? Recommendation: no, rely on G1/G2 workarounds and push upstream.
4. Do we carry G1 (per-torrent mixed flag) and G2 (web seeds on I2P torrents) upstream ourselves?

## 8. Unverified

- Whether the shipped 2.1.2.1 native build equals the checkout's source (checked `i2p_pex.cpp`
  and `ut_pex.cpp`: re-diff before phase 3).
- That `session_params` read back from saved state (`BTEngine.loadSettings`) registers the default
  plugins including `i2p_pex`. Expected, not exercised.
- Runtime behavior with a real I2P router (no router was available): everything above is from
  source reading.
- `session_impl` SAM reconnect on `i2p_hostname` change versus torrents already added.
