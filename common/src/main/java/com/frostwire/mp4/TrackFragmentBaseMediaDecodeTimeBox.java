/*
 *     Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 *     Copyright (c) 2011-2026, FrostWire(R). All rights reserved.
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
