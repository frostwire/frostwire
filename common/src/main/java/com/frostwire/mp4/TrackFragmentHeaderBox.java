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
