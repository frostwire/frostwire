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
public final class CompactSampleSizeBox extends FullBox {
    protected final byte[] reserved;
    protected byte field_size;
    protected int sample_count;
    protected Entry[] entries;

    CompactSampleSizeBox() {
        super(stz2);
        reserved = new byte[3];
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 8, buf);
        buf.get(reserved);
        field_size = buf.get();
        sample_count = buf.getInt();
        entries = new Entry[sample_count];
        for (int i = 0; i < sample_count; i++) {
            Entry e = new Entry();
            switch (field_size) {
                case 4:
                    throw new UnsupportedOperationException();
                case 8:
                    IO.read(ch, 1, buf);
                    e.entry_size = buf.get();
                    break;
                case 16:
                    IO.read(ch, 2, buf);
                    e.entry_size = buf.getShort();
                    break;
            }
            entries[i] = e;
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.put(reserved);
        buf.put(field_size);
        buf.putInt(sample_count);
        IO.write(ch, 8, buf);
        for (int i = 0; i < sample_count; i++) {
            Entry e = entries[i];
            switch (field_size) {
                case 4:
                    throw new UnsupportedOperationException();
                case 8:
                    buf.put((byte) e.entry_size);
                    IO.write(ch, 1, buf);
                    break;
                case 16:
                    buf.putShort(e.entry_size);
                    IO.write(ch, 2, buf);
                    break;
            }
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 8;
        s += sample_count * field_size;
        length(s);
    }

    public static final class Entry {
        public short entry_size;
    }
}
