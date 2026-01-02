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
public final class VisualSampleEntry extends SampleEntry {
    protected final int[] pre_defined2;
    protected final byte[] compressorname;
    protected short pre_defined1;
    protected short reserved1;
    protected short width;
    protected short height;
    protected int horizresolution;
    protected int vertresolution;
    protected int reserved2;
    protected short frame_count;
    protected short depth;
    protected short pre_defined3;

    VisualSampleEntry(int codingname) {
        super(codingname);
        pre_defined2 = new int[3];
        horizresolution = 0x00480000; // 72 dpi
        vertresolution = 0x00480000; // 72 dpi
        frame_count = 1;
        compressorname = new byte[32];
        depth = 0x0018;
        pre_defined3 = -1;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 70, buf);
        pre_defined1 = buf.getShort();
        reserved1 = buf.getShort();
        IO.get(buf, pre_defined2);
        width = buf.getShort();
        height = buf.getShort();
        horizresolution = buf.getInt();
        vertresolution = buf.getInt();
        reserved2 = buf.getInt();
        frame_count = buf.getShort();
        buf.get(compressorname);
        depth = buf.getShort();
        pre_defined3 = buf.getShort();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putShort(pre_defined1);
        buf.putShort(reserved1);
        IO.put(buf, pre_defined2);
        buf.putShort(width);
        buf.putShort(height);
        buf.putInt(horizresolution);
        buf.putInt(vertresolution);
        buf.putInt(reserved2);
        buf.putShort(frame_count);
        buf.put(compressorname);
        buf.putShort(depth);
        buf.putShort(pre_defined3);
        IO.write(ch, 70, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 8; // sample entry
        s += 2; // pre_defined1
        s += 2; // reserved1
        s += 3 * 4; // pre_defined2
        s += 2; // width
        s += 2; // height
        s += 4; // horizresolution
        s += 4; // vertresolution
        s += 4; // reserved2
        s += 2; // frame_count
        s += 32; // compressorname
        s += 2; // depth
        s += 2; // pre_defined3
        s += ContainerBox.length(boxes);
        length(s);
    }
}
