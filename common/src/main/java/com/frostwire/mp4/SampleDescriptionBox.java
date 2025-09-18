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
public final class SampleDescriptionBox extends FullBox {
    protected int entry_count;
    protected SampleEntry[] entries;

    SampleDescriptionBox() {
        super(stsd);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 4, buf);
        entry_count = buf.getInt();
        entries = new SampleEntry[entry_count];
        int handler_type = handler_type();
        for (int i = 0; i < entry_count; i++) {
            IO.read(ch, 8, buf);
            int size = buf.getInt();
            int type = buf.getInt();
            Long largesize = null;
            if (size == 1) {
                IO.read(ch, 8, buf);
                largesize = buf.getLong();
            }
            SampleEntry e;
            if (handler_type == soun) {
                e = new AudioSampleEntry(type);
            } else if (handler_type == vide) {
                e = new VisualSampleEntry(type);
            } else if (handler_type == hint) {
                e = new HintSampleEntry(type);
            } else {
                throw new UnsupportedOperationException("Can't decode for such handler_type: " + Bits.make4cc(handler_type));
            }
            e.size = size;
            e.largesize = largesize;
            long r = ch.count();
            e.read(ch, buf);
            r = ch.count() - r;
            long length = e.length();
            if (r < length) {
                IsoMedia.read(ch, length - r, e, buf, IsoMedia.OnBoxListener.ALL);
            }
            entries[i] = e;
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
            IsoMedia.write(ch, b.boxes, buf, IsoMedia.OnBoxListener.ALL);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 4; // entry_count
        for (int i = 0; i < entry_count; i++) {
            SampleEntry e = entries[i];
            e.update();
            if (e.size == 1) {
                s += e.largesize;
            } else {
                s += e.size;
            }
        }
        length(s);
    }

    private int handler_type() throws IOException {
        try {
            Box b = parent.parent.parent;
            b = b.findFirst(Box.hdlr);
            return ((HandlerBox) b).handler_type;
        } catch (Throwable e) {
            throw new IOException("Can't detect handler type for proper reading", e);
        }
    }
}
