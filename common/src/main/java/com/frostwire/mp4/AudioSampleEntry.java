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
public final class AudioSampleEntry extends SampleEntry {
    protected final int[] reserved1;
    protected short channelcount;
    protected short samplesize;
    protected short pre_defined;
    protected short reserved2;
    protected int samplerate;

    AudioSampleEntry(int codingname) {
        super(codingname);
        reserved1 = new int[2];
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 20, buf);
        IO.get(buf, reserved1);
        channelcount = buf.getShort();
        samplesize = buf.getShort();
        pre_defined = buf.getShort();
        reserved2 = buf.getShort();
        samplerate = buf.getInt();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        IO.put(buf, reserved1);
        buf.putShort(channelcount);
        buf.putShort(samplesize);
        buf.putShort(pre_defined);
        buf.putShort(reserved2);
        buf.putInt(samplerate);
        IO.write(ch, 20, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 8; // sample entry
        s += 20;
        s += ContainerBox.length(boxes);
        length(s);
    }
}
