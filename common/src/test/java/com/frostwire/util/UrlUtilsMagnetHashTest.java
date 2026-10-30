/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UrlUtils.magnetHexInfoHash returns only a well-formed 40-digit hex hash, and never throws: found by
 * fuzzing Android's TorrentUrlInfo, which used to take substring(20, 60) of any such link.
 */
class UrlUtilsMagnetHashTest {

    private static final String HASH = "0123456789ABCDEF0123456789abcdef01234567";

    @Test
    void wellFormedHexHashesAreReturnedInLowercase() {
        assertEquals(HASH.toLowerCase(), UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + HASH));
        assertEquals(HASH.toLowerCase(), UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + HASH + "&dn=ubuntu&tr=udp%3A%2F%2Fx"));
    }

    @Test
    void anythingElseIsNull() {
        assertNull(UrlUtils.magnetHexInfoHash(null));
        // Fuzzer inputs: a link cut short (was StringIndexOutOfBoundsException) and garbage after the prefix.
        assertNull(UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:\u0006"));
        assertNull(UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:ag.etmagnet:?xt=urn:btih:urn:btih:ag.etmagne:?xt=ur\u0006"));
        // A base32 hash, then more parameters: 40 characters past the prefix, but not a hex hash.
        // Before frostwire/frostwire#1408 this was null; base32 is now decoded (still supported per
        // BEP 9), so the old "take 40 characters" bug can't come back as a wrong hash either.
        assertEquals("00443214c74254b635cf84653a56d7c675be77df",
                UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:ABCDEFGHIJKLMNOPQRSTUVWXYZ234567&dn=a-long-name"));
        // 41 hex digits, a hash of the wrong length.
        assertNull(UrlUtils.magnetHexInfoHash("magnet:?xt=urn:btih:" + HASH + "f"));
        // 40 hex digits elsewhere in the link don't count.
        assertNull(UrlUtils.magnetHexInfoHash("magnet:?dn=" + HASH + "&xt=urn:btih:short"));
        assertNull(UrlUtils.magnetHexInfoHash("http://example.com/" + HASH + ".torrent"));
    }
}
