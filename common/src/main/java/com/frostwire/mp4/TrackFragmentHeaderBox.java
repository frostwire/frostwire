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
public final class TrackFragmentHeaderBox extends FullBox {
    protected int track_ID;
    protected long base_data_offset;
    protected int sample_description_index;
    protected int default_sample_duration;
    protected int default_sample_size;
    protected int default_sample_flags;

    TrackFragmentHeaderBox() {
        super(tfhd);
    }

    public boolean baseDataOffsetPresent() {
        return (flags & 0x1) == 0x1;
    }

    public boolean sampleDescriptionIndexPresent() {
        return (flags & 0x2) == 0x2;
    }

    public boolean defaultSampleDurationPresent() {
        return (flags & 0x8) == 0x8;
    }

    public boolean defaultSampleSizePresent() {
        return (flags & 0x10) == 0x10;
    }

    public boolean defaultSampleFlagsPresent() {
        return (flags & 0x20) == 0x20;
    }

    public boolean durationIsEmpty() {
        return (flags & 0x10000) == 0x10000;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 4, buf);
        track_ID = buf.getInt();
        if ((flags & 0x1) == 0x1) { // base-data-offset-present
            IO.read(ch, 8, buf);
            base_data_offset = buf.getLong();
        }
        if ((flags & 0x2) == 0x2) { // sample-description-index-present
            IO.read(ch, 4, buf);
            sample_description_index = buf.getInt();
        }
        if ((flags & 0x8) == 0x8) { // default-sample-duration-present
            IO.read(ch, 4, buf);
            default_sample_duration = buf.getInt();
        }
        if ((flags & 0x10) == 0x10) { // default-sample-size-present
            IO.read(ch, 4, buf);
            default_sample_size = buf.getInt();
        }
        if ((flags & 0x20) == 0x20) { // default-sample-flags-present
            IO.read(ch, 4, buf);
            default_sample_flags = buf.getInt();
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putInt(track_ID);
        IO.write(ch, 4, buf);
        if ((flags & 0x1) == 0x1) { // base-data-offset-present
            buf.putLong(base_data_offset);
            IO.write(ch, 8, buf);
        }
        if ((flags & 0x2) == 0x2) { // sample-description-index-present
            buf.putInt(sample_description_index);
            IO.write(ch, 4, buf);
        }
        if ((flags & 0x8) == 0x8) { // default-sample-duration-present
            buf.putInt(default_sample_duration);
            IO.write(ch, 4, buf);
        }
        if ((flags & 0x10) == 0x10) { // default-sample-size-present
            buf.putInt(default_sample_size);
            IO.write(ch, 4, buf);
        }
        if ((flags & 0x20) == 0x20) { // default-sample-flags-present
            buf.putInt(default_sample_flags);
            IO.write(ch, 4, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 4; // track_ID
        if ((flags & 0x1) == 0x1) {
            size += 8; // base_data_offset
        }
        if ((flags & 0x2) == 0x2) {
            size += 4; // sample_description_index
        }
        if ((flags & 0x8) == 0x8) {
            size += 4; // default_sample_duration
        }
        if ((flags & 0x10) == 0x10) {
            size += 4; // default_sample_size
        }
        if ((flags & 0x20) == 0x20) {
            size += 4; // default_sample_flags
        }
        length(s);
    }
}
