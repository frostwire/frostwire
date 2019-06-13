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

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author gubatron
 * @author aldenml
 */
public final class HintSampleEntry extends SampleEntry {
    protected byte[] data;

    HintSampleEntry(int protocol) {
        super(protocol);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        int len = (int) (length() - 8);
        if (len != 0) {
            IO.read(ch, len, buf);
            data = new byte[len];
            buf.get(data);
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        if (data != null) {
            buf.put(data);
            IO.write(ch, data.length, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 8; // sample entry
        if (data != null) {
            s += data.length;
        }
        s += ContainerBox.length(boxes);
        length(s);
    }
}
