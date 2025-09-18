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
