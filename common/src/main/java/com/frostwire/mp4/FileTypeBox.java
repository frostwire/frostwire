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
public final class FileTypeBox extends Box {
    protected int major_brand;
    protected int minor_version;
    protected int[] compatible_brands;

    FileTypeBox() {
        super(ftyp);
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        IO.read(ch, (int) length(), buf);
        major_brand = buf.getInt();
        minor_version = buf.getInt();
        compatible_brands = new int[buf.remaining() / 4];
        IO.get(buf, compatible_brands);
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        buf.putInt(major_brand);
        buf.putInt(minor_version);
        IO.put(buf, compatible_brands);
        IO.write(ch, buf.position(), buf);
    }

    @Override
    void update() {
        long s = 0;
        s += 4; // major_brand
        s += 4; // minor_version
        s += compatible_brands.length * 4;
        length(s);
    }
}
