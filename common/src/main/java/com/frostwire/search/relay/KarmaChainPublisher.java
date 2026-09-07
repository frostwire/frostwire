/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.util.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Publishes the most recent karma chain entries as a BEP 46 mutable
 * DHT item, signed with the node's Ed25519 key under
 * {@link KarmaConstants#BEP46_SALT_KARMA}.
 *
 * <p>Manifest format (bencoded):
 * <pre>
 * {
 *   "v":       1,
 *   "pub":     "base64url raw Ed25519 pub",
 *   "len":     total chain length (seq + 1),
 *   "head":    "hex entryHash of the last entry in the chain",
 *   "entries": [ { "k":"EC"|"EN", "seq":n, "bh":h, "bkh":"hex", "ph":"hex",
 *                  "ep":e?, "en":e?, "pp":"b64"?, "ih":"hex"?,
 *                  "sd":d?, "s":"b64" } ... ]
 *   "base":    { "seq": seq of the first included entry,
 *                "ph":  "hex entryHash of the entry before it (genesis if from start)" },
 *   "ts":      epoch seconds
 * }
 * </pre>
 *
 * <p>BEP 44 mutable items are capped at ~1000 bytes. A full chain
 * does not fit, so the publisher includes the most recent entries
 * (tail) up to the size limit. The head entry is always included.
 * Peers that need the full chain use {@code len} and {@code head}
 * to detect divergence and request missing entries out of band.
 *
 * <p>Thread-safe: stateless aside from the in-memory chain it reads.
 * The chain is itself synchronized, so this publisher can be called
 * from any thread.
 */
public final class KarmaChainPublisher {

    private static final Logger LOG = Logger.getLogger(KarmaChainPublisher.class);

    /** BEP 44 mutable item value size limit (conservative). */
    static final int MAX_MANIFEST_BYTES = 950;

    /**
     * Minimum chain size worth publishing: one epoch commitment plus one
     * endorsement, the smallest unit that verifies as a complete chain.
     * Smaller chains stay local until they grow; publishing a lone
     * endorsement would force receivers to trust an unanchored tail.
     */
    static final int MIN_PUBLISH_CHAIN_SIZE = 2;

    static final int MANIFEST_VERSION = 1;

    private final KarmaChainWriter writer;
    private final IdentityKeys identity;

    public KarmaChainPublisher(KarmaChainWriter writer, IdentityKeys identity) {
        if (writer == null) {
            throw new IllegalArgumentException("writer is null");
        }
        if (identity == null) {
            throw new IllegalArgumentException("identity is null");
        }
        this.writer = writer;
        this.identity = identity;
    }

    /**
     * Build and publish the karma chain tail. Returns the number of
     * entries included in the published manifest, or 0 if nothing
     * was published (empty chain or DHT not available).
     */
    public int publishIfNeeded(SessionManager session) {
        if (session == null) {
            return 0;
        }
        try {
            List<KarmaChainEntry> chain = writer.chain().entries();
            if (chain.isEmpty()) {
                return 0;
            }
            Entry manifest = buildManifest(chain);
            if (manifest == null) {
                return 0;
            }
            if (manifest.bencode().length > MAX_MANIFEST_BYTES) {
                LOG.warn("Karma manifest exceeds DHT value limit; not publishing");
                return 0;
            }
            byte[] pubKey = identity.ed25519PubRaw();
            byte[] privKey = identity.ed25519SecretKeyNaCl();
            byte[] salt = KarmaConstants.BEP46_SALT_KARMA.getBytes(StandardCharsets.US_ASCII);
            session.dhtPutItem(pubKey, privKey, manifest, salt);

            int count = manifest.dictionary().get("entries").list().size();
            LOG.info("Published karma chain tail with " + count +
                    " of " + chain.size() + " entries");
            return count;
        } catch (Throwable t) {
            LOG.warn("Failed to publish karma chain", t);
            return 0;
        }
    }

    /**
     * Build the bencoded manifest entry, including as many tail
     * entries as fit within {@link #MAX_MANIFEST_BYTES}. Returns
     * null if the chain is empty.
     */
    Entry buildManifest(List<KarmaChainEntry> chain) {
        if (chain == null || chain.isEmpty()) {
            return null;
        }

        // Try with the full chain, then shrink from the front (oldest
        // entries drop first) until the bencoded value fits. Tails stay
        // anchored: trimming stops at an epoch commitment, and chains too
        // small to verify are kept local until they grow.
        List<KarmaChainEntry> included = new ArrayList<>(chain);
        while (included.size() >= MIN_PUBLISH_CHAIN_SIZE) {
            Entry entry = buildManifestEntry(included, chain);
            byte[] bencoded = entry.bencode();
            if (bencoded.length <= MAX_MANIFEST_BYTES) {
                return entry;
            }
            included.remove(0);
            while (included.size() >= MIN_PUBLISH_CHAIN_SIZE
                    && included.get(0).kind() != KarmaChainEntry.Kind.EPOCH_COMMITMENT) {
                included.remove(0);
            }
        }
        // If even the smallest publishable unit (EC + 1 EN) exceeds
        // exceeds the DHT value cap: keep the chain local rather than
        // emit an unverifiable fragment.
        return null;
    }

    private Entry buildManifestEntry(List<KarmaChainEntry> tail,
                                     List<KarmaChainEntry> fullChain) {
        List<Entry> entryEntries = new ArrayList<>(tail.size());
        for (KarmaChainEntry e : tail) {
            entryEntries.add(buildEntryDict(e));
        }

        Map<String, Object> map = new TreeMap<>();
        map.put("v", new Entry((long) MANIFEST_VERSION));
        map.put("pub", new Entry(Base64.getUrlEncoder()
                .withoutPadding().encodeToString(identity.ed25519PubRaw())));
        map.put("len", new Entry((long) fullChain.size()));
        map.put("head", new Entry(com.frostwire.util.Hex.encode(
                writer.chain().headHash())));
        map.put("entries", Entry.fromList(entryEntries));
        // Anchor for verifyTail: only tails trimmed from a longer chain need
        // it. A complete chain (first entry seq 0 with genesis prevHash)
        // verifies with verify() and carries no base — saving ~87 bytes,
        // which is exactly the margin a minimal EC+EN manifest needs.
        // The manifest-level "pub" already carries the owner's key, so
        // per-entry "pub" copies are redundant: drop them (~50 bytes each)
        // and let reconstruct() default endorserPub to the manifest owner.
        KarmaChainEntry first = tail.get(0);
        if (first.seq() != 0
                || !java.util.Arrays.equals(
                        first.prevHash(), KarmaChainEntry.GENESIS_PREV_HASH)) {
            Map<String, Object> base = new TreeMap<>();
            base.put("seq", new Entry(first.seq()));
            base.put("ph", new Entry(com.frostwire.util.Hex.encode(first.prevHash())));
            map.put("base", Entry.fromMap(base));
        }
        map.put("ts", new Entry(Instant.now().getEpochSecond()));
        return Entry.fromMap(map);
    }

    private Entry buildEntryDict(KarmaChainEntry e) {
        Map<String, Object> m = new TreeMap<>();
        m.put("k", new Entry(e.kind().code()));
        m.put("seq", new Entry(e.seq()));
        m.put("bh", new Entry(e.blockHeight()));
        m.put("bkh", new Entry(com.frostwire.util.Hex.encode(e.blockHash())));
        m.put("ph", new Entry(com.frostwire.util.Hex.encode(e.prevHash())));
        Long epoch = e.epoch();
        if (epoch != null) {
            m.put("ep", new Entry(epoch));
        }
        Double energy = e.energy();
        if (energy != null) {
            m.put("en", new Entry(formatEnergy(energy)));
        }
        byte[] peerPub = e.peerPub();
        if (peerPub != null) {
            m.put("pp", new Entry(Base64.getUrlEncoder()
                    .withoutPadding().encodeToString(peerPub)));
        }
        byte[] infoHash = e.infoHash();
        if (infoHash != null) {
            m.put("ih", new Entry(com.frostwire.util.Hex.encode(infoHash)));
        }
        // Score deltas are always 1 in this protocol version; the reader
        // defaults a missing field to 1. Omitting it saves ~7 bytes per
        // endorsement — the margin a minimal EC+EN manifest needs.
        Integer scoreDelta = e.scoreDelta();
        if (scoreDelta != null && scoreDelta != 1) {
            m.put("sd", new Entry(scoreDelta.longValue()));
        }
        m.put("s", new Entry(Base64.getUrlEncoder()
                .withoutPadding().encodeToString(e.signature())));
        return Entry.fromMap(m);
    }

    private static String formatEnergy(double energy) {
        // Decimal string for human readability; the energy model is
        // deterministic so this field is convenience data, not authoritative.
        return String.format(java.util.Locale.ROOT, "%.3f", energy);
    }
}
