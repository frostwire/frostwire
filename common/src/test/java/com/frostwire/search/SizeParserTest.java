/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sizes parsed from search engines' pages are bytes or -1, never negative or wrapped
 * (frostwire/frostwire#1360).
 */
class SizeParserTest {

    @Test
    void negativeSizesAreRejected() {
        assertEquals(-1, SizeParser.parseSize("-2 GB"));
        assertEquals(-1, SizeParser.parseSize("-2 B"));
        assertEquals(-1, SizeParser.parseSize("-0.5 MiB"));
    }

    @Test
    void terabytesAndPetabytesAreNotZero() {
        assertEquals(1024L * 1024 * 1024 * 1024, SizeParser.parseSize("1 TB"));
        assertEquals(1536L * 1024 * 1024 * 1024, SizeParser.parseSize("1.5 TiB"));
        assertEquals(1024L * 1024 * 1024 * 1024 * 1024, SizeParser.parseSize("1 PB"));
    }

    @Test
    void sizesTooLargeForALongAreRejected() {
        assertEquals(-1, SizeParser.parseSize("9000 PB"));
        assertEquals(-1, SizeParser.parseSize("1e300 B"));
    }

    @Test
    void validSizesStillParse() {
        assertEquals(0, SizeParser.parseSize("0 B"));
        assertEquals(1, SizeParser.parseSize("1 bytes"));
        assertEquals(1536, SizeParser.parseSize("1.5 KB"));
        assertEquals(1024L * 1024 * 1024, SizeParser.parseSize("1 GiB"));
        assertEquals(1024L * 1024, SizeParser.parseSize("1" + (char) 160 + "MiB"));
        assertEquals(-1, SizeParser.parseSize("12 parsecs"));
        assertEquals(-1, SizeParser.parseSize("GB"));
    }
}
