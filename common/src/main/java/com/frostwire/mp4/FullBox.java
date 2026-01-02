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
public class FullBox extends Box {
    protected byte version;
    protected int flags;

    FullBox(int type) {
        super(type);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        IO.read(ch, 4, buf);
        int n = buf.getInt();
        version = Bits.int3(n);
        flags = Bits.int32((byte) 0, Bits.int2(n), Bits.int1(n), Bits.int0(n));
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        int n = Bits.int32(version, Bits.int2(flags), Bits.int1(flags), Bits.int0(flags));
        buf.putInt(n);
        IO.write(ch, 4, buf);
    }
}
