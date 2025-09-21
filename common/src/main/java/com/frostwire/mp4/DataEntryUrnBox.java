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
public final class DataEntryUrnBox extends FullBox {
    protected byte[] name;
    protected byte[] location;

    DataEntryUrnBox() {
        super(urn_);
    }

    public String name() {
        return name != null ? Utf8.convert(name) : null;
    }

    public void name(String value) {
        name = value != null ? Utf8.convert(value) : null;
    }

    public String location() {
        return location != null ? Utf8.convert(location) : null;
    }

    public void location(String value) {
        location = value != null ? Utf8.convert(value) : null;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        int len = (int) (length() - 4);
        if (len != 0) {
            IO.read(ch, len, buf);
            if (buf.remaining() > 0) {
                name = IO.str(buf);
            }
            if (buf.remaining() > 0) {
                location = IO.str(buf);
            }
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        if (name != null) {
            buf.put(name);
            buf.put((byte) 0);
        }
        if (location != null) {
            buf.put(location);
            buf.put((byte) 0);
        }
        if (buf.position() > 0) {
            IO.write(ch, buf.position(), buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        if (name != null) {
            s += name.length + 1;
        }
        if (location != null) {
            s += location.length + 1;
        }
        length(s);
    }
}
