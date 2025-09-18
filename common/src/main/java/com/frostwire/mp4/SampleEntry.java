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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public class SampleEntry extends Box {
    protected final byte[] reserved;
    protected short data_reference_index;

    SampleEntry(int format) {
        super(format);
        boxes = new LinkedList<>();
        reserved = new byte[6];
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        IO.read(ch, 8, buf);
        buf.get(reserved);
        data_reference_index = buf.getShort();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        buf.put(reserved);
        buf.putShort(data_reference_index);
        IO.write(ch, 8, buf);
    }
}
