/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import com.frostwire.jlibtorrent.Entry;
import com.frostwire.util.Hex;
import com.frostwire.util.Logger;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

/**
 * A single entry in a peer's karma chain. Two kinds: epoch
 * commitments (declare energy budget) and endorsements (credit
 * a peer for a download).
 *
 * <p>Each entry is:
 * <ul>
 *   <li>Hash-linked to the previous entry via {@code prevHash} (SHA-256 of the previous entry's canonical bytes)</li>
 *   <li>Anchored to a Bitcoin block ({@code blockHeight} + {@code blockHash})</li>
 *   <li>Signed with the chain owner's Ed25519 key</li>
 * </ul>
 *
 * <p>Genesis entries use {@link #GENESIS_PREV_HASH} (32 zero bytes).
 */
public final class KarmaChainEntry {

    public enum Kind {
        EPOCH_COMMITMENT("EC"),
        ENDORSEMENT("EN");

        final String code;

        Kind(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Sentinel prevHash for the first entry in a chain (32 zero bytes). */
    public static final byte[] GENESIS_PREV_HASH = new byte[32];

    private static final Logger LOG = Logger.getLogger(KarmaChainEntry.class);

    private final Kind kind;
    private final byte[] prevHash;
    private final long seq;
    private final byte[] endorserPub;
    private final long timestamp;
    private final byte[] signature;
    private final long blockHeight;
    private final byte[] blockHash;

    // Epoch commitment fields (null/0 for ENDORSEMENT)
    private final Long epoch;
    private final Double energy;

    // Endorsement fields (null for EPOCH_COMMITMENT)
    private final byte[] peerPub;
    private final byte[] infoHash;
    private final Integer scoreDelta;

    private KarmaChainEntry(Builder b) {
        this.kind = b.kind;
        this.prevHash = b.prevHash.clone();
        this.seq = b.seq;
        this.endorserPub = b.endorserPub.clone();
        this.timestamp = b.timestamp;
        this.signature = b.signature.clone();
        this.blockHeight = b.blockHeight;
        this.blockHash = b.blockHash.clone();
        this.epoch = b.epoch;
        this.energy = b.energy;
        this.peerPub = b.peerPub == null ? null : b.peerPub.clone();
        this.infoHash = b.infoHash == null ? null : b.infoHash.clone();
        this.scoreDelta = b.scoreDelta;
    }

    /**
     * Create and sign an epoch commitment entry.
     *
     * @param prevHash     SHA-256 of the previous entry's canonical bytes (or GENESIS_PREV_HASH)
     * @param seq          0-based monotonic sequence number
     * @param endorserPub  32-byte raw Ed25519 pubkey of the chain owner
     * @param block        Bitcoin block reference for this epoch
     * @param energy       available energy at this epoch (with decay)
     * @param signingKey   chain owner's Ed25519 PrivateKey
     */
    public static KarmaChainEntry createEpochCommitment(
            byte[] prevHash, long seq, byte[] endorserPub,
            BitcoinBlockReference block, double energy,
            PrivateKey signingKey) {
        if (prevHash == null || prevHash.length != 32) {
            throw new IllegalArgumentException("prevHash must be 32 bytes");
        }
        if (endorserPub == null || endorserPub.length != 32) {
            throw new IllegalArgumentException("endorserPub must be 32 bytes");
        }
        if (block == null) {
            throw new IllegalArgumentException("block is null");
        }
        if (signingKey == null) {
            throw new IllegalArgumentException("signingKey is null");
        }
        if (energy < 0 || energy > KarmaConstants.MAX_ENERGY) {
            throw new IllegalArgumentException("energy must be in [0, " + KarmaConstants.MAX_ENERGY + "]");
        }

        Builder b = new Builder();
        b.kind = Kind.EPOCH_COMMITMENT;
        b.prevHash = prevHash;
        b.seq = seq;
        b.endorserPub = endorserPub;
        b.timestamp = Instant.now().getEpochSecond();
        b.blockHeight = block.height();
        b.blockHash = block.hash();
        b.epoch = KarmaConstants.epochForHeight(block.height());
        b.energy = energy;

        b.canonicalBytesForSigning();
        b.signature = sign(b.canonicalBytes, signingKey);
        return b.build();
    }

    /**
     * Create and sign an endorsement entry.
     */
    public static KarmaChainEntry createEndorsement(
            byte[] prevHash, long seq, byte[] endorserPub,
            BitcoinBlockReference block,
            byte[] peerPub, byte[] infoHash, int scoreDelta,
            PrivateKey signingKey) {
        if (prevHash == null || prevHash.length != 32) {
            throw new IllegalArgumentException("prevHash must be 32 bytes");
        }
        if (endorserPub == null || endorserPub.length != 32) {
            throw new IllegalArgumentException("endorserPub must be 32 bytes");
        }
        if (block == null) {
            throw new IllegalArgumentException("block is null");
        }
        if (peerPub == null || peerPub.length != 32) {
            throw new IllegalArgumentException("peerPub must be 32 bytes");
        }
        if (infoHash == null || infoHash.length != 20) {
            throw new IllegalArgumentException("infoHash must be 20 bytes");
        }
        if (signingKey == null) {
            throw new IllegalArgumentException("signingKey is null");
        }

        Builder b = new Builder();
        b.kind = Kind.ENDORSEMENT;
        b.prevHash = prevHash;
        b.seq = seq;
        b.endorserPub = endorserPub;
        b.timestamp = Instant.now().getEpochSecond();
        b.blockHeight = block.height();
        b.blockHash = block.hash();
        b.peerPub = peerPub;
        b.infoHash = infoHash;
        b.scoreDelta = scoreDelta;

        b.canonicalBytesForSigning();
        b.signature = sign(b.canonicalBytes, signingKey);
        return b.build();
    }

    public Kind kind() {
        return kind;
    }

    public byte[] prevHash() {
        return prevHash.clone();
    }

    public long seq() {
        return seq;
    }

    public byte[] endorserPub() {
        return endorserPub.clone();
    }

    public long timestamp() {
        return timestamp;
    }

    public byte[] signature() {
        return signature.clone();
    }

    public long blockHeight() {
        return blockHeight;
    }

    public byte[] blockHash() {
        return blockHash.clone();
    }

    public Long epoch() {
        return epoch;
    }

    public Double energy() {
        return energy;
    }

    public byte[] peerPub() {
        return peerPub == null ? null : peerPub.clone();
    }

    public byte[] infoHash() {
        return infoHash == null ? null : infoHash.clone();
    }

    public Integer scoreDelta() {
        return scoreDelta;
    }

    /**
     * Returns the canonical bencoded form (excluding signature).
     * Same input always produces the same bytes — TreeMap guarantees
     * sorted key order.
     */
    public byte[] canonicalBytes() {
        Builder b = new Builder();
        b.kind = this.kind;
        b.prevHash = this.prevHash;
        b.seq = this.seq;
        b.endorserPub = this.endorserPub;
        b.timestamp = this.timestamp;
        b.blockHeight = this.blockHeight;
        b.blockHash = this.blockHash;
        b.epoch = this.epoch;
        b.energy = this.energy;
        b.peerPub = this.peerPub;
        b.infoHash = this.infoHash;
        b.scoreDelta = this.scoreDelta;
        b.canonicalBytesForSigning();
        return b.canonicalBytes.clone();
    }

    /**
     * SHA-256 of {@link #canonicalBytes()}. This is what the next
     * entry's {@code prevHash} must reference.
     */
    public byte[] entryHash() {
        return sha256(canonicalBytes());
    }

    /**
     * Verifies the Ed25519 signature against the canonical bytes.
     */
    public boolean verifySignature() {
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(rawEd25519ToPublicKey(endorserPub));
            verifier.update(canonicalBytes());
            return verifier.verify(signature);
        } catch (Exception e) {
            LOG.debug("Signature verification failed", e);
            return false;
        }
    }

