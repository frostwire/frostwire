/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.mp4;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author gubatron
 * @author aldenml
 */
public class SimpleDemuxTest {

    @Test
    public void testSimpleAudio() throws IOException {
        File fIn = new File("/Users/aldenml/Downloads/test_raw.m4a");
        File fOut = new File("/Users/aldenml/Downloads/test_out.mp4");

        Mp4Info tags = new Mp4Info();
        tags.compatibleBrands = new int[]{Bits.make4cc("M4A "), Bits.make4cc("mp42"), Bits.make4cc("isom"), Bits.make4cc("\0\0\0\0")};
        tags.title = "ti";
        tags.author = "au";
        tags.album = "sr";
        tags.jpg = null;

        Mp4Demuxer.audio(fIn, fOut, tags, null);
    }
}
