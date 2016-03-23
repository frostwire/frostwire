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
public final class TrackHeaderBox extends FullBox {

    protected long creation_time;
    protected long modification_time;
    protected int track_ID;
    protected int reserved1;
    protected long duration;
    protected int[] reserved2;
    protected short layer;
    protected short alternate_group;
    protected short volume;
    protected short reserved3;
    protected int[] matrix;
    protected int width;
    protected int height;

    TrackHeaderBox() {
        super(tkhd);
    }

    @Override
    void read(InputChannel in, ByteBuffer buf) throws IOException {
        super.read(in, buf);

        if (version == 1) {
            IO.read(in, 32, buf);
            creation_time = buf.getLong();
            modification_time = buf.getLong();
            track_ID = buf.getInt();
            reserved1 = buf.getInt();
            duration = buf.getLong();
        } else { // version == 0
            IO.read(in, 20, buf);
            creation_time = buf.getInt();
            modification_time = buf.getInt();
            track_ID = buf.getInt();
            reserved1 = buf.getInt();
            duration = buf.getInt();
        }

        IO.read(in, 60, buf);
        reserved2 = new int[2];
        IO.get(buf, reserved2);
        layer = buf.getShort();
        alternate_group = buf.getShort();
        volume = buf.getShort();
        reserved3 = buf.getShort();
        matrix = new int[9];
        IO.get(buf, matrix);
        width = buf.getInt();
        height = buf.getInt();
    }

    @Override
    void update() {
        long s = 60;
        if (version == 1) {
            s += 32;
        } else { // version == 0
            s += 20;
        }
        length(s);
    }
}
