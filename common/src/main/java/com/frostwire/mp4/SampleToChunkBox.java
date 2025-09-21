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
public final class SampleToChunkBox extends FullBox {
    protected int entry_count;
    protected Entry[] entries;

    SampleToChunkBox() {
        super(stsc);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 4, buf);
        entry_count = buf.getInt();
        entries = new Entry[entry_count];
        for (int i = 0; i < entry_count; i++) {
            Entry e = new Entry();
            IO.read(ch, 12, buf);
            e.get(buf);
            entries[i] = e;
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putInt(entry_count);
        IO.write(ch, 4, buf);
        IsoMedia.write(ch, entry_count, 12, entries, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 4; // entry_count
        s += entry_count * 12;
        length(s);
    }

    public static final class Entry extends BoxEntry {
        public int first_chunk;
        public int samples_per_chunk;
        public int sample_description_index;

        @Override
        void get(ByteBuffer buf) {
            first_chunk = buf.getInt();
            samples_per_chunk = buf.getInt();
            sample_description_index = buf.getInt();
        }

        @Override
        void put(ByteBuffer buf) {
            buf.putInt(first_chunk);
            buf.putInt(samples_per_chunk);
            buf.putInt(sample_description_index);
        }
    }
}
