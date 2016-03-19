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
public final class MovieHeaderBox extends FullBox {

    protected long creation_time;
    protected long modification_time;
    protected int timescale;
    protected long duration;
    protected int rate;
    protected short volume;
    protected short reserved1;
    protected int[] reserved2;
    protected int[] matrix;
    protected int[] pre_defined;
    protected int next_track_ID;

    MovieHeaderBox() {
    }

    @Override
    void read(InputChannel in, ByteBuffer buf) throws IOException {
        super.read(in, buf);

        if (version == 1) {
            IO.read(in, 28, buf);
            creation_time = buf.getLong();
            modification_time = buf.getLong();
            timescale = buf.getInt();
            duration = buf.getLong();
        } else { // version == 0
            IO.read(in, 16, buf);
            creation_time = buf.getInt();
            modification_time = buf.getInt();
            timescale = buf.getInt();
            duration = buf.getInt();
        }

        IO.read(in, 80, buf);
        rate = buf.getInt();
        volume = buf.getShort();
        reserved1 = buf.getShort();
        reserved2 = new int[2];
        IO.get(buf, reserved2);
        matrix = new int[9];
        IO.get(buf, matrix);
        pre_defined = new int[6];
        IO.get(buf, pre_defined);
        next_track_ID = buf.getInt();
    }
}
