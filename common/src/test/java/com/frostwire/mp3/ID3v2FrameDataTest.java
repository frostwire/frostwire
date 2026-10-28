/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A frame whose data is shorter than its fields is invalid, never an ArrayIndexOutOfBounds or
 * NegativeArraySize from a getter (frostwire/frostwire#1357).
 */
class ID3v2FrameDataTest {

    @Test
    void truncatedFrameDataIsInvalid() {
        assertThrows(InvalidDataException.class, () -> new ID3v2TextFrameData(false, new byte[0]));
        assertThrows(InvalidDataException.class, () -> new ID3v2UrlFrameData(false, new byte[0]));
        // Encoding byte and one byte of the language.
        assertThrows(InvalidDataException.class, () -> new ID3v2CommentFrameData(false, new byte[]{0, 'e'}));
        // Encoding byte only: no MIME type terminator, no picture type.
        assertThrows(InvalidDataException.class, () -> new ID3v2PictureFrameData(false, new byte[]{0}));
        // Encoding byte and two of the three image format bytes.
        assertThrows(InvalidDataException.class, () -> new ID3v2ObseletePictureFrameData(false, new byte[]{0, 'J', 'P'}));
    }

    /** Minimized Jazzer findings: empty text frame, APIC and v2.2 PIC frames cut short. */
    @ParameterizedTest
    @ValueSource(strings = {
            "49443302000000000029545432000000000000000000",
            "494433040000000000344150494300000000000000000000000000000000",
            "4944330200000000002950494300000400000000000000000000",
            "49443302000000000041504943000000000000000000",
    })
    void gettersOfTagsWithTruncatedFramesDontThrow(String hex) {
        AbstractID3v2Tag tag;
        try {
            tag = ID3v2TagFactory.createTag(HexFormat.of().parseHex(hex));
        } catch (NoSuchTagException | UnsupportedTagException | InvalidDataException rejected) {
            return; // Documented rejection.
        }
        tag.getTitle();
        tag.getArtist();
        tag.getAlbum();
        tag.getComment();
        tag.getUrl();
        tag.getAlbumImage();
        tag.getAlbumImageMimeType();
    }
}