    private static byte[] sign(byte[] data, PrivateKey key) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(data);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign karma chain entry", e);
        }
    }

    /**
     * Reconstructs an Ed25519 public key from its raw 32-byte form.
     * The X.509 SubjectPublicKeyInfo prefix for Ed25519 is
     * 30 2a 30 05 06 03 2b 65 70 03 21 00 (12 bytes).
     */
    private static PublicKey rawEd25519ToPublicKey(byte[] raw) throws GeneralSecurityException {
        byte[] prefix = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
        byte[] encoded = new byte[prefix.length + raw.length];
        System.arraycopy(prefix, 0, encoded, 0, prefix.length);
        System.arraycopy(raw, 0, encoded, prefix.length, raw.length);
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KarmaChainEntry)) return false;
        KarmaChainEntry that = (KarmaChainEntry) o;
        return seq == that.seq
                && Arrays.equals(signature, that.signature)
                && Arrays.equals(prevHash, that.prevHash);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(seq) + Arrays.hashCode(signature);
    }

    @Override
    public String toString() {
        return "KarmaChainEntry{kind=" + kind + ", seq=" + seq +
                ", blockHeight=" + blockHeight + "}";
    }

    private static final class Builder {
        Kind kind;
        byte[] prevHash;
        long seq;
        byte[] endorserPub;
        long timestamp;
        byte[] signature = new byte[64];
        long blockHeight;
        byte[] blockHash;
        Long epoch;
        Double energy;
        byte[] peerPub;
        byte[] infoHash;
        Integer scoreDelta;
        byte[] canonicalBytes;

        void canonicalBytesForSigning() {
            Map<String, Object> map = new TreeMap<>();
            map.put("bh", new Entry(blockHeight));
            map.put("bk", new Entry(Hex.encode(blockHash)));
            map.put("k", new Entry(kind.code));
            map.put("ph", new Entry(Hex.encode(prevHash)));
            map.put("pub", new Entry(Base64.getEncoder().withoutPadding().encodeToString(endorserPub)));
            map.put("seq", new Entry(seq));
            // Note: timestamp is NOT included in the signed canonical form
            // so that two entries created at the same logical state produce
            // identical signatures. Timestamp is informational and part of
            // the public record but not part of the integrity contract.

            if (kind == Kind.EPOCH_COMMITMENT) {
                map.put("e", new Entry(String.format(java.util.Locale.ROOT, "%.2f", energy)));
                map.put("ep", new Entry(epoch));
            } else { // ENDORSEMENT
                map.put("ih", new Entry(Hex.encode(infoHash)));
                map.put("pp", new Entry(Base64.getEncoder().withoutPadding().encodeToString(peerPub)));
                map.put("sd", new Entry(scoreDelta));
            }

            this.canonicalBytes = Entry.fromMap(map).bencode();
        }

        KarmaChainEntry build() {
            return new KarmaChainEntry(this);
        }
    }
}
