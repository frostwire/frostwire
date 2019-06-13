/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
