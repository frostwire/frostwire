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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author gubatron
 * @author aldenml
 */
public final class VisualSampleEntry extends SampleEntry {

    protected short pre_defined1;
    protected short reserved1;
    protected int[] pre_defined2;
    protected short width;
    protected short height;
    protected int horizresolution;
    protected int vertresolution;
    protected int reserved2;
    protected short frame_count;
    protected byte[] compressorname;
    protected short depth;
    protected short pre_defined3;

    VisualSampleEntry(int codingname) {
        super(codingname);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);

        IO.read(ch, 70, buf);
        pre_defined1 = buf.getShort();
        reserved1 = buf.getShort();
        pre_defined2 = new int[3];
        IO.get(buf, pre_defined2);
        width = buf.getShort();
        height = buf.getShort();
        horizresolution = buf.getInt();
        vertresolution = buf.getInt();
        reserved2 = buf.getInt();
        frame_count = buf.getShort();
        compressorname = new byte[32];
        buf.get(compressorname);
        depth = buf.getShort();
        pre_defined3 = buf.getShort();
    }

    @Override
    void update() {
        long s = 70 + 8; // + 8 sample entry
        length(s);
    }
}
