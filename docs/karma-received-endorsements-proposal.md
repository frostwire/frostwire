# Karma from received endorsements: proposal

Covers frostwire/frostwire#1416 (propagation and discovery) and #1417 (scoring). This is a design
proposal only: nothing here is implemented. It needs a revision of `SPEC_KARMA_CHAIN.md` before
any code.

## Where things stand (after #1412 to #1415)

- A finished download from a distributed-search peer endorses that peer automatically, and the
  user can also endorse by hand. Each endorsement (EN) names the endorsee (`pp`) and the info hash
  (`ih`), and is signed into the endorser's own hash-linked chain.
- Verification now enforces the epoch budget: an epoch commitment's epoch matches its height,
  energy follows the decay rule, nobody endorses themselves, and heights and hashes are checked
  against Bitcoin when a block source is available.
- The score is still the number of ENs a peer has **given** (`PeerKarmaCache.computeScore`).
  Anyone can raise their own score by endorsing made-up keys.
- Each chain is published as one BEP 46 mutable item under the owner's key, salt
  `frostwire-karma-v1`, at most 950 bytes: about one EC and one EN. Nothing lets a node find the
  ENs **about** a given peer.

## 1. Scoring

**A. One vote per distinct endorser (recommended first).** A peer's score is the number of
distinct endorsers whose verified chains hold at least one EN naming it. An endorser counts only
if:

- its chain verifies, including the Bitcoin checks;
- its identity meets `IDENTITY_DIFFICULTY`;
- it is not the endorsee;
- its chain is at least *k* epochs old, which costs the attacker time instead of CPU.

Each endorser's vote is capped at 1, however many torrents it endorsed, and decays with the age
of its latest EN for that endorsee. The score is simple and explainable, but Sybil-prone: see §3.

**B. Trust-weighted.** An endorser's vote is weighted by its own received score, computed to
`WOT_MAX_DEPTH` with `DECAY`, as `PeerDirectory` already models. This resists Sybil clusters that
nobody outside them endorses. It costs more to compute, needs many chains to be useful, and is
harder to explain to users.

Recommendation: ship A with the age floor and the per-endorser cap, keep the given-count as a
separate "participation" signal (not trust), and add B as a weight once (2) below gives enough
data. In both cases `PeerDirectory.trustScore` takes the received score instead of
`PeerKarmaCache`'s given count.

## 2. Propagation and discovery

A BEP 44/46 mutable item can only be written by the holder of its key. So "a per-endorsee DHT
key where endorsers append" (option (a) in #1416) can't be built on mutable items: an endorser
can't sign under the endorsee's key, and a salt derived from the endorsee under each endorser's
own key can't be enumerated by someone who doesn't already know the endorsers. The workable
options are:

- **(b) Local index plus gossip (no wire change).**
  - Every node indexes the ENs of each chain it verifies (`RemoteKarmaChainFetcher` already
    fetches the chains of publishers it sees in results) by endorsee.
  - IceBridge peer manifests carry the newest few EN references (endorser key, seq, entry hash)
    so nodes learn which chains to fetch.
  - The result is a partial view, which grows with use.
- **(a′) Endorsee-published "received" manifest.**
  - The endorser sends the signed EN to the endorsee over rUDP when it endorses.
  - The endorsee publishes references to the ENs it received under **its own** key, with salt
    `frostwire-karma-recv-v1/<page>`.
  - Each reference holds the endorser key, seq and signature: about 140 bytes encoded, so 6 fit
    in 1,000 bytes per page.
  - A reader verifies each EN against the endorser's chain, fetched under the endorser's key.
  - An endorsee can leave out references, but can't forge them, and has every reason to include
    them.
  - This makes "who endorsed X" answerable for any X, at the cost of one fetch per page plus one
    per endorser.
- **Publisher fixes from #1416, independent of the choice above.**
  - Republish on every tick, including chains with only an EC, so items don't expire.
  - Publish right after an endorsement.
  - Fit more than one EN: drop the per-entry fields a tail repeats, or page the chain as
    `frostwire-karma-v1/<n>`.

Recommendation: (b) first, because it needs no protocol change. Then (a′) for discoverability.
That is option (c) in the ticket, with (a) replaced by (a′).

## 3. Sybil and spam

- Identity PoW (20 bits) takes seconds. A Bitcoin-anchored EC costs only waiting. With plain A,
  an attacker with N aged identities gives a target up to N votes per epoch window.
- Mitigations, cheapest first:
  - the age floor and the vote cap;
  - count only endorsements of torrents the endorsee itself announced in its signed index
    (the info hash is in the EN, and the endorsee's signed announcements are fetchable);
  - weight by IceBridge path or address diversity;
  - move to B, where a cluster nobody outside endorses scores about 0.
- Spam and DoS:
  - keep a bounded per-endorsee index (LRU, newest per endorser);
  - verify lazily and on worker threads only;
  - cap chain and page fetches per lookup;
  - cache the Bitcoin tip (now `TipCachingBlockHeaderSource`).

## 4. Migration

- The v1 chain format already has everything (A) and (b) need (`pp`, `ih`, signatures, block
  anchors). The scoring change is local, and older clients keep publishing chains newer ones can
  index.
- (a′) adds new salts and an rUDP message. Old clients ignore both, and new clients fall back to
  (b) for old peers.
- The "received" score starts at 0 for everyone. The UI should say so instead of showing old
  given-counts as reputation.
- Clean up local state:
  - `KarmaChainTable.loadChain` loads every row whatever its owner, so after an identity change
    the old identity's entries are loaded under the new key. They then fail verification at the
    next restart and are overwritten ("starting fresh").
  - The table should be keyed by owner, which is a schema change worth doing in the same
    release.

## 5. Questions for the maintainer

1. Scoring: A (one vote per distinct, aged, PoW-valid endorser) first, or straight to
   trust-weighted B? What age floor *k* (in epochs) and decay half-life?
2. Discovery: is a partial local view (b) acceptable for the first release, or must every peer be
   able to answer "who endorsed X" from day one (a′)?
3. Should an endorsement count only for torrents the endorsee itself announced (stronger, but
   excludes peers that relay others' results)?
4. Keep the given-count visible as "participation", or drop it from the UI?
5. Publisher: page the chain (`frostwire-karma-v1/<n>`), compact the entry encoding, or both?
6. Accept a schema change to key `karma_chain` by owner, alongside this work?
7. The decay rule: the writer decays the carried energy **once per commitment**, however many
   epochs were skipped, and #1415 verifies it that way. Should it instead decay ×0.5 **per
   epoch**, as #1415's ticket text says? That changes chains already published, so it would need
   a format version.
