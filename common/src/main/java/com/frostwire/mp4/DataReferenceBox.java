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
