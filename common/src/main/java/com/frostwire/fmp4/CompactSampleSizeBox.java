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
public final class CompactSampleSizeBox extends FullBox {

    protected byte[] reserved;
    protected byte field_size;
    protected int sample_count;
    protected Entry[] entries;

    CompactSampleSizeBox() {
        super(stz2);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);

        IO.read(ch, 8, buf);
        reserved = new byte[3];
        buf.get(reserved);
        field_size = buf.get();
        sample_count = Bits.l2i(Bits.i2u(buf.getInt())); // it's unrealistic to have more than 2G elements
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
    void update() {
        long s = 8 + 4; // + 4 full box
        for (int i = 0; i < sample_count; i++) {
            s = Bits.l2u(s + field_size);
        }
        length(s);
    }

    private static final class Entry {
        public short entry_size;
    }
}
