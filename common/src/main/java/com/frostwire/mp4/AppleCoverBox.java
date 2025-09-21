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
public final class AppleCoverBox extends AppleDataBox {
    private static final int IMAGE_TYPE_JPG = 13;
    private static final int IMAGE_TYPE_PNG = 14;
    private byte[] value;

    AppleCoverBox() {
        super(covr);
    }

    public byte[] value() {
        return value;
    }

    public void setJpg(byte[] data) {
        value(data, IMAGE_TYPE_JPG);
    }

    public void setPng(byte[] data) {
        value(data, IMAGE_TYPE_PNG);
    }

    private void value(byte[] value, int dataType) {
        this.value = value;
        if (value != null) {
            this.dataLength = value.length + 16;
            this.dataType = dataType;
        }
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        super.read(ch, buf);
        int len = (int) (length() - 16);
        if (len != 0) {
            value = new byte[len];
            buf = ByteBuffer.wrap(value);
            IO.read(ch, len, buf);
        }
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        super.write(ch, buf);
        if (value != null) {
            buf = ByteBuffer.wrap(value);
            IO.write(ch, value.length, buf);
        }
    }

    @Override
    void update() {
        long s = 0;
        s += 16; // apple data box
        if (value != null) {
            s += value.length;
        }
        length(s);
    }
}
