/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search.relay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class TestTorrentMetadata {
    private static final String INFO =
            "d6:lengthi1e4:name7:fixture12:piece lengthi16384e6:pieces20:12345678901234567890e";

    private TestTorrentMetadata() {
    }

    static byte[] bytes(int commentLength) {
        return ("d7:comment" + commentLength + ":" + "x".repeat(commentLength) + "4:info" + INFO + "e")
                .getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] infoHash() {
        try {
            return MessageDigest.getInstance("SHA-1").digest(INFO.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    static byte[] v2Info(boolean hybrid) {
        return ("d9:file treed7:fixtured0:d6:lengthi1e11:pieces root32:12345678901234567890123456789012eee"
                + (hybrid ? "6:lengthi1e" : "")
                + "12:meta versioni2e4:name7:fixture12:piece lengthi16384e"
                + (hybrid ? "6:pieces20:12345678901234567890" : "") + "e").getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] v2Bytes(boolean hybrid) {
        return ("d4:info" + new String(v2Info(hybrid), StandardCharsets.US_ASCII) + "e")
                .getBytes(StandardCharsets.US_ASCII);
    }
}
