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

package com.frostwire.fmp4;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author gubatron
 * @author aldenml
 */
public class SimpleReadTest {

    @Test
    public void testRead() throws IOException {
        File f = new File("/Users/aldenml/Downloads/test2.mp4");
        RandomAccessFile in = new RandomAccessFile("/Users/aldenml/Downloads/test2.mp4", "r");
        InputChannel ch = new InputChannel(in.getChannel());

        IsoMedia.read(ch, f.length(), new IsoMedia.ReadListener() {
            @Override
            public void onBox(Box b) {
                System.out.println(Bits.make4cc(b.type));
            }
        });
    }
}
