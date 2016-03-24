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
import java.util.Iterator;
import java.util.LinkedList;

/**
 * @author gubatron
 * @author aldenml
 */
public final class SampleDescriptionBox extends FullBox {

    protected int entry_count;
    protected SampleEntry[] entries;

    SampleDescriptionBox() {
        super(stsd);
        boxes = new LinkedList<>();
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);

        IO.read(ch, 4, buf);
        entry_count = Bits.l2i(Bits.i2u(buf.getInt())); // it's unrealistic to have more than 2G elements
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

            SampleEntry e = null;
            if (handler_type == soun) {
                e = new AudioSampleEntry(type);
            } else if (handler_type == vide) {
                e = new VisualSampleEntry(type);
            } else if (handler_type == hint) {
                e = new HintSampleEntry(type);
            }
            e.size = size;
            e.largesize = largesize;

            e.read(ch, buf);

            entries[i] = e;
        }
    }

    @Override
    void update() {
        long s = 8; // 4 entry_count + 4 full box
        for (int i = 0; i < entries.length; i++) {
            SampleEntry e = entries[i];
            e.update();
            if (e.size == 1) {
                s = Bits.l2u(s + e.largesize);
            } else {
                s = Bits.l2u(s + e.size);
            }
        }
        length(s);
    }

    private int handler_type() {
        Box b = parent.parent.parent;
        Iterator<Box> it = b.boxes.iterator();
        while (it.hasNext()) {
            Box t = it.next();
            if (t.type == hdlr) {
                b = t;
                break;
            }
        }
        return ((HandlerBox) b).handler_type;
    }
}
