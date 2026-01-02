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
public class AppleDataBox extends Box {
    protected int dataLength;
    protected int data4cc;
    protected int dataType;
    protected short dataCountry;
    protected short dataLanguage;

    AppleDataBox(int type) {
        super(type);
        data4cc = data;
    }

    public final int dataType() {
        return dataType;
    }

    public final void dataType(int value) {
        dataType = value;
    }

    @Override
    void read(InputChannel ch, ByteBuffer buf) throws IOException {
        IO.read(ch, 16, buf);
        dataLength = buf.getInt();
        data4cc = buf.getInt();
        dataType = buf.getInt();
        dataCountry = buf.getShort();
        dataLanguage = buf.getShort();
    }

    @Override
    void write(OutputChannel ch, ByteBuffer buf) throws IOException {
        buf.putInt(dataLength);
        buf.putInt(data4cc);
        buf.putInt(dataType);
        buf.putShort(dataCountry);
        buf.putShort(dataLanguage);
        IO.write(ch, 16, buf);
    }
}
