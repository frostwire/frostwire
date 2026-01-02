/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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
