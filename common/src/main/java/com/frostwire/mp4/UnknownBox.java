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
public final class UnknownBox extends Box {
    private static final int MAX_DATA_SIZE = 1024 * 1024; // 1MB
    protected byte[] data;

    UnknownBox(int type) {
        super(type);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        int len = (int) length();
        if (len > 0) {
            if (len <= MAX_DATA_SIZE) {
                data = new byte[len];
                buf = ByteBuffer.wrap(data);
                IO.read(ch, len, buf);
            } else {
                IO.skip(ch, len, buf);
            }
        } else {
            IO.skip(ch, buf);
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        if (data != null) {
            buf = ByteBuffer.wrap(data);
            IO.write(ch, data.length, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        if (data != null) {
            s += data.length;
        }
        length(s);
    }
}
