/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2025, FrostWire(R). All rights reserved.
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frostwire.mp4;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static com.frostwire.TestUtil.getTestResource;

/**
 * @author gubatron
 * @author aldenml
 */
public class SimpleDemuxTest {

    @Test
    public void testSimpleAudio() throws IOException {
        File fIn = getTestResource("/com/frostwire/mp4/test_audio.m4a");
        File fOut = new File(System.getProperty("java.io.tmpdir"), "test_out.mp4");

        Mp4Info tags = new Mp4Info();
        tags.compatibleBrands = new int[]{Bits.make4cc("M4A "), Bits.make4cc("mp42"), Bits.make4cc("isom"), Bits.make4cc("\0\0\0\0")};
        tags.title = "ti";
        tags.author = "au";
        tags.album = "sr";
        tags.jpg = null;

        Mp4Demuxer.audio(fIn, fOut, tags, null);
    }
}
