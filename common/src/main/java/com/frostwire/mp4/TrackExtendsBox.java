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
public final class TrackExtendsBox extends FullBox {
    protected int track_ID;
    protected int default_sample_description_index;
    protected int default_sample_duration;
    protected int default_sample_size;
    protected int default_sample_flags;

    TrackExtendsBox() {
        super(trex);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 20, buf);
        track_ID = buf.getInt();
        default_sample_description_index = buf.getInt();
        default_sample_duration = buf.getInt();
        default_sample_size = buf.getInt();
        default_sample_flags = buf.getInt();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putInt(track_ID);
        buf.putInt(default_sample_description_index);
        buf.putInt(default_sample_duration);
        buf.putInt(default_sample_size);
        buf.putInt(default_sample_flags);
        IO.write(ch, 20, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 20;
        length(s);
    }
}
