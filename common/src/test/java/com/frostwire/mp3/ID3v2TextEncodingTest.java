/*
 *     Created by Angel Leon (@gubatron)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
 *
 *     Licensed under GPL v3. See LICENSE file.
 */

package com.frostwire.mp3;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A frame's text encoding byte is checked when the frame is parsed (frostwire/frostwire#1358): an
 * invalid one rejects the frame, and the tag's getters never throw IllegalArgumentException.
 */
class ID3v2TextEncodingTest {

    @Test
    void invalidEncodingBytesRejectTheFrame() {
        byte[][] frames = {
                {6, 'h', 'i'}, // text
                {-1, 'h', 'i'}, // text, 0xff
                {6, 0, 'h', 't', 't', 'p'}, // URL
                {6, 'e', 'n', 'g', 0, 'h', 'i'}, // comment
                {6, 'i', 'm', 'a', 'g', 'e', 0, 3, 0, 1}, // picture
                {6, 'J', 'P', 'G', 3, 0, 1}, // v2.2 picture
        };
        assertThrows(InvalidDataException.class, () -> new ID3v2TextFrameData(false, frames[0]));
        assertThrows(InvalidDataException.class, () -> new ID3v2TextFrameData(false, frames[1]));
        assertThrows(InvalidDataException.class, () -> new ID3v2UrlFrameData(false, frames[2]));
        assertThrows(InvalidDataException.class, () -> new ID3v2CommentFrameData(false, frames[3]));
        assertThrows(InvalidDataException.class, () -> new ID3v2PictureFrameData(false, frames[4]));
        assertThrows(InvalidDataException.class, () -> new ID3v2ObseletePictureFrameData(false, frames[5]));
    }

    @Test
    void validEncodingsStillParse() throws InvalidDataException {
        for (byte encoding = 0; encoding <= 3; encoding++) {
            assertEquals(encoding, new ID3v2TextFrameData(false, new byte[]{encoding, 'h', 'i'}).getText().getTextEncoding());
        }
    }

    @Test
    void fuzzerTagGettersDontThrow() throws Exception {
        // Minimized Jazzer finding: a v2.3 TIT2 frame with encoding byte 6.
        AbstractID3v2Tag tag = ID3v2TagFactory.createTag(
                HexFormat.of().parseHex("494433030000000000415449543200000006000006000000000000000000000000000000"));
        tag.getTitle();
        tag.getArtist();
        tag.getAlbum();
    }
}
