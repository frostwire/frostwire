# Karma from received endorsements

Covers frostwire/frostwire#1416 (propagation and discovery) and #1417 (scoring). This started as
a design proposal (da6dfffe3); the maintainer answered its seven questions on #1416, and this
document now describes what was built on branch `karma-1416-received`. The IceBridge white paper
(§10) has the wire details.

## Where things stood (after #1412 to #1415)

- A finished download from a distributed-search peer endorses that peer automatically, and the
  user can also endorse by hand. Each endorsement (EN) names the endorsee (`pp`) and the info hash
  (`ih`), and is signed into the endorser's own hash-linked chain.
- Verification enforced the epoch budget, but decayed the carried energy once per commitment,
  however many epochs were skipped.
- The score was the number of ENs a peer had **given** (`PeerKarmaCache.computeScore`). Anyone
  could raise their own score by endorsing made-up keys.
- Each chain was published as one BEP 46 item, at most 950 bytes: about one EC and one EN. A chain
  with only an EC, or whose newest epoch had two ENs, published nothing, and its item expired.
- `KarmaChainTable.loadChain` loaded every row whatever its owner.

## Decisions and what was built

### 1. Scoring: trust-weighted from the start

`ReceivedKarma` (common) indexes the ENs of every chain this node verifies, by endorsee, and scores
them. An EN of X by E for torrent T counts only if E's chain verified, E has identity proof of
work (`IdentityKeys.meetsIdentityDifficulty`, 20 bits, as the rUDP handshake requires) and E is
not X, and X announced T (decision 3).

```
vote(E, X) = 0.5 ^ (age of E's newest counting EN for X, in epochs / 30)   one per E and X
T(self)    = 1
T(X)       = min(1, sum over E: T(E) * 0.5 * vote(E, X))                     3 hops (WOT_MAX_DEPTH)
w(self)    = 1
w(E)       = max(T(E), 0.1 if this node first saw E at least 7 epochs ago, else 0)
karma(X)   = sum over E: w(E) * vote(E, X)
```

Parameters (`ReceivedKarma`), and why:

| Parameter | Value | Why |
|---|---|---|
| Trust seed | this node, 1.0 | Trust must come from somewhere a Sybil can't reach. This node's own endorsements follow real downloads it verified; peers they endorse are trusted half as much per hop, as in the Peer Directory's web of trust (`DECAY = 0.5`, `WOT_MAX_DEPTH = 3`). A cluster nobody this node trusts endorses gets no trust. |
| `STRANGER_WEIGHT` | 0.1 | Without some weight for strangers a new node (no endorsements yet) would show 0 for everyone. 0.1 keeps a stranger's vote small: ten aged, PoW-valid strangers equal one peer this node endorsed directly. |
| `MIN_ENDORSER_AGE_EPOCHS` | 7 (about a week) | A stranger counts only after this node has **seen** it for a week. The age is witnessed, not read from the chain: an owner can sign commitments at any past height (a block hash proves "not before", never "at"), so chain epochs prove nothing about age. First sightings are kept in `libtorrent/karma_first_seen.txt` across restarts. Trusted endorsers (T > 0.1) skip the floor: this node's own graph already vetted them. |
| `HALF_LIFE_EPOCHS` | 30 (about a month) | Karma should follow current behaviour, but chains are sparse and readers see at most 8 pages; a month keeps a peer that endorsed last week near full weight and fades a year-old vote to about 0. |
| Vote cap | 1 per endorser per endorsee | Endorsing the same peer for many torrents is still one vote. |

Time is the current epoch of this node's own chain, which commits every epoch (the clock is
`writer.chain().currentEpoch()`); only before it has one, the newest epoch indexed. A chain
verified offline can't push "now" forward.

`PeerKarmaCache.receivedKarma` is the karma; `PeerDirectory.trustScore` adds it (instead of the
given count) to the structural trust, and `PeerDirectory.displayKarma`, which every karma
indicator reads, is the received karma for any peer, this node included (spam-marked peers still
show -1). `LocalSharedTorrentSearchPerformer` ranks by it and no longer waits on a DHT fetch.

### 2. Discovery: the partial local view

The index holds the endorsements this node has heard about: every chain `RemoteKarmaChainFetcher`
verifies (publishers of results and directory peers) and its own chain (re-indexed after each
endorsement). There is no global "who endorsed X" index and no wire change for discovery yet.

### 3. Only torrents the endorsee announced

`DistributedSearchPerformer`, after verifying a search response's signature, records each row
whose publisher is the responder as that peer's announcement (`PeerDirectory.recordAnnouncement`).
For this node, a torrent counts when it is in its own index. An EN for a torrent this node never
saw its endorsee announce doesn't count yet; it counts once the announcement is seen. Peers that
relay others' results get no credit for them.

### 4. Participation stays, apart from karma

`PeerKarmaCache.participation` / `cachedParticipation` / `endorsementsGiven` (renamed from
`getKarma`, `getCachedKarma`, `computeScore`) count the ENs a peer gave. The desktop Options >
Identity pane and Android's Distributed Search settings show "Participation: N endorsements given"
next to the karma; it never counts as trust.

### 5. Publisher: compact entries and pages

Manifest version 2 (`KarmaChainPages`):

- entries carry raw binary hashes, keys and signatures; seq and prevHash are derived from the
  page and the previous entry, the epoch from the height, the owner from the item's key. The
  signed canonical bytes are unchanged. An EN is about 196 bytes (362 in version 1); a page holds
  three or four ENs or about six ECs.
- the head page is under `frostwire-karma-v1`; up to 7 older pages under `frostwire-karma-v1/<n>`,
  counting back from 1. The head holds the newest entries that fit, starting at an EC when one is
  among them, so it verifies alone.
- every non-empty chain is published on every tick, a lone EC included, and right after each
  endorsement.
- readers fetch older pages within the lookup's deadline and keep those that link; version 1
  manifests (released clients) still read.

### 6. `karma_chain` keyed by owner

Desktop `KarmaChainTable` and Android `AndroidKarmaChainStore` key the table by
`(endorser_pub, seq)`; `loadChain` and the rejected-chain set-aside touch only the owner's rows. A
table from an earlier version is rebuilt once at open, keeping its rows.

### 7. Decay per skipped epoch, as chain format 2

A format 2 EC has energy `5 + 0.5^Δ · R` (Δ epochs since the previous EC, R its unused energy) and
signs `fv = 2`. Format 1 ECs (no `fv`) keep verifying under their own rule (`5 + 0.5 · R`); a
chain moves to format 2 at its first new EC and never back. The stores persist the format in a
`format_version` column.

## Compatibility with released clients (7.1.3 / 3.2.3)

- They read only the head item. A version 2 head has no `entries` list; their reader returns
  null (every path is caught), so a new peer shows as having no chain, score 0. Nothing crashes.
- Even in a version 1 manifest they couldn't verify a format 2 EC: its signature covers `fv`,
  which their canonical form leaves out.
- New clients read old clients' version 1 manifests. Their ENs count once the endorser's chain is
  verified and the endorser is trusted or has been seen for 7 epochs.
- No rUDP, IceBridge or search wire format changed.

## Next steps (not built)

- Discoverability for any X: an endorsee-published "received" manifest
  (`frostwire-karma-recv-v1/<page>`, the endorser sends the signed EN over rUDP), or gossip of EN
  references in IceBridge peer manifests.
- Fetch an endorsee's signed catalog to learn announcements this node didn't see in its own
  searches.
- Populate the Peer Directory's web of trust (`addEndorser`) from the same index.
