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
public final class SampleSizeBox extends FullBox {
    protected int sample_size;
    protected int sample_count;
    protected Entry[] entries;

    SampleSizeBox() {
        super(stsz);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 8, buf);
        sample_size = buf.getInt();
        if (sample_size != 0) {
            // Every sample has this size and there is no table (ISO/IEC 14496-12 8.7.3.2).
            sample_count = buf.getInt();
            if (sample_count < 0) {
                throw new IOException("Invalid sample count " + sample_count + " in stsz");
            }
            entries = new Entry[0];
            return;
        }
        sample_count = entryCount(ch, buf.getInt(), 4);
        entries = new Entry[sample_count];
        for (int i = 0; i < sample_count; i++) {
            Entry e = new Entry();
            IO.read(ch, 4, buf);
            e.get(buf);
            entries[i] = e;
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putInt(sample_size);
        buf.putInt(sample_count);
        IO.write(ch, 8, buf);
        if (sample_size == 0) {
            IsoMedia.write(ch, sample_count, 4, entries, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 8;
        if (sample_size == 0) {
            s += sample_count * 4;
        }
        length(s);
    }

    public static final class Entry extends BoxEntry {
        public int entry_size;

        @Override
        void get(ByteBuffer buf) {
            entry_size = buf.getInt();
        }

        @Override
        void put(ByteBuffer buf) {
            buf.putInt(entry_size);
        }
    }
}
