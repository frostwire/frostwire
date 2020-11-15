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
