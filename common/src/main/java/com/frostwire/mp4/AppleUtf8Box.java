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

/**
 * @author gubatron
 * @author aldenml
 */
public class AppleUtf8Box extends AppleDataBox {
    private byte[] value;

    AppleUtf8Box(int type) {
        super(type);
        this.dataType = 1;
    }

    public String value() {
        return value != null ? Utf8.convert(value) : null;
    }

    public void value(String value) {
        this.value = value != null ? Utf8.convert(value) : null;
        if (this.value != null) {
            dataLength = this.value.length + 16;
        }
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        int len = (int) (length() - 16);
        if (len != 0) {
            IO.read(ch, len, buf);
            value = new byte[len];
            buf.get(value);
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        if (value != null) {
            buf.put(value);
            IO.write(ch, buf.position(), buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 16; // apple data box
        if (value != null) {
            s += value.length;
        }
        length(s);
    }
}
