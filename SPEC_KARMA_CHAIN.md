# Peer Karma Chain — Implementation Spec v1

**Status**: Ready for implementation
**Author**: gubatron + claude-opus-4-6
**Base commit**: `d7e93f97e`
**Branch**: `master`

---

## 1. Overview

Every FrostWire node maintains its own **karma chain**: an append-only, hash-linked
sequence of signed records anchored to Bitcoin block heights. The chain tracks
endorsements the node issues to peers after successful downloads. No global chain;
each node is the sole writer of its own chain.

The system deters Sybil attacks through:
- **Proof-of-work identity mining** (leading zero bits in `SHA-1(ed25519_pub)`)
- **Bitcoin-anchored epochs** (can't fake time advancement)
- **Energy decay** (can't hoard karma energy across epochs)
- **Trust-graph distance scoring** (Sybil clusters score zero if no trust path exists)

---

## 2. Existing Code (DO NOT MODIFY unless stated)

These files already exist and must not be changed by this implementation:

| File | What it provides |
|------|-----------------|
| `common/.../relay/KarmaConstants.java` | `BLOCKS_PER_EPOCH=144`, `KARMA_ENERGY_PER_EPOCH=5`, `ENERGY_DECAY_FACTOR=0.5`, `MAX_ENERGY=10.0`, `IDENTITY_DIFFICULTY=20`, `BEP46_SALT_KARMA`, `EPOCH_TOLERANCE_BLOCKS=2`, `epochForHeight(long)`, `energyAtEpoch(double)` |
| `common/.../relay/BitcoinBlockReference.java` | Immutable `(long height, byte[32] hash)` with `epoch()`, `hashHex()` |
| `common/.../relay/IdentityKeys.java` | `ed25519()`, `ed25519PubRaw()`, `ed25519Seed()`, `ed25519SecretKeyNaCl()`, `nodeId()`, `generate()`, `loadOrCreate(File)` |
| `common/.../relay/IdentityRecord.java` | `NODE_ID_LENGTH=20`, `ED25519_PUB_LENGTH=32`, `SIGNATURE_LENGTH=64`, `extractRawEd25519(PublicKey)` |
| `common/.../relay/RelayConstants.java` | `IDENTITY_FILE="identity.dat"`, `topicHash(String)` |
| `common/.../relay/LocalIndex.java` | Storage interface for shared-torrent index |
| `common/.../relay/LocalSharedTorrent.java` | Immutable torrent row with `publisherEd25519Pub()`, `infoHashHex()` |
| `common/.../relay/IndexAnnouncementPublisher.java` | Publishes index manifests to DHT |
| `desktop/.../relay/LocalIndexTable.java` | SQLite implementation of `LocalIndex` |
| `common/src/main/java/com/frostwire/util/Hex.java` | `encode(byte[])`, `decode(String)` |
| `common/src/main/java/com/frostwire/util/Logger.java` | `Logger.getLogger(Class)` — always use this, never `System.out` |
| `common/src/main/java/com/frostwire/util/HttpClientFactory.java` | `getInstance(HttpContext.MISC)` returns `HttpClient` |
| `common/src/main/java/com/frostwire/util/http/HttpClient.java` | `String get(String url, int timeout, String userAgent, ...)` |

---

## 3. House-Style Rules (MUST follow)

1. **Logger**: `private static final Logger LOG = Logger.getLogger(MyClass.class);` — never `System.out`/`System.err`.
2. **License header**: `/* Created by Angel Leon (@gubatron) ... Licensed under GPL v3. See LICENSE file. */`
3. **Immutability**: value objects must be final with defensive `byte[].clone()` on both store and return.
4. **Null rejection**: constructors and public methods must throw `IllegalArgumentException` for null required params.
5. **Package**: all new classes go in `com.frostwire.search.relay` (in `common/src/main/java/`).
6. **Tests**: in `desktop/src/test/java/com/frostwire/search/relay/`, package-private classes, JUnit 5.
7. **No Mockito** — not on the classpath. Use hand-written fakes/stubs.
8. **No `@SuppressWarnings`** unless unavoidable and documented.
9. **Commit scope prefixes**: `[common]`, `[desktop]`, `[common/desktop]`.
10. **Thread safety**: document thread-safety in the Javadoc. Use `volatile`, `AtomicReference`, or `synchronized` as needed.
11. **try-with-resources** for any `AutoCloseable`.
12. **`AutoCloseable`** on any class that holds a database connection.

---

## 4. Build Order

Implement in this exact order. Each step produces one or two commits.

### Step 1: PoW Identity Mining

**File to modify**: `common/.../relay/IdentityKeys.java`

Add a new overload:

```java
/**
 * Generate a PoW-qualified identity. Loops until SHA-1(ed25519PubRaw)
 * has at least {@code minDifficulty} leading zero bits.
 */
public static IdentityKeys generate(int minDifficulty) throws GeneralSecurityException
```

Implementation:
1. Loop: call `KeyPairGenerator.getInstance("Ed25519").generateKeyPair()`
2. Extract raw pubkey via `IdentityRecord.extractRawEd25519(kp.getPublic())`
3. Compute `SHA-1(rawPub)` via `MessageDigest.getInstance("SHA-1")`
4. Check leading zero bits: `countLeadingZeroBits(sha1result) >= minDifficulty`
5. If yes, generate the X25519 pair and return `new IdentityKeys(ed, x)`
6. If no, continue loop

Add a package-private static helper:

```java
static int countLeadingZeroBits(byte[] hash)
```

This counts from the most significant bit of `hash[0]` down. For each byte, count
leading zeros (use `Integer.numberOfLeadingZeros(byte & 0xFF) - 24`). Stop at
first non-zero bit.

**Modify `loadOrCreate(File)`**: Change `generate()` call to `generate(KarmaConstants.IDENTITY_DIFFICULTY)`.
But also keep the zero-arg `generate()` (calls `generate(0)` for backwards compat in tests).

**Migration**: existing `identity.dat` files were generated without PoW. On load, check if the
existing identity meets difficulty. If not, log a warning but keep using it — don't force
re-mining on existing installs. PoW is enforced on new identity creation only.

**Tests** (in `IdentityKeysTest.java`):
- `countLeadingZeroBitsAllZeros` — `new byte[20]` → 160
- `countLeadingZeroBitsFirstBitSet` — `{(byte)0x80, 0, ...}` → 0
- `countLeadingZeroBitsMixedByte` — `{0x00, 0x01, ...}` → 15
- `generateWithDifficultyProducesQualifyingNodeId` — `generate(8)` then verify `countLeadingZeroBits(keys.nodeId()) >= 8`
- `generateZeroDifficultyAlwaysSucceeds` — `generate(0)` must not throw

**Commit message**: `[common] Add PoW identity mining with configurable difficulty`

---

### Step 2: BlockHeaderSource Interface

**New file**: `common/.../relay/BlockHeaderSource.java`

```java
package com.frostwire.search.relay;

/**
 * Strategy for resolving Bitcoin block hashes by height.
 * Implementations may read from a local cache, HTTP API, or
 * the Bitcoin P2P network.
 */
public interface BlockHeaderSource {
    /**
     * Returns the block reference at the given height, or null if
     * the height is unknown or the source is unavailable.
     */
    BitcoinBlockReference getBlock(long height);

    /**
     * Returns the current chain tip height, or -1 if unknown.
     */
    long getChainTipHeight();
}
```

**No tests needed** — this is just an interface.

**Commit message**: `[common] Add BlockHeaderSource interface for Bitcoin block resolution`

---

### Step 3: HttpBlockHeaderFetcher

**New file**: `common/.../relay/HttpBlockHeaderFetcher.java`

This is the HTTP multi-source fetcher with local caching.

**Constructor**: `HttpBlockHeaderFetcher(File cacheDir)`

**Cache**: a simple flat file `<cacheDir>/block-headers.cache` storing one line per
cached block: `<height> <hex-hash>\n`. Loaded into a `ConcurrentHashMap<Long, byte[]>`
on construction. Written back on each successful fetch.

Alternatively, use a tiny SQLite table in the same db directory — but a flat file is
simpler and sufficient. Use the flat file approach.

**HTTP sources** (tried in order, skip on failure, 5-second timeout per request):

| Priority | URL pattern | Response format | Parse logic |
|----------|-------------|-----------------|-------------|
| 1 | `https://blockstream.info/api/block-height/{h}` | Plain text: 64-char hex hash | Trim whitespace, decode hex, reverse byte order (Bitcoin displays big-endian, stores little-endian) |
| 2 | `https://mempool.space/api/block-height/{h}` | Plain text: 64-char hex hash | Same as above |
| 3 | `https://blockchain.info/block-height/{h}?format=json` | JSON: `{"blocks":[{"hash":"..."}]}` | Extract `blocks[0].hash`, parse as hex, reverse byte order |

**IMPORTANT**: Bitcoin block hashes are displayed in big-endian (reversed) byte order.
The hex string from the API is the display format. Store the raw 32 bytes in **display
byte order** (big-endian) since that's what we'll use in the karma chain entries for
human readability. The `BitcoinBlockReference` constructor takes raw bytes; store them
as the API returns them (after hex decoding).

**Chain tip**: `GET https://blockstream.info/api/blocks/tip/height` returns a plain text
integer. Fallback: `https://mempool.space/api/blocks/tip/height`.

**`getBlock(long height)`**:
1. Check cache. If hit, return immediately.
2. Try HTTP sources in order. On success, cache the result and return.
3. If all sources fail, return null.

**`getChainTipHeight()`**:
1. Try tip endpoints in order.
2. Parse the integer response.
3. Return the height, or -1 on failure.

**Thread safety**: `ConcurrentHashMap` for cache, `synchronized` for file writes.

**HTTP client**: use `HttpClientFactory.getInstance(HttpClientFactory.HttpContext.MISC)`.
Call `httpClient.get(url, 5000, "FrostWire/1.0", null, null, null)`. The method returns
a `String` response body. Catch `IOException` and continue to next source.

**Tests** (in `HttpBlockHeaderFetcherTest.java`):
- `getBlockReturnsCachedResult` — pre-populate cache file, verify getBlock returns it
- `getBlockReturnsNullForUnknownHeight` — empty cache, no HTTP (mock not available, so
  just test with a height far in the future like 99999999 that no API will have; or
  better: use a cache-only test by writing a known entry and reading it back)
- `cacheFileRoundTrips` — write cache, create new instance from same dir, verify it loads
- `getChainTipHeightReturnsNegativeOneOnFailure` — with no network (just verify the return
  type contract; actual HTTP testing is a manual/integration concern)
- `constructorRejectsNullCacheDir`
- `constructorCreatesCacheDirIfMissing`

**Note**: Do NOT write tests that make real HTTP calls to blockchain APIs. Tests must be
hermetic. The HTTP paths are tested manually or via integration tests.

**Commit message**: `[common] Add HttpBlockHeaderFetcher with multi-source Bitcoin header resolution`

---

### Step 4: KarmaChainEntry

**New file**: `common/.../relay/KarmaChainEntry.java`

This is the signed, hash-linked record in the karma chain. There are two kinds of
entries: epoch commitments and endorsements.

```java
public final class KarmaChainEntry {

    public enum Kind { EPOCH_COMMITMENT, ENDORSEMENT }

    // -- Common fields (all entries) --
    private final Kind kind;
    private final byte[] prevHash;       // SHA-256 of previous entry's canonical bytes; zeros for genesis
    private final long seq;              // 0-based monotonic index in the chain
    private final byte[] endorserPub;    // 32-byte raw Ed25519 pub of the chain owner
    private final long timestamp;        // Unix epoch seconds (informational, not authoritative)
    private final byte[] signature;      // 64-byte Ed25519 signature

    // -- Bitcoin anchor (all entries) --
    private final long blockHeight;      // Bitcoin block height at time of creation
    private final byte[] blockHash;      // 32-byte Bitcoin block hash

    // -- Epoch commitment fields (only when kind == EPOCH_COMMITMENT) --
    private final long epoch;            // karma epoch number (blockHeight / BLOCKS_PER_EPOCH)
    private final double energy;         // available energy at this epoch (with decay)

    // -- Endorsement fields (only when kind == ENDORSEMENT) --
    private final byte[] peerPub;        // 32-byte raw Ed25519 pub of the endorsed peer (null for EPOCH_COMMITMENT)
    private final byte[] infoHash;       // 20-byte torrent info hash that triggered the endorsement (null for EPOCH_COMMITMENT)
    private final int scoreDelta;        // +1 for positive endorsement; -1 reserved for future spam marking
}
```

**Constructor**: private. Use static factory methods:

```java
public static KarmaChainEntry createEpochCommitment(
        byte[] prevHash, long seq, byte[] endorserPub,
        BitcoinBlockReference block, double energy,
        PrivateKey signingKey)

public static KarmaChainEntry createEndorsement(
        byte[] prevHash, long seq, byte[] endorserPub,
        BitcoinBlockReference block,
        byte[] peerPub, byte[] infoHash, int scoreDelta,
        PrivateKey signingKey)
```

Both factory methods:
1. Validate all fields (lengths, non-null, etc.)
2. Build the canonical bytes (see below)
3. Sign with `java.security.Signature.getInstance("Ed25519")`
4. Return the immutable entry

**Canonical bytes** for signing: bencode a `TreeMap` with all fields except `sig`,
using the same pattern as `IdentityRecord.canonicalBytes()`. Keys in sorted order
(TreeMap guarantees this):

```
{
  "bh": <block_height as integer>,
  "bk": <block_hash as hex string>,
  "e":  <energy as string "5.00" — only for EPOCH_COMMITMENT>,
  "ep": <epoch as integer — only for EPOCH_COMMITMENT>,
  "ih": <info_hash hex — only for ENDORSEMENT>,
  "k":  <kind as string "EC" or "EN">,
  "ph": <prev_hash hex>,
  "pp": <peer_pub base64 — only for ENDORSEMENT>,
  "pub": <endorser_pub base64>,
  "sd": <score_delta — only for ENDORSEMENT>,
  "seq": <seq as integer>,
  "ts": <timestamp as integer>
}
```

Use `Entry.fromMap(TreeMap)` then `.bencode()` for canonical bytes. This is the
same pattern used in `IdentityRecord`.

**Accessors**: standard getters with defensive `byte[].clone()` on all byte arrays.

**`verifySignature()`**: reconstruct canonical bytes, verify with `endorserPub`.
Same pattern as `IdentityRecord.verifySignature()`.

**`canonicalBytes()`**: public, returns the bencoded canonical form.

**`entryHash()`**: `SHA-256(canonicalBytes())` — this is what the next entry's
`prevHash` references.

**GENESIS_PREV_HASH**: `public static final byte[] GENESIS_PREV_HASH = new byte[32]` — all zeros.

**Tests** (in `KarmaChainEntryTest.java`):
- `createEpochCommitmentProducesValidSignature`
- `createEndorsementProducesValidSignature`
- `canonicalBytesAreStableAndDeterministic` — create same entry twice with same params, verify identical bytes
- `entryHashIsSha256OfCanonicalBytes`
- `prevHashLinksEntries` — create entry 0, then entry 1 with `prevHash = entry0.entryHash()`, verify chain
- `verifySignatureRejectsTamperedEntry` — modify a field, re-serialize, verify returns false
- `createEpochCommitmentRejectsInvalidInputs` — null pubkey, wrong-length hash, etc.
- `createEndorsementRejectsInvalidInputs`
- `defensiveCopiesOnAllByteArrays`
- `genesisEntryHasZeroPrevHash`

**Commit message**: `[common] Add KarmaChainEntry with Bitcoin-anchored signed records`

---

### Step 5: KarmaChain

**New file**: `common/.../relay/KarmaChain.java`

This is the in-memory representation of a node's karma chain with energy tracking
and budget enforcement.

```java
public final class KarmaChain {

    private final byte[] ownerPub;           // 32-byte Ed25519 pub
    private final List<KarmaChainEntry> entries;  // append-only, in order
    private long currentEpoch;
    private double currentEnergy;
    private int endorsementsThisEpoch;
}
```

**Constructor**: `KarmaChain(byte[] ownerPub)` — creates an empty chain.

**Key methods**:

```java
/** Returns a read-only view of all entries. */
public List<KarmaChainEntry> entries()

/** Returns the head entry, or null if the chain is empty. */
public KarmaChainEntry head()

/** Returns the hash that the next entry's prevHash must reference. */
public byte[] headHash()

/** Returns the next sequence number. */
public long nextSeq()

/**
 * Start a new epoch. Must be called before any endorsements in that epoch.
 * Verifies the block is in a new epoch relative to the last commitment.
 * Computes energy with decay from the previous epoch.
 * Appends an EPOCH_COMMITMENT entry to the chain.
 * Returns the appended entry.
 */
public KarmaChainEntry commitEpoch(BitcoinBlockReference block, PrivateKey signingKey)

/**
 * Endorse a peer for a completed download.
 * Verifies energy budget (throws IllegalStateException if exhausted).
 * Verifies an epoch commitment exists for the current epoch.
 * Appends an ENDORSEMENT entry to the chain.
 * Returns the appended entry.
 */
public KarmaChainEntry endorse(byte[] peerPub, byte[] infoHash,
                               BitcoinBlockReference currentBlock,
                               PrivateKey signingKey)

/**
 * Returns the available energy in the current epoch.
 */
public double availableEnergy()

/**
 * Validates the entire chain: hash links, signatures, epoch ordering,
 * energy budgets. Returns true if valid. Used when loading a peer's
 * published chain.
 */
public static boolean verify(List<KarmaChainEntry> entries)
```

**Energy tracking**:
- On `commitEpoch()`: `currentEnergy = KarmaConstants.energyAtEpoch(previousRemaining)` where
  `previousRemaining = currentEnergy - endorsementsThisEpoch`. Reset `endorsementsThisEpoch = 0`.
- On `endorse()`: check `endorsementsThisEpoch < floor(currentEnergy)`. Increment counter.
- `availableEnergy()`: return `floor(currentEnergy) - endorsementsThisEpoch`.

**`verify(List<KarmaChainEntry> entries)`** — static validation of a foreign chain:
1. First entry's `prevHash` must be `GENESIS_PREV_HASH`.
2. Each entry's `prevHash` must equal `SHA-256(previousEntry.canonicalBytes())`.
3. Each entry's `seq` must equal its index in the list.
4. Each entry's `endorserPub` must be the same across all entries.
5. Each entry's signature must verify.
6. Epoch commitments must appear in monotonically increasing epoch order.
7. Endorsements in an epoch must appear after that epoch's commitment.
8. Endorsement count per epoch must not exceed `floor(energyAtThatEpoch)`.
9. Block heights must be monotonically non-decreasing.

**Thread safety**: `synchronized` on all mutating methods. `entries()` returns
`Collections.unmodifiableList(entries)`.

**Tests** (in `KarmaChainTest.java`):
- `emptyChainHasNoHeadAndZeroNextSeq`
- `commitEpochAppendsEpochCommitment`
- `endorseAppendsEndorsement`
- `endorseThrowsIfNoEpochCommitted`
- `endorseThrowsIfEnergyExhausted` — commit epoch, endorse 5 times (budget=5), 6th throws
- `energyDecaysAcrossEpochs` — commit epoch 0, use 3 of 5 energy, commit epoch 1: energy = 5 + 2*0.5 = 6.0
- `verifyAcceptsValidChain`
- `verifyRejectsBrokenHashLink`
- `verifyRejectsWrongSequence`
- `verifyRejectsOverBudget`
- `verifyRejectsMixedOwners` — two entries with different endorserPub
- `verifyRejectsEndorsementBeforeEpochCommitment`

**Commit message**: `[common] Add KarmaChain with energy decay and budget enforcement`

---

### Step 6: KarmaChainTable (SQLite persistence)

**New file**: `desktop/.../relay/KarmaChainTable.java`

**Package**: `com.frostwire.search.relay` (in `desktop/src/main/java/`)

**Purpose**: persists the local node's karma chain to SQLite and provides
aggregate queries for peer karma scores.

**Database**: stored in the same SQLite file as `LocalIndexTable`
(`frostwire-shared-torrents.db`). Add new tables to the existing db.

**HOWEVER**: `LocalIndexTable` currently owns its own `Connection`. To share
the db, the cleanest approach is: `KarmaChainTable` opens its own connection
to the same file path. SQLite supports multiple connections via WAL mode
(already configured by `LocalIndexTable`).

**Schema**:

```sql
CREATE TABLE IF NOT EXISTS karma_chain (
    seq              INTEGER PRIMARY KEY,
    kind             TEXT NOT NULL,      -- 'EC' or 'EN'
    prev_hash        TEXT NOT NULL,      -- hex
    endorser_pub     TEXT NOT NULL,      -- base64
    timestamp        INTEGER NOT NULL,
    block_height     INTEGER NOT NULL,
    block_hash       TEXT NOT NULL,      -- hex
    epoch            INTEGER,            -- only for EC
    energy           REAL,               -- only for EC
    peer_pub         TEXT,               -- only for EN, base64
    info_hash        TEXT,               -- only for EN, hex
    score_delta      INTEGER,            -- only for EN
    signature        TEXT NOT NULL,       -- base64
    canonical_bytes  BLOB NOT NULL        -- for hash verification
);

CREATE TABLE IF NOT EXISTS peer_karma (
    peer_pub         TEXT PRIMARY KEY,    -- base64
    total_score      INTEGER NOT NULL DEFAULT 0,
    endorsement_count INTEGER NOT NULL DEFAULT 0,
    last_endorsed_at INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_karma_chain_epoch ON karma_chain(epoch);
CREATE INDEX IF NOT EXISTS idx_karma_chain_block ON karma_chain(block_height);
CREATE INDEX IF NOT EXISTS idx_peer_karma_score ON peer_karma(total_score);
```

**Constructor**: `KarmaChainTable(File dbFile)` — same pattern as `LocalIndexTable`.

**Implements `AutoCloseable`**.

**Key methods**:

```java
/** Append a chain entry. Also updates peer_karma if it's an endorsement. */
public void append(KarmaChainEntry entry)

/** Load the full chain into a KarmaChain. Used on startup. */
public KarmaChain loadChain(byte[] ownerPub)

/** Get the aggregate karma score for a peer. */
public long getPeerKarma(byte[] peerPub)

/** Get all peers sorted by total_score descending. */
public List<PeerKarmaScore> getTopPeers(int limit)

/** Get endorsement count in a given epoch. */
public int endorsementCountInEpoch(long epoch)
```

**`PeerKarmaScore`**: simple immutable holder `(byte[] peerPub, long totalScore, int endorsementCount, long lastEndorsedAt)`.

**Tests** (in `KarmaChainTableTest.java`):
- `appendAndLoadRoundTrips`
- `appendUpdatesAggregateScore`
- `getTopPeersReturnsDescending`
- `endorsementCountInEpochIsAccurate`
- `operationsAfterCloseThrow`
- `tryWithResourcesClosesTable`

Use temp directory for test database, same pattern as `LocalIndexTableTest`.

**Commit message**: `[desktop] Add KarmaChainTable for karma chain persistence and peer scoring`

---

### Step 7: Download Completion Credit Hook

**File to modify**: `common/.../relay/SharedTorrentIndexer.java`

The `SharedTorrentIndexer` already receives `downloadUpdate` events. When a download
transitions to a completed state, it should trigger a karma endorsement for the peer
that published the torrent (if the torrent came from a peer, not from ourselves).

**Add a new interface**: `KarmaEndorsementSink`

**New file**: `common/.../relay/KarmaEndorsementSink.java`

```java
package com.frostwire.search.relay;

/**
 * Callback for the karma endorsement trigger. The indexer fires this
 * when a download completes and the torrent was published by a peer
 * (non-zero publisher Ed25519 key that differs from our own).
 */
public interface KarmaEndorsementSink {
    void onDownloadCompletedFromPeer(byte[] peerEd25519Pub, byte[] infoHash);
}
```

**Modify `SharedTorrentIndexer`**:
- Add a field: `private volatile KarmaEndorsementSink karmaSink;`
- Add setter: `public void setKarmaSink(KarmaEndorsementSink sink)`
- In `downloadUpdate()`, after indexing: check if the download's state indicates
  completion. Since `BTDownload.getState()` isn't available to us in `common/`
  without a libtorrent dependency concern, use the existing seam pattern: add a
  `DownloadCompletionSource` interface (similar to `TorrentInfoSource`).

Actually, simpler approach: don't modify `SharedTorrentIndexer`. Instead, create a
**separate `BTEngineListener`** that only watches for download completion.

**New file**: `common/.../relay/KarmaEndorsementTrigger.java`

```java
public final class KarmaEndorsementTrigger implements BTEngineListener {

    private final LocalIndex index;
    private final byte[] ownEd25519Pub;  // our own pubkey, to exclude self-endorsement
    private final KarmaEndorsementSink sink;

    public KarmaEndorsementTrigger(LocalIndex index, byte[] ownEd25519Pub,
                                   KarmaEndorsementSink sink)
```

In `downloadUpdate(BTEngine engine, BTDownload dl)`:
1. Check if `dl.isComplete()` (or `dl.getProgress() == 100` — use whichever method exists on `BTDownload`).
2. Get the info hash: `dl.getInfoHash()`.
3. Look up the torrent in `LocalIndex.get(infoHashHex)`.
4. If found and `publisherEd25519Pub` is non-zero and differs from `ownEd25519Pub`, call `sink.onDownloadCompletedFromPeer(publisherPub, infoHash)`.
5. Guard against duplicate triggers by keeping a `Set<String>` of already-credited info hashes (use `ConcurrentHashMap.newKeySet()`).

**CHECK FIRST**: examine `BTDownload` to find the right completion check method. Look at
`com.frostwire.bittorrent.BTDownload` for `isComplete()`, `isFinished()`, `getState()`,
or `getProgress()`. Use whatever exists. If `getState()` returns a `TransferState` enum,
check for `TransferState.SEEDING` or `TransferState.FINISHED`.

This trigger is installed via `BTEngineListenerChain.install()` in `startRelayStack()`,
same as the indexer.

**Tests** (in `KarmaEndorsementTriggerTest.java`):
- These tests are hard to write without Mockito and without real BTDownload instances.
  Write tests for the logic that doesn't depend on BTDownload:
  - `constructorRejectsNulls`
  - `excludesSelfEndorsement` — test the check logic with a recording sink
  - `excludesZeroPubkey` — test with all-zeros publisher

For the integration with BTDownload, rely on the manual smoke test path.

**Commit message**: `[common] Add KarmaEndorsementTrigger for download-completion karma credit`

---

## 5. File Manifest

### New files to create:

| # | Path | Description |
|---|------|-------------|
| 1 | `common/.../relay/BlockHeaderSource.java` | Interface |
| 2 | `common/.../relay/HttpBlockHeaderFetcher.java` | HTTP multi-source + cache |
| 3 | `common/.../relay/KarmaChainEntry.java` | Signed hash-linked record |
| 4 | `common/.../relay/KarmaChain.java` | In-memory chain with energy tracking |
| 5 | `common/.../relay/KarmaEndorsementSink.java` | Callback interface |
| 6 | `common/.../relay/KarmaEndorsementTrigger.java` | BTEngineListener for download completion |
| 7 | `desktop/.../relay/KarmaChainTable.java` | SQLite persistence |
| 8 | `desktop/src/test/.../relay/KarmaChainEntryTest.java` | Tests |
| 9 | `desktop/src/test/.../relay/KarmaChainTest.java` | Tests |
| 10 | `desktop/src/test/.../relay/KarmaChainTableTest.java` | Tests |
| 11 | `desktop/src/test/.../relay/HttpBlockHeaderFetcherTest.java` | Tests |
| 12 | `desktop/src/test/.../relay/KarmaEndorsementTriggerTest.java` | Tests |

### Files to modify:

| # | Path | Change |
|---|------|--------|
| 1 | `common/.../relay/IdentityKeys.java` | Add `generate(int minDifficulty)`, `countLeadingZeroBits()` |
| 2 | `desktop/src/test/.../relay/IdentityKeysTest.java` | Add PoW tests |

---

## 6. Test Execution

All tests run via:

```bash
cd desktop && ./gradlew test --tests 'com.frostwire.search.relay.*' \
    --tests 'com.frostwire.tests.dht.*' \
    --tests 'com.frostwire.tests.relay.*' --rerun-tasks
```

Current baseline: **100 PASSED, 0 FAILED**. After implementation, expect ~130+ tests, 0 failures.

---

## 7. Verification Checklist

Before declaring done, verify:

- [ ] `./gradlew compileJava` succeeds in `desktop/`
- [ ] All tests pass (0 failures)
- [ ] No `System.out`/`System.err` in any new code
- [ ] No `@SuppressWarnings` without justification
- [ ] All byte array fields are defensively cloned
- [ ] All constructors reject null required params
- [ ] Logger uses `Logger.getLogger(ClassName.class)` pattern
- [ ] License header on every new file
- [ ] Each commit has the scope prefix and imperative-mood message
- [ ] `identity.dat` migration: existing files without PoW are accepted on load
- [ ] `HttpBlockHeaderFetcher` tests do NOT make real HTTP calls

---

## 8. What This Spec Does NOT Cover (Future Work)

- Wiring `KarmaEndorsementTrigger` into `Initializer.startRelayStack()` (do this after review)
- Publishing the karma chain to DHT via BEP 46
- Fetching and verifying remote peer karma chains
- Trust-graph traversal and scoring for search results
- Bitcoin P2P network fallback for block headers
- Negative karma (spam marking) UI and trigger
- `PeerDirectory` and `PeerSearchPerformer`
