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
public final class EditListBox extends FullBox {
    protected int entry_count;
    protected Entry[] entries;

    EditListBox() {
        super(elst);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 4, buf);
        entry_count = buf.getInt();
        entries = new Entry[entry_count];
        for (int i = 0; i < entry_count; i++) {
            Entry e = new Entry();
            IO.read(ch, version == 1 ? 20 : 12, buf);
            if (version == 1) {
                e.segment_duration = buf.getLong();
                e.media_time = buf.getLong();
            } else {
                e.segment_duration = buf.getInt();
                e.media_time = buf.getInt();
            }
            e.media_rate_integer = buf.getShort();
            e.media_rate_fraction = buf.getShort();
            entries[i] = e;
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putInt(entry_count);
        IO.write(ch, 4, buf);
        for (int i = 0; i < entry_count; i++) {
            Entry e = entries[i];
            if (version == 1) {
                buf.putLong(e.segment_duration);
                buf.putLong(e.media_time);
            } else {
                buf.putInt((int) e.segment_duration);
                buf.putInt((int) e.media_time);
            }
            buf.putShort(e.media_rate_integer);
            buf.putShort(e.media_rate_fraction);
            IO.write(ch, version == 1 ? 20 : 12, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 4; // entry_count
        s += entry_count * (version == 1 ? 20 : 12);
        length(s);
    }

    public static final class Entry {
        public long segment_duration;
        public long media_time;
        public short media_rate_integer;
        public short media_rate_fraction;
    }
}
