/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UrlUtils.decode keeps a stray % as a literal instead of throwing (frostwire/frostwire#1359).
 */
class UrlUtilsDecodeTest {

    @Test
    void strayPercentsAreKept() {
        assertEquals("%", UrlUtils.decode("%"));
        assertEquals("50%", UrlUtils.decode("50%"));
        assertEquals("50% off", UrlUtils.decode("50%+off"));
        assertEquals("%4%2", UrlUtils.decode("%4%2"));
        assertEquals("%zz", UrlUtils.decode("%zz"));
        assertEquals("a%", UrlUtils.decode("a%25"));
    }

    @Test
    void validEscapesStillDecode() {
        assertEquals("Some Album (2020) [FLAC]", UrlUtils.decode("Some+Album%20%282020%29+%5BFLAC%5D"));
        assertEquals("ñandú", UrlUtils.decode("%C3%B1and%C3%BA"));
        assertEquals("", UrlUtils.decode(null));
    }

    @Test
    void fuzzerMagnetNameDecodes() {
        String dn = new String(Base64.getDecoder().decode("U29tZStBbGJ1bTAlNCUyODIwMjAlMjkrJTVCRkxBQyU1RA=="), StandardCharsets.UTF_8);
        assertEquals("Some Album0%4(2020) [FLAC]", UrlUtils.decode(dn));
    }
}
