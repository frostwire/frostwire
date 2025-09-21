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
public final class TrackRunBox extends FullBox {
    protected int sample_count;
    protected int data_offset;
    protected int first_sample_flags;
    protected Entry[] entries;

    TrackRunBox() {
        super(trun);
    }

    public boolean dataOffsetPresent() {
        return (flags & 0x1) == 0x1;
    }

    public boolean firstSampleFlagsPresent() {
        return (flags & 0x4) == 0x4;
    }

    public boolean sampleDurationPresent() {
        return (flags & 0x100) == 0x100;
    }

    public boolean sampleSizePresent() {
        return (flags & 0x200) == 0x200;
    }

    public boolean sampleFlagsPresent() {
        return (flags & 0x400) == 0x400;
    }

    public boolean sampleCompositionTimeOffsetsPresent() {
        return (flags & 0x800) == 0x800;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 4, buf);
        sample_count = buf.getInt();
        if ((flags & 0x1) == 0x1) { // data-offset-present
            IO.read(ch, 4, buf);
            data_offset = buf.getInt();
        }
        if ((flags & 0x4) == 0x4) { // first-sample-flags-present
            IO.read(ch, 4, buf);
            first_sample_flags = buf.getInt();
        }
        entries = new Entry[sample_count];
        for (int i = 0; i < sample_count; i++) {
            Entry e = new Entry();
            if ((flags & 0x100) == 0x100) { // sample-duration-present
                IO.read(ch, 4, buf);
                e.sample_duration = buf.getInt();
            }
            if ((flags & 0x200) == 0x200) { // sample-size-present
                IO.read(ch, 4, buf);
                e.sample_size = buf.getInt();
            }
            if ((flags & 0x400) == 0x400) { // sample-flags-present
                IO.read(ch, 4, buf);
                e.sample_flags = buf.getInt();
            }
            if ((flags & 0x800) == 0x800) { // sample-composition-time-offsets-present
                IO.read(ch, 4, buf);
                e.sample_composition_time_offset = buf.getInt();
            }
            entries[i] = e;
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putInt(sample_count);
        IO.write(ch, 4, buf);
        if ((flags & 0x1) == 0x1) { // data-offset-present
            buf.putInt(data_offset);
            IO.write(ch, 4, buf);
        }
        if ((flags & 0x4) == 0x4) { // first-sample-flags-present
            buf.putInt(first_sample_flags);
            IO.write(ch, 4, buf);
        }
        for (int i = 0; i < sample_count; i++) {
            Entry e = entries[i];
            if ((flags & 0x100) == 0x100) { // sample-duration-present
                buf.putInt(e.sample_duration);
                IO.write(ch, 4, buf);
            }
            if ((flags & 0x200) == 0x200) { // sample-size-present
                buf.putInt(e.sample_size);
                IO.write(ch, 4, buf);
            }
            if ((flags & 0x400) == 0x400) { // sample-flags-present
                buf.putInt(e.sample_flags);
                IO.write(ch, 4, buf);
            }
            if ((flags & 0x800) == 0x800) { // sample-composition-time-offsets-present
                buf.putInt(e.sample_composition_time_offset);
                IO.write(ch, 4, buf);
            }
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 4; // sample_count
        if ((flags & 0x1) == 0x1) { // data-offset-present
            s += 4;
        }
        if ((flags & 0x4) == 0x4) { // first-sample-flags-present
            s += 4;
        }
        for (int i = 0; i < sample_count; i++) {
            if ((flags & 0x100) == 0x100) { // sample-duration-present
                s += 4;
            }
            if ((flags & 0x200) == 0x200) { // sample-size-present
                s += 4;
            }
            if ((flags & 0x400) == 0x400) { // sample-flags-present
                s += 4;
            }
            if ((flags & 0x800) == 0x800) { // sample-composition-time-offsets-present
                s += 4;
            }
        }
        length(s);
    }

    public static final class Entry {
        public int sample_duration;
        public int sample_size;
        public int sample_flags;
        public int sample_composition_time_offset;
    }
}
