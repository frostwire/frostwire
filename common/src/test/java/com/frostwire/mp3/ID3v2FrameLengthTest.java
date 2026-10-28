/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Lengths read from an ID3v2 tag are checked against the tag before anything is allocated for them
 * (frostwire/frostwire#1355): a 20-byte tag must not allocate 2 GB.
 */
class ID3v2FrameLengthTest {

    @Test
    void copyBufferChecksBoundsBeforeAllocating() {
        byte[] bytes = new byte[4];
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> BufferTools.copyBuffer(bytes, 0, Integer.MAX_VALUE));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> BufferTools.copyBuffer(bytes, 2, -1));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> BufferTools.copyBuffer(bytes, -1, 2));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> BufferTools.copyBuffer(bytes, 3, 2));
    }

    @Test
    void frameDataPastTheEndOfTheTagIsInvalid() {
        // TIT2, data length 0x7fffffff, flags, then 2 bytes of data.
        byte[] frame = HexFormat.of().parseHex("544954327fffffff00000102");
        assertThrows(InvalidDataException.class, () -> new ID3v2Frame(frame, 0));
        // v2.2 TT2, data length 0xffffff, then 2 bytes of data.
        byte[] obsolete = HexFormat.of().parseHex("545432ffffff0102");
        assertThrows(InvalidDataException.class, () -> new ID3v2ObseleteFrame(obsolete, 0));
    }

    @Test
    void frameHeaderPastTheEndOfTheTagIsInvalid() {
        assertThrows(InvalidDataException.class, () -> new ID3v2Frame(HexFormat.of().parseHex("54495432"), 0));
        assertThrows(InvalidDataException.class, () -> new ID3v2ObseleteFrame(HexFormat.of().parseHex("5454"), 0));
    }

    @Test
    void fuzzerTagsAreRejectedOrParsedWithoutRuntimeErrors() {
        // Minimized Jazzer findings: a v2.2 frame claiming 2 GB, and one with a negative length.
        for (String hex : new String[]{"49443302000000000029545449436767673304ff10", "4944330200000000002941504943ff0000000000"}) {
            byte[] tag = HexFormat.of().parseHex(hex);
            try {
                ID3v2TagFactory.createTag(tag);
            } catch (NoSuchTagException | UnsupportedTagException | InvalidDataException expected) {
                // Documented rejections.
            }
        }
    }

    @Test
    void extendedHeaderPastTheEndOfTheTagIsInvalid() {
        // v2.3 tag with the extended header flag (0x40), 32 bytes of data, an extended header claiming
        // far more than the tag holds.
        byte[] tag = new byte[42];
        System.arraycopy(HexFormat.of().parseHex("49443303004000000020"), 0, tag, 0, 10);
        System.arraycopy(HexFormat.of().parseHex("7f7f7f7f"), 0, tag, 10, 4);
        assertThrows(InvalidDataException.class, () -> ID3v2TagFactory.createTag(tag));
    }
}
