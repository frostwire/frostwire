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
public final class VideoMediaHeaderBox extends FullBox {
    protected final short[] opcolor;
    protected short graphicsmode;

    VideoMediaHeaderBox() {
        super(vmhd);
        opcolor = new short[]{0, 0, 0};
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        IO.read(ch, 8, buf);
        graphicsmode = buf.getShort();
        IO.get(buf, opcolor);
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        buf.putShort(graphicsmode);
        IO.put(buf, opcolor);
        IO.write(ch, 8, buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // full box
        s += 2; // graphicsmode
        s += 3 * 2; // opcolor
        length(s);
    }
}
