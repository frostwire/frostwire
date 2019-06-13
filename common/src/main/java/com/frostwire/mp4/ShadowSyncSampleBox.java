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
public final class ShadowSyncSampleBox extends FullBox {
    protected int entry_count;
    protected Entry[] entries;

    ShadowSyncSampleBox() {
        super(stsh);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 4, buf);
        entry_count = buf.getInt();
        entries = new Entry[entry_count];
        for (int i = 0; i < entry_count; i++) {
            Entry e = new Entry();
            IO.read(ch, 8, buf);
            e.get(buf);
            entries[i] = e;
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putInt(entry_count);
        IO.write(ch, 4, buf);
        IsoMedia.write(ch, entry_count, 8, entries, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 4; // entry_count
        s += entry_count * 8;
        length(s);
    }

    public static final class Entry extends BoxEntry {
        public int shadowed_sample_number;
        public int sync_sample_number;

        @Override
        void get(ByteBuffer buf) {
            shadowed_sample_number = buf.getInt();
            sync_sample_number = buf.getInt();
        }

        @Override
        void put(ByteBuffer buf) {
            buf.putInt(shadowed_sample_number);
            buf.putInt(sync_sample_number);
        }
    }
}
