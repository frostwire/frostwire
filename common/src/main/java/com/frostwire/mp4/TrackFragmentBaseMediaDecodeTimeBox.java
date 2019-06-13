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
public final class TrackFragmentBaseMediaDecodeTimeBox extends FullBox {
    protected long base_media_decode_time;

    TrackFragmentBaseMediaDecodeTimeBox() {
        super(tfdt);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        if (version == 1) {
            IO.read(ch, 8, buf);
            base_media_decode_time = buf.getLong();
        } else { // version == 0
            IO.read(ch, 4, buf);
            base_media_decode_time = buf.getInt();
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        if (version == 1) {
            buf.putLong(base_media_decode_time);
            IO.write(ch, 8, buf);
        } else { // version == 0
            buf.putInt((int) base_media_decode_time);
            IO.write(ch, 4, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += version == 1 ? 8 : 4; // base_media_decode_time
        length(s);
    }
}
