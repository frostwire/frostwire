/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.util.Arrays;

/**
 * Immutable reference to a Bitcoin block: height + 32-byte hash.
 *
 * <p>Used as a time anchor in karma chain epoch commitments. The
 * block hash is unpredictable before the block is mined, so
 * including it in a signed record proves the record was created
 * after the block existed.
 */
public final class BitcoinBlockReference {

    private final long height;
    private final byte[] hash;

    public BitcoinBlockReference(long height, byte[] hash) {
        if (height < 0) {
            throw new IllegalArgumentException("height must be >= 0");
        }
        if (hash == null || hash.length != 32) {
            throw new IllegalArgumentException("hash must be 32 bytes");
        }
        this.height = height;
        this.hash = hash.clone();
    }

    public long height() {
        return height;
    }

    public byte[] hash() {
        return hash.clone();
    }

    /** Hex-encoded block hash (big-endian, as commonly displayed). */
    public String hashHex() {
        return com.frostwire.util.Hex.encode(hash);
    }

    /** The karma epoch this block falls in. */
    public long epoch() {
        return KarmaConstants.epochForHeight(height);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BitcoinBlockReference)) return false;
        BitcoinBlockReference that = (BitcoinBlockReference) o;
        return height == that.height && Arrays.equals(hash, that.hash);
    }

    @Override
    public int hashCode() {
        return 31 * Long.hashCode(height) + Arrays.hashCode(hash);
    }

    @Override
    public String toString() {
        return "Block{height=" + height + ", hash=" + hashHex() + "}";
    }
}
