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

package com.frostwire.fmp4;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SampleToChunkBox extends EntryBaseBox {

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
            e.first_chunk = buf.getInt();
            e.samples_per_chunk = buf.getInt();
            e.sample_description_index = buf.getInt();
            entries[i] = e;
        }
    }

    @Override
    void writeFields(OutputChannel ch, ByteBuffer buf) throws IOException {
        buf.putInt(entry_count);
        IO.write(ch, 4, buf);
    }

    @Override
    int entryCount() {
        return entry_count;
    }

    @Override
    int entrySize() {
        return 12;
    }

    @Override
    void putEntry(int i, ByteBuffer buf) {
        Entry e = entries[i];
        buf.putInt(e.first_chunk);
        buf.putInt(e.samples_per_chunk);
        buf.putInt(e.sample_description_index);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 4; // entry_count
        s += entry_count * 12;
        length(s);
    }

    public static final class Entry {
        public int first_chunk;
        public int samples_per_chunk;
        public int sample_description_index;
    }
}
