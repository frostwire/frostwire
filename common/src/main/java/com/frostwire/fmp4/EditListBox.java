/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.

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
public final class EditListBox extends FullBox {

    protected int entry_count;
    protected Entry[] entries;

    EditListBox() {
    }

    @Override
    void read(InputChannel in, ByteBuffer buf) throws IOException {
        super.read(in, buf);

        IO.read(in, 4, buf);
        entry_count = buf.getInt();
        entries = new Entry[entry_count];
        for (int i = 0; i < entry_count; i++) {
            Entry e = new Entry();
            if (version == 1) {
                IO.read(in, 16, buf);
                e.segment_duration = buf.getLong();
                e.media_time = buf.getLong();
            } else {
                IO.read(in, 8, buf);
                e.segment_duration = buf.getInt();
                e.media_time = buf.getInt();
            }
            IO.read(in, 4, buf);
            e.media_rate_integer = buf.getShort();
            e.media_rate_fraction = buf.getShort();
            entries[i] = e;
        }
    }

    private static final class Entry {
        public long segment_duration;
        public long media_time;
        public short media_rate_integer;
        public short media_rate_fraction;
    }
}
