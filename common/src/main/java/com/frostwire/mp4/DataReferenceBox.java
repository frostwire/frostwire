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
public final class DataReferenceBox extends FullBox {
    protected int entry_count;
    protected Box[] entries;

    DataReferenceBox() {
        super(dref);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 4, buf);
        entry_count = buf.getInt();
        entries = new Box[entry_count];
        for (int i = 0; i < entry_count; i++) {
            IO.read(ch, 8, buf);
            int size = buf.getInt();
            int type = buf.getInt();
            Long largesize = null;
            if (size == 1) {
                IO.read(ch, 8, buf);
                largesize = buf.getLong();
            }
            Box b = Box.empty(type);
            b.size = size;
            b.largesize = largesize;
            b.read(ch, buf);
            entries[i] = b;
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putInt(entry_count);
        IO.write(ch, 4, buf);
        for (int i = 0; i < entry_count; i++) {
            Box b = entries[i];
            buf.putInt(b.size);
            buf.putInt(b.type);
            IO.write(ch, 8, buf);
            if (b.largesize != null) {
                buf.putLong(b.largesize);
                IO.write(ch, 8, buf);
            }
            b.write(ch, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 4; // entry_count
        for (int i = 0; i < entry_count; i++) {
            Box b = entries[i];
            b.update();
            if (b.size == 1) {
                s += b.largesize;
            } else {
                s += b.size;
            }
        }
        length(s);
    }
}
